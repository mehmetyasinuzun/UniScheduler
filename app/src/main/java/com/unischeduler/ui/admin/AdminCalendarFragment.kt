package com.unischeduler.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.chip.Chip
import com.unischeduler.R
import com.unischeduler.data.model.OrgSettings
import com.unischeduler.data.model.ScheduleEntry
import com.unischeduler.databinding.FragmentWeeklyScheduleBinding
import com.unischeduler.ui.lecturer.CalendarViewModel
import com.unischeduler.ui.shared.ScheduleViewConfig
import com.unischeduler.util.PdfExporter
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow
import com.unischeduler.util.shareDocument
import com.unischeduler.util.writeBytesToUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class AdminCalendarFragment : Fragment() {

    private var _binding: FragmentWeeklyScheduleBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CalendarViewModel by viewModels()

    private var allEntries: List<ScheduleEntry> = emptyList()
    private var currentSettings: OrgSettings? = null

    private data class FilterCriteria(
        val lecturerName: String? = null,
        val classroomCode: String? = null,
        val departmentName: String? = null,
        val classYear: Int? = null
    ) {
        val isEmpty: Boolean get() =
            lecturerName == null && classroomCode == null && departmentName == null && classYear == null
    }

    private var criteria: FilterCriteria = FilterCriteria()

    private lateinit var pdfSaveLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pdfSaveLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf")
        ) { uri ->
            uri ?: return@registerForActivityResult
            val entriesAtClick = filteredEntries()
            val settingsAtClick = currentSettings ?: OrgSettings()
            val title = buildTitle()
            writeBytesToUri(
                destination = uri,
                produce = {
                    withContext(Dispatchers.IO) {
                        ByteArrayOutputStream().use { buf ->
                            PdfExporter.exportSchedule(buf, title, entriesAtClick, settingsAtClick)
                            buf.toByteArray()
                        }
                    }
                },
                onDone = { savedUri ->
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.export_success)
                        .setPositiveButton(R.string.export_pdf_share) { _, _ ->
                            shareDocument(savedUri, "application/pdf")
                        }
                        .setNegativeButton(R.string.common_ok, null)
                        .show()
                }
            )
        }
    }

    /**
     * Tek doğruluk kaynağı: criteria + allEntries → görünen liste.
     * Hem ekran hem PDF buradan beslenir; senkron kalır garanti.
     */
    private fun filteredEntries(): List<ScheduleEntry> {
        if (criteria.isEmpty) return allEntries
        return allEntries.filter { e ->
            (criteria.lecturerName == null || e.lecturerName == criteria.lecturerName) &&
            (criteria.classroomCode == null || e.classroomCode == criteria.classroomCode) &&
            (criteria.departmentName == null || e.offerings?.courses?.departments?.name == criteria.departmentName) &&
            (criteria.classYear == null || e.offerings?.classYear == criteria.classYear)
        }
    }

    private fun buildTitle(): String {
        if (criteria.isEmpty) return getString(R.string.export_pdf_admin_title)
        val parts = mutableListOf<String>()
        criteria.departmentName?.let { parts += getString(R.string.label_department_value, it) }
        criteria.classYear?.let { parts += getString(R.string.label_class_year_value, "${it}.") }
        criteria.lecturerName?.let { parts += getString(R.string.label_lecturer_value, it) }
        criteria.classroomCode?.let { parts += getString(R.string.label_classroom_value, it) }
        return parts.joinToString(" • ")
    }

    /**
     * Dosya adı: filtreye göre şekillenir, son kullanıcı içeriği
     * dosya adından anlayabilsin. Türkçe karakterler ASCII'ye
     * çevrilir; boşluk/özel karakter "-" olur.
     */
    private fun buildFileName(): String {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        if (criteria.isEmpty) return "program-tumu-$today.pdf"

        val slugs = mutableListOf<String>()
        criteria.departmentName?.let { slugs += slugify(it) }
        criteria.classYear?.let { slugs += "${it}sinif" }
        criteria.lecturerName?.let { slugs += slugify(it) }
        criteria.classroomCode?.let { slugs += slugify(it) }
        val combined = slugs.joinToString("-").take(80)
        return "program-$combined-$today.pdf"
    }

    private fun slugify(s: String): String {
        val map = mapOf(
            'ç' to 'c', 'Ç' to 'c', 'ğ' to 'g', 'Ğ' to 'g',
            'ı' to 'i', 'İ' to 'i', 'ö' to 'o', 'Ö' to 'o',
            'ş' to 's', 'Ş' to 's', 'ü' to 'u', 'Ü' to 'u'
        )
        val sb = StringBuilder()
        for (c in s) {
            val mapped = map[c] ?: c.lowercaseChar()
            when {
                mapped.isLetterOrDigit() -> sb.append(mapped)
                sb.isNotEmpty() && sb.last() != '-' -> sb.append('-')
            }
        }
        return sb.toString().trim('-')
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWeeklyScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTitle.text = getString(R.string.export_pdf_admin_title)
        binding.btnRetry.setOnClickListener { viewModel.loadAllEntries() }
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadAllEntries() }
        binding.chipGroupFilter.visibility = View.VISIBLE
        binding.btnExportPdf.setOnClickListener {
            pdfSaveLauncher.launch(buildFileName())
        }

        // Coach mark kaldırıldı — TourCoordinator Calendar sekmesinde aynı
        // bilgiyi BottomSheet ile veriyor.

        binding.weeklySchedule.setOnEntryClickListener { entry ->
            showEntryDetail(entry)
        }

        collectFlow(viewModel.state) { state ->
            when (state) {
                is UiState.Idle -> viewModel.loadAllEntries()
                is UiState.Loading -> {
                    if (!binding.swipeRefresh.isRefreshing) binding.progressBar.visibility = View.VISIBLE
                    binding.tvError.visibility = View.GONE
                    binding.tvEmpty.visibility = View.GONE
                    binding.btnRetry.visibility = View.GONE
                }
                is UiState.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.text = state.message
                    binding.tvError.visibility = View.VISIBLE
                    binding.btnRetry.visibility = if (state.retryable) View.VISIBLE else View.GONE
                }
                is UiState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.visibility = View.GONE
                    binding.btnRetry.visibility = View.GONE
                    binding.btnExportPdf.visibility = View.VISIBLE

                    val data = state.data
                    allEntries = data.entries
                    currentSettings = data.settings

                    binding.weeklySchedule.setConfig(
                        ScheduleViewConfig(
                            dayStart = data.settings.dayStart,
                            dayEnd = data.settings.dayEnd,
                            timeStepMinutes = data.settings.timeStepMinutes.coerceAtLeast(30),
                            activeDays = data.settings.activeDays.ifEmpty {
                                listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
                            }
                        )
                    )

                    buildFilterChips()
                    refreshView()
                }
            }
        }
    }

    private fun buildFilterChips() {
        binding.chipGroupFilter.removeAllViews()

        val chipAll = createChip(if (criteria.isEmpty) "Tümü ✓" else "Tümü")
        chipAll.setOnClickListener {
            criteria = FilterCriteria()
            buildFilterChips()
            refreshView()
        }
        binding.chipGroupFilter.addView(chipAll)

        val lecturers = allEntries.map { it.lecturerName }.filter { it.isNotBlank() }.distinct().sorted()
        if (lecturers.isNotEmpty()) {
            val label = criteria.lecturerName?.let { getString(R.string.filter_chip_lecturer, it) } ?: getString(R.string.filter_by_lecturer)
            val chipLecturer = createChip(label)
            chipLecturer.setOnClickListener {
                if (criteria.lecturerName != null) {
                    criteria = criteria.copy(lecturerName = null)
                    buildFilterChips(); refreshView()
                } else {
                    showFilterDialog("Hoca Seç", lecturers) { name ->
                        criteria = criteria.copy(lecturerName = name)
                        buildFilterChips(); refreshView()
                    }
                }
            }
            binding.chipGroupFilter.addView(chipLecturer)
        }

        val classrooms = allEntries.map { it.classroomCode }.filter { it.isNotBlank() }.distinct().sorted()
        if (classrooms.isNotEmpty()) {
            val label = criteria.classroomCode?.let { getString(R.string.filter_chip_classroom, it) } ?: getString(R.string.filter_by_classroom)
            val chipClassroom = createChip(label)
            chipClassroom.setOnClickListener {
                if (criteria.classroomCode != null) {
                    criteria = criteria.copy(classroomCode = null)
                    buildFilterChips(); refreshView()
                } else {
                    showFilterDialog("Derslik Seç", classrooms) { code ->
                        criteria = criteria.copy(classroomCode = code)
                        buildFilterChips(); refreshView()
                    }
                }
            }
            binding.chipGroupFilter.addView(chipClassroom)
        }

        val departments = allEntries.mapNotNull { it.offerings?.courses?.departments?.name }
            .filter { it.isNotBlank() }.distinct().sorted()
        if (departments.isNotEmpty()) {
            val label = criteria.departmentName?.let { getString(R.string.filter_chip_department, it) } ?: getString(R.string.filter_by_department)
            val chipDept = createChip(label)
            chipDept.setOnClickListener {
                if (criteria.departmentName != null) {
                    criteria = criteria.copy(departmentName = null)
                    buildFilterChips(); refreshView()
                } else {
                    showFilterDialog("Bölüm Seç", departments) { dept ->
                        criteria = criteria.copy(departmentName = dept)
                        buildFilterChips(); refreshView()
                    }
                }
            }
            binding.chipGroupFilter.addView(chipDept)
        }

        val classYears = allEntries.mapNotNull { it.offerings?.classYear }.distinct().sorted()
        if (classYears.isNotEmpty()) {
            val label = criteria.classYear?.let { "${it}. Sınıf ✕" } ?: "Sınıfa Göre"
            val chipYear = createChip(label)
            chipYear.setOnClickListener {
                if (criteria.classYear != null) {
                    criteria = criteria.copy(classYear = null)
                    buildFilterChips(); refreshView()
                } else {
                    val labels = classYears.map { "${it}. Sınıf" }
                    showFilterDialog("Sınıf Seç", labels) { selected ->
                        val year = selected.substringBefore(".").toIntOrNull()
                        if (year != null) {
                            criteria = criteria.copy(classYear = year)
                            buildFilterChips(); refreshView()
                        }
                    }
                }
            }
            binding.chipGroupFilter.addView(chipYear)
        }
    }

    private fun createChip(label: String): Chip {
        return Chip(requireContext()).apply {
            text = label
            isCheckable = false
            isClickable = true
        }
    }

    private fun showFilterDialog(title: String, items: List<String>, onSelect: (String) -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(items.toTypedArray()) { _, which -> onSelect(items[which]) }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun refreshView() {
        val entries = filteredEntries()
        binding.tvTitle.text = buildTitle()
        if (entries.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.weeklySchedule.setEntries(emptyList())
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.weeklySchedule.setEntries(entries)
        }
    }

    private fun showEntryDetail(entry: ScheduleEntry) {
        val msg = buildString {
            append(getString(R.string.label_course_value, "${entry.courseCode} — ${entry.courseName}"))
            append('\n')
            append(getString(R.string.label_lecturer_value, entry.lecturerName))
            append('\n')
            append(getString(R.string.label_classroom_value, entry.classroomCode))
            append('\n')
            append("${entry.timeRange}\n")
            entry.offerings?.classYear?.let {
                append(getString(R.string.label_class_year_value, "${it}."))
                append('\n')
            }
            val deptName = entry.offerings?.courses?.departments?.name
            if (!deptName.isNullOrBlank()) append(getString(R.string.label_department_value, deptName))
        }
        AlertDialog.Builder(requireContext())
            .setTitle("${entry.day} — ${entry.courseCode}")
            .setMessage(msg)
            .setPositiveButton(getString(R.string.common_ok), null)
            .show()
    }

    private var isFirstResume = true
    override fun onResume() {
        super.onResume()
        if (isFirstResume) { isFirstResume = false; return }
        viewModel.loadAllEntries()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
