package com.unischeduler.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.unischeduler.R
import com.unischeduler.data.model.Course
import com.unischeduler.data.model.Department
import com.unischeduler.data.model.Lecturer
import com.unischeduler.data.model.Offering
import com.unischeduler.databinding.FragmentDataBinding
import com.unischeduler.databinding.ItemCourseBinding
import com.unischeduler.databinding.ItemLecturerBinding
import com.unischeduler.databinding.ItemOfferingBinding
import com.unischeduler.util.CsvImporter
import com.unischeduler.util.ErrorReporter
import com.unischeduler.util.ExcelHelper
import com.unischeduler.util.FileTypeDetector
import com.unischeduler.util.ImportPreviewDialog
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class DataFragment : Fragment() {

    private var _binding: FragmentDataBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DataViewModel by viewModels()

    private var departments: List<Department> = emptyList()
    private var allCourses: List<Course>         = emptyList()
    private var allLecturers: List<Lecturer>     = emptyList()
    private var allOfferings: List<Offering>     = emptyList()
    private var courses: List<Course>         = emptyList()
    private var lecturers: List<Lecturer>     = emptyList()

    private var selectedDeptId: Int? = null
    // Tab değişikliklerinden sonra arka plandaki verinin de güncel kalması için
    // onResume'da yeniden yüklüyoruz. İlk açılışta ViewModel zaten Idle->load
    // tetikliyor, çift yüklemeyi önlemek için flag'liyoruz.
    private var isFirstResume = true

    private val academicTitles = listOf("Dr.", "Prof.", "Asst. Prof.", "Lecturer", "Mr.", "Ms.")
    private val terms          = listOf("Fall", "Spring", "Summer")
    private val classYears     = listOf(1, 2, 3, 4)

    // ── File pickers ──────────────────────────────────────────────────────────
    private lateinit var courseImportLauncher: ActivityResultLauncher<String>
    private lateinit var lecturerImportLauncher: ActivityResultLauncher<String>
    private lateinit var courseExportLauncher: ActivityResultLauncher<String>
    private lateinit var lecturerExportLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        courseImportLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            handleCourseImport(uri)
        }

        lecturerImportLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            handleLecturerImport(uri)
        }

        courseExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
            uri ?: return@registerForActivityResult
            performExportAsync(uri, getString(R.string.data_course_export_success)) { stream ->
                ExcelHelper.exportCourses(courses, stream)
            }
        }

        lecturerExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
            uri ?: return@registerForActivityResult
            performExportAsync(uri, getString(R.string.data_lecturer_export_success)) { stream ->
                ExcelHelper.exportLecturers(lecturers, stream)
            }
        }
    }

    private fun performExportAsync(
        uri: android.net.Uri,
        successMessage: String,
        write: (java.io.OutputStream) -> Unit
    ) {
        val ctx = requireContext().applicationContext
        viewLifecycleOwnerLiveData.value?.lifecycleScope?.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val out = ctx.contentResolver.openOutputStream(uri)
                        ?: error("Dosya açılamadı")
                    out.use { write(it) }
                }
            }
            if (!isAdded) return@launch
            result
                .onSuccess { Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show() }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    Toast.makeText(requireContext(), getString(R.string.data_export_fail, it.message), Toast.LENGTH_LONG).show()
                }
        } ?: lifecycleScope.launch {
            // Fallback: view destroyed before launcher returned — swallow silently
            runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { write(it) }
                }
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Adapters set once and reused across data updates (DiffUtil computes diffs).
    private val courseAdapter   by lazy { CourseAdapter({ showEditCourseDialog(it) }, { showDeleteCourseDialog(it) }) }
    private val offeringAdapter by lazy { OfferingAdapter({ showEditOfferingDialog(it) }, { showDeleteOfferingDialog(it) }) }
    private val lecturerAdapter by lazy { LecturerAdapter({ showEditLecturerDialog(it) }, { showDeleteLecturerDialog(it) }) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupStaticSpinners()
        setupAccordion()
        setupDefaults()
        binding.rvCourses.layoutManager   = LinearLayoutManager(requireContext())
        binding.rvCourses.adapter         = courseAdapter
        binding.rvCourses.isNestedScrollingEnabled = false
        binding.rvCourses.setItemViewCacheSize(20)
        binding.rvOfferings.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOfferings.adapter       = offeringAdapter
        binding.rvOfferings.isNestedScrollingEnabled = false
        binding.rvOfferings.setItemViewCacheSize(20)
        binding.rvLecturers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLecturers.adapter       = lecturerAdapter
        binding.rvLecturers.isNestedScrollingEnabled = false
        binding.rvLecturers.setItemViewCacheSize(20)

        binding.btnRetryLecturers.setOnClickListener    { viewModel.loadLecturers() }
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadDepartments()
            viewModel.loadLecturers()
            viewModel.loadCourses()
            viewModel.loadOfferings()
        }
        binding.btnAddLecturer.setOnClickListener       { onAddLecturerClicked() }
        binding.btnImportLecturers.setOnClickListener   { lecturerImportLauncher.launch("*/*") }
        binding.btnExportLecturers.setOnClickListener   { lecturerExportLauncher.launch("lecturers.xlsx") }
        binding.btnAddCourse.setOnClickListener         { onAddCourseClicked() }
        binding.btnImportCourses.setOnClickListener     { courseImportLauncher.launch("*/*") }
        binding.btnExportCourses.setOnClickListener     { courseExportLauncher.launch("courses.xlsx") }
        binding.btnAddOffering.setOnClickListener       { onAddOfferingClicked() }

        observeStates()
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeStates() {
        collectFlow(viewModel.deptState) { state ->
            when (state) {
                is UiState.Idle    -> viewModel.loadDepartments()
                is UiState.Loading -> binding.progressDepts.visibility = View.VISIBLE
                is UiState.Error   -> {
                    binding.progressDepts.visibility = View.GONE
                    binding.tvDeptsError.text        = state.message
                    binding.tvDeptsError.visibility  = View.VISIBLE
                }
                is UiState.Success -> {
                    binding.progressDepts.visibility = View.GONE
                    binding.tvDeptsError.visibility  = View.GONE
                    departments = state.data
                    populateDeptSpinners(state.data)
                }
            }
        }

        collectFlow(viewModel.lecturerAddState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> {
                    binding.btnAddLecturer.isEnabled      = false
                    binding.tvLecturerAddError.visibility = View.GONE
                }
                is UiState.Error   -> {
                    binding.btnAddLecturer.isEnabled      = true
                    binding.tvLecturerAddError.text       = state.message
                    binding.tvLecturerAddError.visibility = View.VISIBLE
                }
                is UiState.Success -> {
                    binding.btnAddLecturer.isEnabled      = true
                    binding.tvLecturerAddError.visibility = View.GONE
                    clearLecturerForm()
                    showCredentialsDialog(state.data.username, state.data.password)
                    viewModel.loadLecturers()
                    viewModel.resetLecturerAddState()
                }
            }
        }

        collectFlow(viewModel.lecturerListState) { state ->
            when (state) {
                is UiState.Idle    -> viewModel.loadLecturers()
                is UiState.Loading -> {
                    binding.progressLecturers.visibility   = View.VISIBLE
                    binding.tvLecturerListError.visibility = View.GONE
                    binding.btnRetryLecturers.visibility   = View.GONE
                }
                is UiState.Error   -> {
                    binding.progressLecturers.visibility   = View.GONE
                    binding.tvLecturerListError.text       = state.message
                    binding.tvLecturerListError.visibility = View.VISIBLE
                    binding.btnRetryLecturers.visibility   = if (state.retryable) View.VISIBLE else View.GONE
                    binding.swipeRefresh.isRefreshing      = false
                }
                is UiState.Success -> {
                    binding.progressLecturers.visibility   = View.GONE
                    binding.tvLecturerListError.visibility = View.GONE
                    binding.btnRetryLecturers.visibility   = View.GONE
                    allLecturers = state.data
                    binding.swipeRefresh.isRefreshing      = false
                    applyDeptFilter()
                }
            }
        }

        collectFlow(viewModel.lecturerDeleteState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> Unit
                is UiState.Error   -> {
                    binding.tvLecturerListError.text       = state.message
                    binding.tvLecturerListError.visibility = View.VISIBLE
                }
                is UiState.Success -> {
                    Toast.makeText(requireContext(), getString(R.string.data_lecturer_deleted), Toast.LENGTH_SHORT).show()
                    viewModel.resetLecturerDeleteState()
                }
            }
        }

        collectFlow(viewModel.lecturerImportState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> binding.btnImportLecturers.isEnabled = false
                is UiState.Error   -> {
                    binding.btnImportLecturers.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetLecturerImportState()
                }
                is UiState.Success -> {
                    binding.btnImportLecturers.isEnabled = true
                    showLecturerImportResult(state.data)
                    viewModel.loadLecturers()
                    viewModel.resetLecturerImportState()
                }
            }
        }

        collectFlow(viewModel.courseAddState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> {
                    binding.btnAddCourse.isEnabled      = false
                    binding.tvCourseAddError.visibility = View.GONE
                }
                is UiState.Error   -> {
                    binding.btnAddCourse.isEnabled      = true
                    binding.tvCourseAddError.text       = state.message
                    binding.tvCourseAddError.visibility = View.VISIBLE
                }
                is UiState.Success -> {
                    binding.btnAddCourse.isEnabled      = true
                    binding.tvCourseAddError.visibility = View.GONE
                    clearCourseForm()
                    Toast.makeText(requireContext(), getString(R.string.data_course_added), Toast.LENGTH_SHORT).show()
                    viewModel.resetCourseAddState()
                    viewModel.loadCourses()
                }
            }
        }

        collectFlow(viewModel.courseListState) { state ->
            when (state) {
                is UiState.Idle    -> viewModel.loadCourses()
                is UiState.Success -> {
                    allCourses = state.data
                    applyDeptFilter()
                }
                else -> Unit
            }
        }

        collectFlow(viewModel.courseImportState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> binding.btnImportCourses.isEnabled = false
                is UiState.Error   -> {
                    binding.btnImportCourses.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetCourseImportState()
                }
                is UiState.Success -> {
                    binding.btnImportCourses.isEnabled = true
                    Toast.makeText(requireContext(), getString(R.string.data_course_import_count, state.data), Toast.LENGTH_SHORT).show()
                    viewModel.resetCourseImportState()
                }
            }
        }

        collectFlow(viewModel.offeringAddState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> {
                    binding.btnAddOffering.isEnabled   = false
                    binding.tvOfferingError.visibility = View.GONE
                }
                is UiState.Error   -> {
                    binding.btnAddOffering.isEnabled   = true
                    binding.tvOfferingError.text       = state.message
                    binding.tvOfferingError.visibility = View.VISIBLE
                }
                is UiState.Success -> {
                    binding.btnAddOffering.isEnabled   = true
                    binding.tvOfferingError.visibility = View.GONE
                    clearOfferingForm()
                    Toast.makeText(requireContext(), getString(R.string.data_offering_added), Toast.LENGTH_SHORT).show()
                    viewModel.resetOfferingAddState()
                }
            }
        }

        // ── Settings navigation ────────────────────────────────────────────────
        binding.btnGoSettings.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }

        // ── Offering list ──────────────────────────────────────────────────────
        collectFlow(viewModel.offeringListState) { state ->
            when (state) {
                is UiState.Idle    -> viewModel.loadOfferings()
                is UiState.Loading -> binding.progressOfferings.visibility = View.VISIBLE
                is UiState.Error   -> {
                    binding.progressOfferings.visibility = View.GONE
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
                is UiState.Success -> {
                    binding.progressOfferings.visibility = View.GONE
                    allOfferings = state.data
                    applyDeptFilter()
                }
            }
        }

        collectFlow(viewModel.lecturerEditState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> Unit
                is UiState.Error   -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetLecturerEditState()
                }
                is UiState.Success -> {
                    Toast.makeText(requireContext(), getString(R.string.data_lecturer_updated), Toast.LENGTH_SHORT).show()
                    viewModel.resetLecturerEditState()
                }
            }
        }

        collectFlow(viewModel.offeringEditState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> Unit
                is UiState.Error   -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetOfferingEditState()
                }
                is UiState.Success -> {
                    Toast.makeText(requireContext(), getString(R.string.data_offering_updated), Toast.LENGTH_SHORT).show()
                    viewModel.resetOfferingEditState()
                }
            }
        }
    }

    // ── Form actions ──────────────────────────────────────────────────────────

    private fun onAddLecturerClicked() {
        if (departments.isEmpty()) {
            showFieldError(binding.tvLecturerAddError, getString(R.string.data_no_dept_error))
            return
        }
        viewModel.addLecturer(
            title        = academicTitles[binding.spinnerTitle.selectedItemPosition],
            firstName    = binding.etFirstName.text?.toString().orEmpty(),
            lastName     = binding.etLastName.text?.toString().orEmpty(),
            departmentId = departments[binding.spinnerLecturerDept.selectedItemPosition].id
        )
    }

    private fun onAddCourseClicked() {
        if (departments.isEmpty()) {
            showFieldError(binding.tvCourseAddError, getString(R.string.data_no_dept_error))
            return
        }
        viewModel.addCourse(
            code         = binding.etCourseCode.text?.toString().orEmpty(),
            name         = binding.etCourseName.text?.toString().orEmpty(),
            departmentId = departments[binding.spinnerCourseDept.selectedItemPosition].id,
            theoryHours  = binding.etTheoryHours.text?.toString()?.toIntOrNull() ?: 0,
            labHours     = binding.etLabHours.text?.toString()?.toIntOrNull() ?: 0,
            credits      = binding.etCredits.text?.toString()?.toIntOrNull() ?: 0
        )
    }

    private fun onAddOfferingClicked() {
        if (courses.isEmpty()) {
            showFieldError(binding.tvOfferingError, getString(R.string.data_no_courses_error))
            return
        }
        viewModel.addOffering(
            courseId      = courses[binding.spinnerOfferingCourse.selectedItemPosition].id,
            lecturerId   = getSelectedLecturerId(),
            academicYear = binding.etAcademicYear.text?.toString().orEmpty(),
            term         = terms[binding.spinnerTerm.selectedItemPosition],
            classYear    = classYears[binding.spinnerClassYear.selectedItemPosition],
            section      = binding.etSection.text?.toString().orEmpty(),
            capacity     = binding.etOfferingCapacity.text?.toString().orEmpty()
        )
    }

    // ── Edit / Delete dialogs ─────────────────────────────────────────────────

    private fun showEditCourseDialog(course: Course) {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val etCode = EditText(requireContext()).apply { setText(course.code); hint = "Code" }
        val etName = EditText(requireContext()).apply { setText(course.name); hint = "Name" }
        val etTheory = EditText(requireContext()).apply { setText(course.theoryHours.toString()); hint = "Theory Hours"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val etLab = EditText(requireContext()).apply { setText(course.labHours.toString()); hint = "Lab Hours"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val etCredits = EditText(requireContext()).apply { setText(course.credits.toString()); hint = "Credits"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }

        layout.addView(etCode)
        layout.addView(etName)
        layout.addView(etTheory)
        layout.addView(etLab)
        layout.addView(etCredits)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.data_course_edit_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.common_save)) { _, _ ->
                viewModel.updateCourse(
                    id = course.id,
                    code = etCode.text.toString(),
                    name = etName.text.toString(),
                    theoryHours = etTheory.text.toString().toIntOrNull() ?: 0,
                    labHours = etLab.text.toString().toIntOrNull() ?: 0,
                    credits = etCredits.text.toString().toIntOrNull() ?: 0
                )
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showDeleteCourseDialog(course: Course) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.data_course_delete_title))
            .setMessage(getString(R.string.data_course_delete_message, course.code, course.name))
            .setPositiveButton(getString(R.string.common_delete)) { _, _ -> viewModel.deleteCourse(course.id) }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showEditLecturerDialog(lecturer: Lecturer) {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val spinnerTitle = Spinner(requireContext())
        spinnerTitle.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, academicTitles)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val titleIdx = academicTitles.indexOf(lecturer.title).takeIf { it >= 0 } ?: 0
        spinnerTitle.setSelection(titleIdx)

        val etFirst = EditText(requireContext()).apply { setText(lecturer.firstName); hint = "First Name" }
        val etLast  = EditText(requireContext()).apply { setText(lecturer.lastName); hint = "Last Name" }
        val etEmail = EditText(requireContext()).apply { setText(lecturer.email ?: ""); hint = "Email (optional)" }

        val spinnerDept = Spinner(requireContext())
        spinnerDept.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, departments.map { it.name })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val deptIdx = departments.indexOfFirst { it.id == lecturer.departmentId }.takeIf { it >= 0 } ?: 0
        spinnerDept.setSelection(deptIdx)

        layout.addView(TextView(requireContext()).apply { text = "Title" })
        layout.addView(spinnerTitle)
        layout.addView(etFirst)
        layout.addView(etLast)
        layout.addView(etEmail)
        layout.addView(TextView(requireContext()).apply { text = "Department" })
        layout.addView(spinnerDept)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.data_lecturer_edit_title))
            .setView(ScrollView(requireContext()).apply { addView(layout) })
            .setPositiveButton(getString(R.string.common_save)) { _, _ ->
                viewModel.editLecturer(
                    id = lecturer.id,
                    title = academicTitles[spinnerTitle.selectedItemPosition],
                    firstName = etFirst.text.toString(),
                    lastName = etLast.text.toString(),
                    departmentId = departments[spinnerDept.selectedItemPosition].id,
                    email = etEmail.text.toString().takeIf { it.isNotBlank() }
                )
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showEditOfferingDialog(offering: Offering) {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val etYear    = EditText(requireContext()).apply { setText(offering.academicYear); hint = "Academic Year" }
        val etSection = EditText(requireContext()).apply { setText(offering.section); hint = "Section" }
        val etCap     = EditText(requireContext()).apply { setText(offering.capacity.toString()); hint = "Capacity"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }

        val spinnerTerm = Spinner(requireContext())
        spinnerTerm.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, terms)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerTerm.setSelection(terms.indexOf(offering.term).takeIf { it >= 0 } ?: 0)

        val spinnerYear = Spinner(requireContext())
        spinnerYear.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, classYears.map { "Year $it" })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerYear.setSelection(classYears.indexOf(offering.classYear).takeIf { it >= 0 } ?: 0)

        val lecturerNames = listOf(getString(R.string.common_not_assigned)) + lecturers.map { it.fullName }
        val spinnerLecturer = Spinner(requireContext())
        spinnerLecturer.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, lecturerNames)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val lecturerIdx = if (offering.lecturerId != null) {
            lecturers.indexOfFirst { it.id == offering.lecturerId }.takeIf { it >= 0 }?.plus(1) ?: 0
        } else 0
        spinnerLecturer.setSelection(lecturerIdx)

        layout.addView(etYear)
        layout.addView(TextView(requireContext()).apply { text = "Term" })
        layout.addView(spinnerTerm)
        layout.addView(TextView(requireContext()).apply { text = "Class Year" })
        layout.addView(spinnerYear)
        layout.addView(etSection)
        layout.addView(etCap)
        layout.addView(TextView(requireContext()).apply { text = "Lecturer" })
        layout.addView(spinnerLecturer)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.data_offering_edit_title))
            .setView(ScrollView(requireContext()).apply { addView(layout) })
            .setPositiveButton(getString(R.string.common_save)) { _, _ ->
                val selectedLecturerId = if (spinnerLecturer.selectedItemPosition > 0)
                    lecturers[spinnerLecturer.selectedItemPosition - 1].id else null
                viewModel.editOffering(
                    id = offering.id,
                    lecturerId = selectedLecturerId,
                    academicYear = etYear.text.toString(),
                    term = terms[spinnerTerm.selectedItemPosition],
                    classYear = classYears[spinnerYear.selectedItemPosition],
                    section = etSection.text.toString(),
                    capacity = etCap.text.toString().toIntOrNull() ?: 0
                )
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showDeleteOfferingDialog(offering: Offering) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.data_offering_delete_title))
            .setMessage(getString(R.string.data_offering_delete_message, offering.courseName, offering.section))
            .setPositiveButton(getString(R.string.common_delete)) { _, _ -> viewModel.deleteOffering(offering.id) }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    // ── Import preview dialogs ────────────────────────────────────────────────

    private fun showCourseImportPreview(result: CsvImporter.ParseResult<CsvImporter.CourseRow>) {
        if (departments.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.import_load_depts_first), Toast.LENGTH_LONG).show()
            return
        }
        val deptPos  = binding.spinnerCourseDept.selectedItemPosition
        val deptName = departments.getOrNull(deptPos)?.name ?: "Bilinmiyor"
        val deptId   = departments.getOrNull(deptPos)?.id ?: return

        if (result.valid.isEmpty()) {
            val errMsg = if (result.errors.isEmpty()) getString(R.string.import_preview_no_rows)
            else getString(R.string.import_preview_no_valid_rows, result.errors.size) + "\n" +
                 result.errors.take(5).joinToString("\n") { "• $it" }
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.import_dialog_title))
                .setMessage(errMsg)
                .setPositiveButton(getString(R.string.common_ok), null)
                .show()
            return
        }

        ImportPreviewDialog.show(
            context = requireContext(),
            title = getString(R.string.import_preview_title_courses),
            targetDescription = "Bölüm: $deptName",
            rows = result.valid,
            errors = result.errors,
            titleProvider = { "${it.code} — ${it.name}" },
            subtitleProvider = { "Teori: ${it.theoryHours}  Lab: ${it.labHours}  Kredi: ${it.credits}" },
            onImport = { chosen -> viewModel.importCourses(chosen, deptId) }
        )
    }

    private fun showLecturerImportPreview(result: CsvImporter.ParseResult<CsvImporter.LecturerRow>) {
        if (departments.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.import_load_depts_first), Toast.LENGTH_LONG).show()
            return
        }
        val deptPos  = binding.spinnerLecturerDept.selectedItemPosition
        val deptName = departments.getOrNull(deptPos)?.name ?: "Bilinmiyor"
        val deptId   = departments.getOrNull(deptPos)?.id ?: return

        if (result.valid.isEmpty()) {
            val errMsg = if (result.errors.isEmpty()) getString(R.string.import_preview_no_rows)
            else getString(R.string.import_preview_no_valid_rows, result.errors.size) + "\n" +
                 result.errors.take(5).joinToString("\n") { "• $it" }
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.import_dialog_title))
                .setMessage(errMsg)
                .setPositiveButton(getString(R.string.common_ok), null)
                .show()
            return
        }

        ImportPreviewDialog.show(
            context = requireContext(),
            title = getString(R.string.import_preview_title_lecturers),
            targetDescription = getString(R.string.import_lecturer_target, deptName),
            rows = result.valid,
            errors = result.errors,
            titleProvider = { "${it.title} ${it.firstName} ${it.lastName}".trim() },
            subtitleProvider = { it.email?.takeIf { e -> e.isNotBlank() } ?: "E-posta yok" },
            onImport = { chosen -> viewModel.importLecturers(chosen, deptId) }
        )
    }

    private fun showLecturerImportResult(result: LecturerImportResult) {
        val credentialsBlock = result.credentials.joinToString("\n") { (u, p) -> "$u  /  $p" }
        val sb = StringBuilder()
        sb.appendLine("İçe aktarılan: ${result.imported}")
        if (result.errors.isNotEmpty()) sb.appendLine("Başarısız: ${result.errors.size}")
        sb.appendLine()
        sb.appendLine("Oluşturulan kullanıcı bilgileri:")
        sb.appendLine(credentialsBlock)
        if (result.errors.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Hatalar:")
            result.errors.forEach { sb.appendLine("• $it") }
        }

        val tv = TextView(requireContext()).apply {
            text = sb.toString()
            setPadding(48, 32, 48, 16)
            textSize = 13f
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(requireContext()).apply { addView(tv) }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.import_result_title))
            .setView(scroll)
            .setPositiveButton(getString(R.string.common_ok), null)
            .setNeutralButton(getString(R.string.data_copy_users)) { _, _ ->
                val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Hocalar", credentialsBlock))
                Toast.makeText(requireContext(), getString(R.string.data_all_users_copied), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun showCredentialsDialog(username: String, password: String) {
        val text = "Hocaya paylaşmak için bu bilgileri kullanın:\n\n" +
                   "Kullanıcı Adı: $username\n" +
                   "Şifre: $password\n\n" +
                   "İlk girişte hoca şifresini değiştirmek zorunda kalacak."
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.data_lecturer_added_title))
            .setMessage(text)
            .setPositiveButton(getString(R.string.common_ok), null)
            .setNeutralButton(getString(R.string.common_copy_clipboard)) { _, _ ->
                val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText(
                    "Hoca Bilgileri", "$username  /  $password"
                ))
                Toast.makeText(requireContext(), getString(R.string.common_copied_clipboard), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showDeleteLecturerDialog(lecturer: Lecturer) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.data_lecturer_delete_title))
            .setMessage(getString(R.string.data_lecturer_delete_message, lecturer.fullName))
            .setPositiveButton(getString(R.string.common_delete)) { _, _ -> viewModel.deleteLecturer(lecturer) }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showFieldError(tv: TextView, msg: String) {
        tv.text       = msg
        tv.visibility = View.VISIBLE
    }

    private fun setupAccordion() {
        fun toggle(content: View, arrow: View) {
            if (content.visibility == View.GONE) {
                content.visibility = View.VISIBLE
                arrow.rotation = 180f
            } else {
                content.visibility = View.GONE
                arrow.rotation = 0f
            }
        }

        binding.headerLecturers.setOnClickListener {
            toggle(binding.contentLecturers, binding.ivExpandLecturers)
        }
        binding.headerCourses.setOnClickListener {
            toggle(binding.contentCourses, binding.ivExpandCourses)
        }
        binding.headerOfferings.setOnClickListener {
            toggle(binding.contentOfferings, binding.ivExpandOfferings)
        }
    }

    private fun setupDefaults() {
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
        val academicYear = if (month >= 8) "$year-${year + 1}" else "${year - 1}-$year"
        binding.etAcademicYear.setText(academicYear)

        val termIdx = if (month in 1..6) 1 else 0
        binding.spinnerTerm.setSelection(termIdx)

        binding.etSection.setText("A")
    }

    private fun setupStaticSpinners() {
        binding.spinnerTitle.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, academicTitles
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.spinnerTerm.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, terms
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.spinnerClassYear.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, classYears.map { "Year $it" }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun populateDeptSpinners(depts: List<Department>) {
        val names   = depts.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerLecturerDept.adapter = adapter
        binding.spinnerCourseDept.adapter   = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, names
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val filterNames = listOf(getString(R.string.common_all_departments)) + names
        binding.spinnerDeptFilter.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, filterNames
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.spinnerDeptFilter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedDeptId = if (pos == 0) null else depts[pos - 1].id
                applyDeptFilter()

                if (pos > 0) {
                    binding.spinnerLecturerDept.setSelection(pos - 1)
                    binding.spinnerCourseDept.setSelection(pos - 1)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun applyDeptFilter() {
        val deptId = selectedDeptId

        val filteredLecturers = if (deptId != null) allLecturers.filter { it.departmentId == deptId } else allLecturers
        lecturers = filteredLecturers
        populateLecturerSpinner()
        binding.tvLecturerHeader.text = getString(R.string.data_lecturers_header_count, filteredLecturers.size)
        populateLecturerList(filteredLecturers)

        val filteredCourses = if (deptId != null) allCourses.filter { it.departmentId == deptId } else allCourses
        courses = filteredCourses
        populateCourseSpinner(filteredCourses)
        binding.tvCourseHeader.text = getString(R.string.data_courses_header_count, filteredCourses.size)
        courseAdapter.submitList(filteredCourses)
        binding.tvCoursesEmpty.visibility = if (filteredCourses.isEmpty()) View.VISIBLE else View.GONE
        binding.rvCourses.visibility      = if (filteredCourses.isEmpty()) View.GONE else View.VISIBLE

        val filteredOfferings = if (deptId != null) {
            allOfferings.filter { it.courses?.departmentId == deptId }
        } else allOfferings
        binding.tvOfferingHeader.text = getString(R.string.data_offerings_header_count, filteredOfferings.size)
        offeringAdapter.submitList(filteredOfferings)
        binding.tvOfferingsEmpty.visibility = if (filteredOfferings.isEmpty()) View.VISIBLE else View.GONE
        binding.rvOfferings.visibility      = if (filteredOfferings.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun populateLecturerList(items: List<Lecturer>) {
        if (items.isEmpty()) {
            binding.tvLecturerEmpty.visibility = View.VISIBLE
            binding.rvLecturers.visibility = View.GONE
        } else {
            binding.tvLecturerEmpty.visibility = View.GONE
            binding.rvLecturers.visibility = View.VISIBLE
        }
        lecturerAdapter.submitList(items)
    }

    private fun populateCourseSpinner(courseList: List<Course>) {
        binding.spinnerOfferingCourse.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, courseList.map { it.displayName }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun populateLecturerSpinner() {
        val names = listOf(getString(R.string.common_not_assigned)) + lecturers.map { it.fullName }
        binding.spinnerOfferingLecturer.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, names
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun getSelectedLecturerId(): Int? {
        val pos = binding.spinnerOfferingLecturer.selectedItemPosition
        return if (pos > 0) lecturers[pos - 1].id else null
    }

    private fun clearLecturerForm() {
        binding.etFirstName.text?.clear()
        binding.etLastName.text?.clear()
        binding.spinnerTitle.setSelection(0)
    }

    private fun clearCourseForm() {
        binding.etCourseCode.text?.clear()
        binding.etCourseName.text?.clear()
        binding.etTheoryHours.text?.clear()
        binding.etLabHours.text?.clear()
        binding.etCredits.text?.clear()
    }

    private fun clearOfferingForm() {
        binding.etOfferingCapacity.text?.clear()
        binding.spinnerOfferingLecturer.setSelection(0)
        setupDefaults()
    }

    // ── Import dispatch (file-type aware) ─────────────────────────────────────

    private fun handleCourseImport(uri: android.net.Uri) {
        val appCtx = requireContext().applicationContext
        binding.btnImportCourses.isEnabled = false
        lifecycleScope.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val kind = FileTypeDetector.detect(appCtx, uri)
                    val parsed = parseCourseFile(appCtx, uri, kind)
                    Triple(kind, parsed, null as Throwable?)
                }
            }
            if (!isAdded) return@launch
            binding.btnImportCourses.isEnabled = true
            result
                .onSuccess { (kind, parseResult, _) ->
                    logImportErrors("import_courses", uri, kind, parseResult.errors, parseResult.valid.size)
                    showCourseImportPreview(parseResult)
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    reportImportFatal("import_courses", uri, it)
                    showImportFatal("Dosya okuma hatası: ${it.message ?: it::class.java.simpleName}")
                }
        }
    }

    private fun handleLecturerImport(uri: android.net.Uri) {
        val appCtx = requireContext().applicationContext
        binding.btnImportLecturers.isEnabled = false
        lifecycleScope.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val kind = FileTypeDetector.detect(appCtx, uri)
                    val parsed = parseLecturerFile(appCtx, uri, kind)
                    Triple(kind, parsed, null as Throwable?)
                }
            }
            if (!isAdded) return@launch
            binding.btnImportLecturers.isEnabled = true
            result
                .onSuccess { (kind, parseResult, _) ->
                    logImportErrors("import_lecturers", uri, kind, parseResult.errors, parseResult.valid.size)
                    showLecturerImportPreview(parseResult)
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    reportImportFatal("import_lecturers", uri, it)
                    showImportFatal("Dosya okuma hatası: ${it.message ?: it::class.java.simpleName}")
                }
        }
    }

    private fun parseCourseFile(
        ctx: android.content.Context,
        uri: android.net.Uri,
        kind: FileTypeDetector.Kind
    ): CsvImporter.ParseResult<CsvImporter.CourseRow> = when (kind) {
        FileTypeDetector.Kind.XLSX, FileTypeDetector.Kind.XLS -> {
            val stream = ctx.contentResolver.openInputStream(uri) ?: error("Dosya açılamadı.")
            stream.use {
                val r = ExcelHelper.importCourses(it)
                CsvImporter.ParseResult(r.valid, r.errors)
            }
        }
        FileTypeDetector.Kind.CSV -> {
            val stream = ctx.contentResolver.openInputStream(uri) ?: error("Dosya açılamadı.")
            val text = stream.use { it.bufferedReader().readText() }
            CsvImporter.parseCourses(text)
        }
        FileTypeDetector.Kind.UNKNOWN ->
            error("Dosya tipi tanınamadı. Lütfen .xlsx, .xls veya .csv yükleyin.")
    }

    private fun parseLecturerFile(
        ctx: android.content.Context,
        uri: android.net.Uri,
        kind: FileTypeDetector.Kind
    ): CsvImporter.ParseResult<CsvImporter.LecturerRow> = when (kind) {
        FileTypeDetector.Kind.XLSX, FileTypeDetector.Kind.XLS -> {
            val stream = ctx.contentResolver.openInputStream(uri) ?: error("Dosya açılamadı.")
            stream.use {
                val r = ExcelHelper.importLecturers(it)
                CsvImporter.ParseResult(r.valid, r.errors)
            }
        }
        FileTypeDetector.Kind.CSV -> {
            val stream = ctx.contentResolver.openInputStream(uri) ?: error("Dosya açılamadı.")
            val text = stream.use { it.bufferedReader().readText() }
            CsvImporter.parseLecturers(text)
        }
        FileTypeDetector.Kind.UNKNOWN ->
            error("Dosya tipi tanınamadı. Lütfen .xlsx, .xls veya .csv yükleyin.")
    }

    private fun showImportFatal(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.import_error_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.common_ok), null)
            .show()
    }

    /** Per-row validation errors → backend log so admins can see failed
     *  imports without the user having to forward a screenshot. */
    private fun logImportErrors(
        action: String,
        uri: android.net.Uri,
        kind: FileTypeDetector.Kind,
        errors: List<String>,
        validCount: Int
    ) {
        if (errors.isEmpty()) return
        val reporter = ErrorReporter(requireActivity().application)
        val summary = "import: $kind, geçerli=$validCount, hatalı=${errors.size}\n" +
                      errors.take(20).joinToString("\n") { "• $it" } +
                      if (errors.size > 20) "\n…ve ${errors.size - 20} hata daha" else ""
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { reporter.reportMessage("DataFragment", action, summary, stackTrace = uri.toString()) }
        }
    }

    /** Whole-file failure (couldn't even parse) → log with stack. */
    private fun reportImportFatal(action: String, uri: android.net.Uri, e: Throwable) {
        val reporter = ErrorReporter(requireActivity().application)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { reporter.reportException("DataFragment", "$action[${uri}]", e) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFirstResume) {
            isFirstResume = false
            return
        }
        // Tab'a tekrar dönüldüğünde Settings'te eklenen departmanlar /
        // ders, hoca, atama listelerinin güncel kalması için yeniden çek.
        viewModel.loadDepartments()
        viewModel.loadLecturers()
        viewModel.loadCourses()
        viewModel.loadOfferings()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Course Adapter ─────────���─────────────────────────────────────────────────

class CourseAdapter(
    private val onEdit: (Course) -> Unit,
    private val onDelete: (Course) -> Unit
) : ListAdapter<Course, CourseAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemCourseBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCourseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = getItem(position)
        holder.binding.tvCourseCode.text    = c.code
        holder.binding.tvCourseName.text    = c.name
        holder.binding.tvCourseDetails.text = "T:${c.theoryHours} L:${c.labHours} C:${c.credits} • ${c.departmentName}"
        holder.binding.btnEditCourse.visibility   = View.VISIBLE
        holder.binding.btnDeleteCourse.visibility = View.VISIBLE
        holder.binding.btnEditCourse.setOnClickListener   { onEdit(c) }
        holder.binding.btnDeleteCourse.setOnClickListener { onDelete(c) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Course>() {
            override fun areItemsTheSame(old: Course, new: Course)    = old.id == new.id
            override fun areContentsTheSame(old: Course, new: Course) = old == new
        }
    }
}


// ── Offering Adapter ─────────────────────────────────────────────────────────

class OfferingAdapter(
    private val onEditClick: (Offering) -> Unit,
    private val onDeleteClick: (Offering) -> Unit
) : ListAdapter<Offering, OfferingAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemOfferingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemOfferingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val o = getItem(position)
        holder.binding.tvOfferingName.text = o.courseName
        holder.binding.tvOfferingDetails.text = "${o.academicYear} • ${o.term} • Year ${o.classYear} • Sec ${o.section} • Cap ${o.capacity} • ${o.lecturerName}"
        holder.binding.btnEditOffering.visibility = View.VISIBLE
        holder.binding.btnDeleteOffering.visibility = View.VISIBLE
        holder.binding.btnEditOffering.setOnClickListener { onEditClick(o) }
        holder.binding.btnDeleteOffering.setOnClickListener { onDeleteClick(o) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Offering>() {
            override fun areItemsTheSame(old: Offering, new: Offering)    = old.id == new.id
            override fun areContentsTheSame(old: Offering, new: Offering) = old == new
        }
    }
}


// ── Lecturer Adapter ─────────────────────────────────────────────────────────

class LecturerAdapter(
    private val onEdit: (Lecturer) -> Unit,
    private val onDelete: (Lecturer) -> Unit
) : ListAdapter<Lecturer, LecturerAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemLecturerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemLecturerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val l = getItem(position)
        holder.binding.tvName.text = l.fullName
        holder.binding.tvDepartment.text = l.departmentName
        holder.binding.tvUsername.text = "@${l.username}"
        holder.binding.btnDelete.visibility = View.VISIBLE
        holder.binding.btnDelete.setOnClickListener { onDelete(l) }
        holder.binding.root.setOnLongClickListener { onEdit(l); true }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Lecturer>() {
            override fun areItemsTheSame(old: Lecturer, new: Lecturer)    = old.id == new.id
            override fun areContentsTheSame(old: Lecturer, new: Lecturer) = old == new
        }
    }
}
