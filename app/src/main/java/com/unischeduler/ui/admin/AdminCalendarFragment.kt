package com.unischeduler.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.chip.Chip
import com.unischeduler.R
import com.unischeduler.data.model.ScheduleEntry
import com.unischeduler.databinding.FragmentWeeklyScheduleBinding
import com.unischeduler.ui.lecturer.CalendarViewModel
import com.unischeduler.ui.shared.ScheduleViewConfig
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow

class AdminCalendarFragment : Fragment() {

    private var _binding: FragmentWeeklyScheduleBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CalendarViewModel by viewModels()

    private var allEntries: List<ScheduleEntry> = emptyList()
    private var activeFilter: FilterType = FilterType.ALL

    private enum class FilterType { ALL, LECTURER, CLASSROOM, DEPARTMENT }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWeeklyScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTitle.text = "Ders Programı"
        binding.btnRetry.setOnClickListener { viewModel.loadAllEntries() }
        binding.chipGroupFilter.visibility = View.VISIBLE

        binding.weeklySchedule.setOnEntryClickListener { entry ->
            showEntryDetail(entry)
        }

        collectFlow(viewModel.state) { state ->
            when (state) {
                is UiState.Idle -> viewModel.loadAllEntries()
                is UiState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvError.visibility = View.GONE
                    binding.tvEmpty.visibility = View.GONE
                    binding.btnRetry.visibility = View.GONE
                }
                is UiState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.text = state.message
                    binding.tvError.visibility = View.VISIBLE
                    binding.btnRetry.visibility = if (state.retryable) View.VISIBLE else View.GONE
                }
                is UiState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.visibility = View.GONE
                    binding.btnRetry.visibility = View.GONE

                    val data = state.data
                    allEntries = data.entries

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

                    buildFilterChips(data.entries)
                    applyFilter(data.entries)
                }
            }
        }
    }

    private fun buildFilterChips(entries: List<ScheduleEntry>) {
        binding.chipGroupFilter.removeAllViews()

        val chipAll = createChip("Tümü")
        chipAll.isChecked = true
        chipAll.setOnClickListener {
            activeFilter = FilterType.ALL
            applyFilter(allEntries)
        }
        binding.chipGroupFilter.addView(chipAll)

        val lecturers = entries.map { it.lecturerName }.distinct().sorted()
        if (lecturers.isNotEmpty()) {
            val chipLecturer = createChip("Hocaya Göre")
            chipLecturer.setOnClickListener { showFilterDialog("Hoca Seç", lecturers) { name ->
                activeFilter = FilterType.LECTURER
                val filtered = allEntries.filter { it.lecturerName == name }
                showFiltered(filtered, "Hoca: $name")
            }}
            binding.chipGroupFilter.addView(chipLecturer)
        }

        val classrooms = entries.map { it.classroomCode }.filter { it.isNotBlank() }.distinct().sorted()
        if (classrooms.isNotEmpty()) {
            val chipClassroom = createChip("Dersliğe Göre")
            chipClassroom.setOnClickListener { showFilterDialog("Derslik Seç", classrooms) { code ->
                activeFilter = FilterType.CLASSROOM
                val filtered = allEntries.filter { it.classroomCode == code }
                showFiltered(filtered, "Derslik: $code")
            }}
            binding.chipGroupFilter.addView(chipClassroom)
        }

        val departments = entries.mapNotNull { it.offerings?.courses?.departments?.name }.distinct().sorted()
        if (departments.isNotEmpty()) {
            val chipDept = createChip("Bölüme Göre")
            chipDept.setOnClickListener { showFilterDialog("Bölüm Seç", departments) { dept ->
                activeFilter = FilterType.DEPARTMENT
                val filtered = allEntries.filter { it.offerings?.courses?.departments?.name == dept }
                showFiltered(filtered, "Bölüm: $dept")
            }}
            binding.chipGroupFilter.addView(chipDept)
        }
    }

    private fun createChip(label: String): Chip {
        return Chip(requireContext()).apply {
            text = label
            isCheckable = true
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

    private fun applyFilter(entries: List<ScheduleEntry>) {
        if (entries.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.weeklySchedule.setEntries(emptyList())
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.weeklySchedule.setEntries(entries)
        }
    }

    private fun showFiltered(entries: List<ScheduleEntry>, label: String) {
        binding.tvTitle.text = label
        applyFilter(entries)
    }

    private fun showEntryDetail(entry: ScheduleEntry) {
        val msg = buildString {
            append("Ders: ${entry.courseCode} — ${entry.courseName}\n")
            append("Hoca: ${entry.lecturerName}\n")
            append("Sınıf: ${entry.classroomCode}\n")
            append("Saat: ${entry.timeRange}\n")
            val deptName = entry.offerings?.courses?.departments?.name
            if (!deptName.isNullOrBlank()) append("Bölüm: $deptName")
        }
        AlertDialog.Builder(requireContext())
            .setTitle("${entry.day} — ${entry.courseCode}")
            .setMessage(msg)
            .setPositiveButton(getString(R.string.common_ok), null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
