package com.unischeduler.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
import com.unischeduler.util.PendingDelete
import com.unischeduler.util.ErrorReporter
import com.unischeduler.util.ExcelHelper
import com.unischeduler.util.FileTypeDetector
import com.unischeduler.util.ImportPreviewDialog
import com.unischeduler.util.DropdownController
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow
import com.unischeduler.util.dismissProgressDialog
import com.unischeduler.util.showErrorSnackbar
import com.unischeduler.util.showProgressDialog
import com.unischeduler.util.showSnackbar
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

    // B8 — Free-text search filters. Stored as lowercased strings so the
    // matching loop doesn't re-lowercase on every keystroke. Empty = all.
    private var lecturerQuery: String = ""
    private var courseQuery: String = ""
    private var offeringQuery: String = ""

    private val academicTitles = listOf("Dr.", "Prof.", "Asst. Prof.", "Lecturer", "Mr.", "Ms.")
    private val terms          = listOf("Fall", "Spring", "Summer")
    private val classYears     = listOf(1, 2, 3, 4)

    // Material 3 ExposedDropdownMenu kontrolcüleri — Spinner.adapter / setSelection
    // / selectedItemPosition API'sini DropdownController üzerinden gizliyoruz.
    private var titleDropdown: DropdownController<String>? = null
    private var lecturerDeptDropdown: DropdownController<String>? = null
    private var courseDeptDropdown: DropdownController<String>? = null
    private var offeringCourseDropdown: DropdownController<String>? = null
    private var offeringLecturerDropdown: DropdownController<String>? = null
    private var termDropdown: DropdownController<String>? = null
    private var classYearDropdown: DropdownController<Int>? = null
    private var deptFilterDropdown: DropdownController<String>? = null

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
                .onSuccess { showSnackbar(successMessage) }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    showErrorSnackbar(getString(R.string.data_export_fail, it.message))
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
    private val lecturerAdapter by lazy {
        LecturerAdapter(
            onEdit = { showEditLecturerDialog(it) },
            onDelete = { showDeleteLecturerDialog(it) },
            onMore = { lecturer, anchor -> showLecturerMenu(lecturer, anchor) }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Excel import için coach mark eski sürümde TapTargetView ile vardı;
        // yeni Spotlight Tour (TourCoordinator) Data sekmesini zaten ziyaret
        // ediyor ve BottomSheet ile aynı bilgiyi veriyor → coach mark gereksiz
        // (çift gösterim engellendi).

        setupStaticDropdowns()
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

        // B8 — search wires. Each TextWatcher only re-applies the
        // department filter (which already does the heavy lifting) so we
        // don't need a separate filter pass per query type.
        binding.etSearchLecturers.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                lecturerQuery = s?.toString()?.trim()?.lowercase() ?: ""
                applyDeptFilter()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        binding.etSearchCourses.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                courseQuery = s?.toString()?.trim()?.lowercase() ?: ""
                applyDeptFilter()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        binding.etSearchOfferings.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                offeringQuery = s?.toString()?.trim()?.lowercase() ?: ""
                applyDeptFilter()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
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
                    populateDeptDropdowns(state.data)
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
                    showSnackbar(R.string.data_lecturer_deleted)
                    viewModel.resetLecturerDeleteState()
                }
            }
        }

        collectFlow(viewModel.lecturerImportState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> {
                    binding.btnImportLecturers.isEnabled = false
                    // Edge Function tek atış / fallback 1500ms throttle × N satır;
                    // her durumda 30+ saniye sürebilir. Cancelable=false dialog
                    // ile "uygulama dondu mu?" hissi engellenir.
                    showProgressDialog(R.string.progress_importing_lecturers, R.string.progress_message_wait)
                }
                is UiState.Error   -> {
                    dismissProgressDialog()
                    binding.btnImportLecturers.isEnabled = true
                    showErrorSnackbar(state.message)
                    viewModel.resetLecturerImportState()
                }
                is UiState.Success -> {
                    dismissProgressDialog()
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
                    showSnackbar(R.string.data_course_added)
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
                is UiState.Loading -> {
                    binding.btnImportCourses.isEnabled = false
                    showProgressDialog(R.string.progress_importing_courses, R.string.progress_message_wait)
                }
                is UiState.Error   -> {
                    dismissProgressDialog()
                    binding.btnImportCourses.isEnabled = true
                    showErrorSnackbar(state.message)
                    viewModel.resetCourseImportState()
                }
                is UiState.Success -> {
                    dismissProgressDialog()
                    binding.btnImportCourses.isEnabled = true
                    showSnackbar(getString(R.string.data_course_import_count, state.data))
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
                    showSnackbar(R.string.data_offering_added)
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
                    showErrorSnackbar(state.message)
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
                    showErrorSnackbar(state.message)
                    viewModel.resetLecturerEditState()
                }
                is UiState.Success -> {
                    showSnackbar(R.string.data_lecturer_updated)
                    viewModel.resetLecturerEditState()
                }
            }
        }

        collectFlow(viewModel.offeringEditState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> Unit
                is UiState.Error   -> {
                    showErrorSnackbar(state.message)
                    viewModel.resetOfferingEditState()
                }
                is UiState.Success -> {
                    showSnackbar(R.string.data_offering_updated)
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
            title        = academicTitles[titleDropdown?.selectedPosition() ?: 0],
            firstName    = binding.etFirstName.text?.toString().orEmpty(),
            lastName     = binding.etLastName.text?.toString().orEmpty(),
            departmentId = departments[lecturerDeptDropdown?.selectedPosition() ?: 0].id
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
            departmentId = departments[courseDeptDropdown?.selectedPosition() ?: 0].id,
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
            courseId      = courses[offeringCourseDropdown?.selectedPosition() ?: 0].id,
            lecturerId   = getSelectedLecturerId(),
            academicYear = binding.etAcademicYear.text?.toString().orEmpty(),
            term         = terms[termDropdown?.selectedPosition() ?: 0],
            classYear    = classYears[classYearDropdown?.selectedPosition() ?: 0],
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
        val etCode = EditText(requireContext()).apply { setText(course.code); hint = getString(R.string.data_course_code_hint) }
        val etName = EditText(requireContext()).apply { setText(course.name); hint = getString(R.string.data_course_name_hint) }
        val etTheory = EditText(requireContext()).apply { setText(course.theoryHours.toString()); hint = getString(R.string.data_theory_hours_hint); inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val etLab = EditText(requireContext()).apply { setText(course.labHours.toString()); hint = getString(R.string.data_lab_hours_hint); inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val etCredits = EditText(requireContext()).apply { setText(course.credits.toString()); hint = getString(R.string.data_credits_hint); inputType = android.text.InputType.TYPE_CLASS_NUMBER }

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
            .setPositiveButton(getString(R.string.common_delete)) { _, _ ->
                // Optimistic remove from local list so user sees it gone
                // immediately. Snackbar UNDO restores it before the actual
                // network delete fires.
                allCourses = allCourses.filter { it.id != course.id }
                applyDeptFilter()
                PendingDelete.schedule(
                    anchor = binding.root,
                    owner = viewLifecycleOwner,
                    message = getString(R.string.ux_undo_deleted_course, course.code),
                    onUndo = {
                        allCourses = (allCourses + course).sortedBy { it.code }
                        applyDeptFilter()
                    },
                    performDelete = { viewModel.deleteCourse(course.id) }
                )
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showEditLecturerDialog(lecturer: Lecturer) {
        if (departments.isEmpty()) {
            showErrorSnackbar(R.string.data_no_dept_error)
            return
        }
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val titleTil = buildEdmTextInputLayout(ctx, getString(R.string.data_title_label))
        val titleAcv = titleTil.editText as com.google.android.material.textfield.MaterialAutoCompleteTextView
        val titleCtl = DropdownController(titleAcv, academicTitles)
        titleCtl.setSelection(academicTitles.indexOf(lecturer.title).takeIf { it >= 0 } ?: 0)

        val etFirst = EditText(ctx).apply { setText(lecturer.firstName); hint = getString(R.string.data_first_name_hint) }
        val etLast  = EditText(ctx).apply { setText(lecturer.lastName); hint = getString(R.string.data_last_name_hint) }
        val etEmail = EditText(ctx).apply { setText(lecturer.email ?: ""); hint = getString(R.string.data_email_optional_hint) }

        val deptTil = buildEdmTextInputLayout(ctx, getString(R.string.data_dept_label))
        val deptAcv = deptTil.editText as com.google.android.material.textfield.MaterialAutoCompleteTextView
        val deptCtl = DropdownController(deptAcv, departments.map { it.name })
        deptCtl.setSelection(departments.indexOfFirst { it.id == lecturer.departmentId }.takeIf { it >= 0 } ?: 0)

        layout.addView(titleTil)
        layout.addView(etFirst)
        layout.addView(etLast)
        layout.addView(etEmail)
        layout.addView(deptTil)

        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.data_lecturer_edit_title))
            .setView(ScrollView(ctx).apply { addView(layout) })
            .setPositiveButton(getString(R.string.common_save)) { _, _ ->
                viewModel.editLecturer(
                    id = lecturer.id,
                    title = academicTitles[titleCtl.selectedPosition()],
                    firstName = etFirst.text.toString(),
                    lastName = etLast.text.toString(),
                    departmentId = departments[deptCtl.selectedPosition()].id,
                    email = etEmail.text.toString().takeIf { it.isNotBlank() }
                )
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showEditOfferingDialog(offering: Offering) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val etYear    = EditText(ctx).apply { setText(offering.academicYear); hint = getString(R.string.data_offering_academic_year_hint) }
        val etSection = EditText(ctx).apply { setText(offering.section); hint = getString(R.string.data_offering_section_hint) }
        val etCap     = EditText(ctx).apply {
            setText(offering.capacity.toString())
            hint = getString(R.string.data_offering_quota_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        val termTil = buildEdmTextInputLayout(ctx, getString(R.string.data_offering_term_label))
        val termAcv = termTil.editText as com.google.android.material.textfield.MaterialAutoCompleteTextView
        val termCtl = DropdownController(termAcv, terms)
        termCtl.setSelection(terms.indexOf(offering.term).takeIf { it >= 0 } ?: 0)

        val yearTil = buildEdmTextInputLayout(ctx, getString(R.string.data_offering_class_year_label))
        val yearAcv = yearTil.editText as com.google.android.material.textfield.MaterialAutoCompleteTextView
        val yearCtl = DropdownController(yearAcv, classYears) { "Year $it" }
        yearCtl.setSelection(classYears.indexOf(offering.classYear).takeIf { it >= 0 } ?: 0)

        val lecturerNames = listOf(getString(R.string.common_not_assigned)) + lecturers.map { it.fullName }
        val lecturerTil = buildEdmTextInputLayout(ctx, getString(R.string.data_offering_lecturer_optional))
        val lecturerAcv = lecturerTil.editText as com.google.android.material.textfield.MaterialAutoCompleteTextView
        val lecturerCtl = DropdownController(lecturerAcv, lecturerNames)
        val lecturerIdx = if (offering.lecturerId != null) {
            lecturers.indexOfFirst { it.id == offering.lecturerId }.takeIf { it >= 0 }?.plus(1) ?: 0
        } else 0
        lecturerCtl.setSelection(lecturerIdx)

        layout.addView(etYear)
        layout.addView(termTil)
        layout.addView(yearTil)
        layout.addView(etSection)
        layout.addView(etCap)
        layout.addView(lecturerTil)

        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.data_offering_edit_title))
            .setView(ScrollView(ctx).apply { addView(layout) })
            .setPositiveButton(getString(R.string.common_save)) { _, _ ->
                val pos = lecturerCtl.selectedPosition()
                val selectedLecturerId = if (pos > 0) lecturers.getOrNull(pos - 1)?.id else null
                viewModel.editOffering(
                    id = offering.id,
                    lecturerId = selectedLecturerId,
                    academicYear = etYear.text.toString(),
                    term = terms[termCtl.selectedPosition()],
                    classYear = classYears[yearCtl.selectedPosition()],
                    section = etSection.text.toString(),
                    capacity = etCap.text.toString().toIntOrNull() ?: 0
                )
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    /**
     * Programatik bir TextInputLayout (ExposedDropdownMenu stiliyle) + içinde
     * boş bir MaterialAutoCompleteTextView üretir. DropdownController sonradan
     * setItems ile doldurur. Dialog'larda Spinner yerine Material 3 standardı
     * için kullanılır — form ekranlarındaki dropdown'larla aynı görünüm/davranış.
     */
    private fun buildEdmTextInputLayout(
        ctx: android.content.Context,
        hint: String
    ): com.google.android.material.textfield.TextInputLayout {
        val til = com.google.android.material.textfield.TextInputLayout(
            ctx,
            null,
            com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle
        ).apply {
            this.hint = hint
            layoutParams = LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }
        val acv = com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
            inputType = android.text.InputType.TYPE_NULL
        }
        til.addView(acv)
        return til
    }

    private fun showDeleteOfferingDialog(offering: Offering) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.data_offering_delete_title))
            .setMessage(getString(R.string.data_offering_delete_message, offering.courseName, offering.section))
            .setPositiveButton(getString(R.string.common_delete)) { _, _ ->
                allOfferings = allOfferings.filter { it.id != offering.id }
                applyDeptFilter()
                PendingDelete.schedule(
                    anchor = binding.root,
                    owner = viewLifecycleOwner,
                    message = getString(R.string.ux_undo_deleted_offering, offering.courseName),
                    onUndo = {
                        allOfferings = (allOfferings + offering)
                        applyDeptFilter()
                    },
                    performDelete = { viewModel.deleteOffering(offering.id) }
                )
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    // ── Import preview dialogs ────────────────────────────────────────────────

    private fun showCourseImportPreview(result: CsvImporter.ParseResult<CsvImporter.CourseRow>) {
        if (departments.isEmpty()) {
            showErrorSnackbar(R.string.import_load_depts_first)
            return
        }
        val deptPos  = courseDeptDropdown?.selectedPosition() ?: 0
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
            targetDescription = getString(R.string.label_department_value, deptName),
            rows = result.valid,
            errors = result.errors,
            titleProvider = { "${it.code} — ${it.name}" },
            subtitleProvider = { "Teori: ${it.theoryHours}  Lab: ${it.labHours}  Kredi: ${it.credits}" },
            onImport = { chosen -> viewModel.importCourses(chosen, deptId) }
        )
    }

    private fun showLecturerImportPreview(result: CsvImporter.ParseResult<CsvImporter.LecturerRow>) {
        if (departments.isEmpty()) {
            showErrorSnackbar(R.string.import_load_depts_first)
            return
        }
        val deptPos  = lecturerDeptDropdown?.selectedPosition() ?: 0
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
        sb.appendLine(getString(R.string.import_result_imported, result.imported))
        // Atlanan (idempotent skip) sayacı — aynı org'da aynı isim varsa
        // güvenlik için yeniden yaratmıyoruz. Kullanıcının bilmesi lazım.
        if (result.skipped.isNotEmpty()) {
            sb.appendLine(getString(R.string.import_result_skipped, result.skipped.size))
        }
        if (result.errors.isNotEmpty()) {
            sb.appendLine(getString(R.string.import_result_failed, result.errors.size))
        }
        sb.appendLine()
        if (result.credentials.isNotEmpty()) {
            sb.appendLine(getString(R.string.import_result_credentials_header))
            sb.appendLine(credentialsBlock)
        }
        if (result.skipped.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(getString(R.string.import_result_skipped_header))
            result.skipped.take(20).forEach { sb.appendLine("• $it") }
            if (result.skipped.size > 20) {
                sb.appendLine(getString(R.string.import_result_more_rows, result.skipped.size - 20))
            }
        }
        if (result.errors.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(getString(R.string.import_result_errors_header))
            result.errors.take(20).forEach { sb.appendLine("• $it") }
            if (result.errors.size > 20) {
                sb.appendLine(getString(R.string.import_result_more_rows, result.errors.size - 20))
            }
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
                showSnackbar(R.string.data_all_users_copied)
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
                showSnackbar(R.string.common_copied_clipboard)
            }
            .show()
    }

    /**
     * Hoca kartında "..." veya uzun bas → küçük menü.
     * Web panelde olan işlemleri mobilde tek bir yere topluyoruz —
     * admin liste görünümünden çıkmadan en kritik 3 aksiyonu
     * (programını gör, şifreyi sıfırla, sil) yapabilsin diye.
     */
    private fun showLecturerMenu(lecturer: Lecturer, anchor: View) {
        val popup = android.widget.PopupMenu(requireContext(), anchor)
        val m = popup.menu
        m.add(0, 1, 1, getString(R.string.lecturer_action_view_schedule))
        m.add(0, 2, 2, getString(R.string.lecturer_action_edit))
        m.add(0, 3, 3, getString(R.string.lecturer_action_reset_password))
        m.add(0, 4, 4, getString(R.string.lecturer_action_delete))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showLecturerScheduleSheet(lecturer)
                2 -> showEditLecturerDialog(lecturer)
                3 -> confirmResetLecturerPassword(lecturer)
                4 -> showDeleteLecturerDialog(lecturer)
            }
            true
        }
        popup.show()
    }

    private fun showLecturerScheduleSheet(lecturer: Lecturer) {
        LecturerScheduleSheet.newInstance(lecturer.id, lecturer.fullName)
            .show(parentFragmentManager, "lecturerScheduleSheet")
    }

    private fun confirmResetLecturerPassword(lecturer: Lecturer) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.lecturer_action_reset_password))
            .setMessage(getString(R.string.lecturer_reset_password_confirm, lecturer.fullName))
            .setPositiveButton(getString(R.string.common_ok)) { _, _ ->
                viewModel.resetLecturerPassword(lecturer) { newPassword ->
                    showResetPasswordResult(lecturer, newPassword)
                }
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showResetPasswordResult(lecturer: Lecturer, password: String) {
        val msg = getString(R.string.lecturer_reset_password_done_msg, lecturer.username, password)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.lecturer_reset_password_done_title)
            .setMessage(msg)
            .setPositiveButton(R.string.lecturer_reset_password_copy) { _, _ ->
                val clip = requireContext()
                    .getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                clip.setPrimaryClip(android.content.ClipData.newPlainText("password", password))
                showSnackbar(R.string.lecturer_reset_password_copied)
            }
            .setNegativeButton(R.string.common_ok, null)
            .show()
    }

    private fun showDeleteLecturerDialog(lecturer: Lecturer) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.data_lecturer_delete_title))
            .setMessage(getString(R.string.data_lecturer_delete_message, lecturer.fullName))
            .setPositiveButton(getString(R.string.common_delete)) { _, _ ->
                allLecturers = allLecturers.filter { it.id != lecturer.id }
                applyDeptFilter()
                PendingDelete.schedule(
                    anchor = binding.root,
                    owner = viewLifecycleOwner,
                    message = getString(R.string.ux_undo_deleted_lecturer, lecturer.fullName),
                    onUndo = {
                        allLecturers = (allLecturers + lecturer).sortedBy { it.lastName }
                        applyDeptFilter()
                    },
                    performDelete = { viewModel.deleteLecturer(lecturer) }
                )
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showFieldError(tv: TextView, msg: String) {
        tv.text       = msg
        tv.visibility = View.VISIBLE
    }

    private fun setupAccordion() {
        // Toggling visibility on a wrap_content RecyclerView inside a
        // ScrollView is a known measure-pass trap: the RV often lays
        // out at 0px height the first time it becomes VISIBLE, only
        // re-measuring on the next focus change (which is why typing
        // into the search field below it makes the list "appear").
        // We force the re-measure ourselves and then ask the parent
        // ScrollView to bring the just-opened section into view.
        fun toggle(header: View, content: View, arrow: View) {
            if (content.visibility == View.GONE) {
                content.visibility = View.VISIBLE
                arrow.animate().rotation(180f).setDuration(180L).start()
                arrow.contentDescription = getString(R.string.cd_collapse_section)
                // Wait one frame so `content`'s width is non-zero, then:
                content.post {
                    // 1) Forced layout pass — the RecyclerViews inside
                    //    `content` measure themselves correctly.
                    content.requestLayout()
                    // 2) Scroll the section header to the top of the
                    //    visible area so the user sees what just opened.
                    val r = android.graphics.Rect(
                        0, 0, header.width, header.height + content.height
                    )
                    header.requestRectangleOnScreen(r, /* immediate = */ false)
                }
            } else {
                content.visibility = View.GONE
                arrow.animate().rotation(0f).setDuration(180L).start()
                arrow.contentDescription = getString(R.string.cd_expand_section)
            }
        }

        binding.headerLecturers.setOnClickListener {
            toggle(binding.headerLecturers, binding.contentLecturers, binding.ivExpandLecturers)
        }
        binding.headerCourses.setOnClickListener {
            toggle(binding.headerCourses, binding.contentCourses, binding.ivExpandCourses)
        }
        binding.headerOfferings.setOnClickListener {
            toggle(binding.headerOfferings, binding.contentOfferings, binding.ivExpandOfferings)
        }
    }

    private fun setupDefaults() {
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
        val academicYear = if (month >= 8) "$year-${year + 1}" else "${year - 1}-$year"
        binding.etAcademicYear.setText(academicYear)

        val termIdx = if (month in 1..6) 1 else 0
        termDropdown?.setSelection(termIdx)

        binding.etSection.setText("A")
    }

    private fun setupStaticDropdowns() {
        titleDropdown = DropdownController(binding.actvTitle, academicTitles)
        termDropdown = DropdownController(binding.actvTerm, terms)
        classYearDropdown = DropdownController(binding.actvClassYear, classYears) { "Year $it" }
    }

    private fun populateDeptDropdowns(depts: List<Department>) {
        val names = depts.map { it.name }

        // Lecturer / Course form dropdown'ları
        lecturerDeptDropdown?.let { it.setItems(names) } ?: run {
            lecturerDeptDropdown = DropdownController(binding.actvLecturerDept, names)
        }
        courseDeptDropdown?.let { it.setItems(names) } ?: run {
            courseDeptDropdown = DropdownController(binding.actvCourseDept, names)
        }

        // Üst filtre dropdown'ı — "Tüm Bölümler" prefix'i ile + selection callback
        val filterNames = listOf(getString(R.string.common_all_departments)) + names
        deptFilterDropdown?.let { it.setItems(filterNames) } ?: run {
            deptFilterDropdown = DropdownController(binding.actvDeptFilter, filterNames).apply {
                onSelected { _, pos ->
                    selectedDeptId = if (pos == 0) null else depts[pos - 1].id
                    applyDeptFilter()
                    if (pos > 0) {
                        lecturerDeptDropdown?.setSelection(pos - 1)
                        courseDeptDropdown?.setSelection(pos - 1)
                    }
                }
            }
        }
    }

    private fun applyDeptFilter() {
        val deptId = selectedDeptId

        // ── Lecturers: dept filter → search filter ──
        val deptScopedLecturers = if (deptId != null) allLecturers.filter { it.departmentId == deptId } else allLecturers
        val filteredLecturers = if (lecturerQuery.isBlank()) deptScopedLecturers else deptScopedLecturers.filter {
            it.firstName.lowercase().contains(lecturerQuery) ||
            it.lastName.lowercase().contains(lecturerQuery) ||
            it.fullName.lowercase().contains(lecturerQuery) ||
            it.username.lowercase().contains(lecturerQuery)
        }
        // Spinner'lar filtreden bağımsız olarak dept-scoped tüm hocaları
        // göstermeli — kullanıcı yeni atama yaparken arama filtresi
        // dropdown'ı kısıtlamamalı.
        lecturers = deptScopedLecturers
        populateLecturerDropdown()
        binding.tvLecturerHeader.text = getString(R.string.data_lecturers_header_count, filteredLecturers.size)
        populateLecturerList(filteredLecturers)

        // ── Courses ──
        val deptScopedCourses = if (deptId != null) allCourses.filter { it.departmentId == deptId } else allCourses
        val filteredCourses = if (courseQuery.isBlank()) deptScopedCourses else deptScopedCourses.filter {
            it.code.lowercase().contains(courseQuery) ||
            it.name.lowercase().contains(courseQuery)
        }
        courses = deptScopedCourses
        populateCourseDropdown(deptScopedCourses)
        binding.tvCourseHeader.text = getString(R.string.data_courses_header_count, filteredCourses.size)
        courseAdapter.submitList(filteredCourses)
        binding.tvCoursesEmpty.visibility = if (filteredCourses.isEmpty()) View.VISIBLE else View.GONE
        binding.rvCourses.visibility      = if (filteredCourses.isEmpty()) View.GONE else View.VISIBLE

        // ── Offerings ──
        val deptScopedOfferings = if (deptId != null) {
            allOfferings.filter { it.courses?.departmentId == deptId }
        } else allOfferings
        val filteredOfferings = if (offeringQuery.isBlank()) deptScopedOfferings else deptScopedOfferings.filter {
            (it.courses?.code?.lowercase()?.contains(offeringQuery) == true) ||
            (it.courses?.name?.lowercase()?.contains(offeringQuery) == true) ||
            (it.lecturers?.fullName?.lowercase()?.contains(offeringQuery) == true) ||
            it.section.lowercase().contains(offeringQuery) ||
            it.term.lowercase().contains(offeringQuery)
        }
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

    private fun populateCourseDropdown(courseList: List<Course>) {
        val items = courseList.map { it.displayName }
        offeringCourseDropdown?.let { it.setItems(items) } ?: run {
            offeringCourseDropdown = DropdownController(binding.actvOfferingCourse, items)
        }
    }

    private fun populateLecturerDropdown() {
        val items = listOf(getString(R.string.common_not_assigned)) + lecturers.map { it.fullName }
        offeringLecturerDropdown?.let { it.setItems(items) } ?: run {
            offeringLecturerDropdown = DropdownController(binding.actvOfferingLecturer, items)
        }
    }

    private fun getSelectedLecturerId(): Int? {
        val pos = offeringLecturerDropdown?.selectedPosition() ?: 0
        return if (pos > 0) lecturers[pos - 1].id else null
    }

    private fun clearLecturerForm() {
        binding.etFirstName.text?.clear()
        binding.etLastName.text?.clear()
        titleDropdown?.setSelection(0)
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
        offeringLecturerDropdown?.setSelection(0)
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

    override fun onDestroyView() {
        // Bulk import sırasında fragment destroy olursa progress dialog
        // window leak yapardı (Activity üzerinde asılı kalır). View'a tag
        // ile bağlı olduğu için dismiss önce çağrılmalı, ardından
        // _binding null'lanır.
        dismissProgressDialog()
        super.onDestroyView()
        _binding = null
    }
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
        holder.binding.tvCourseDetails.text = holder.binding.root.context.getString(
            R.string.data_course_details_format,
            c.theoryHours, c.labHours, c.credits, c.departmentName
        )
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
    private val onDelete: (Lecturer) -> Unit,
    private val onMore: (Lecturer, View) -> Unit = { _, _ -> }
) : ListAdapter<Lecturer, LecturerAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemLecturerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemLecturerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val l = getItem(position)
        val ctx = holder.binding.root.context
        holder.binding.tvName.text = l.fullName
        holder.binding.tvDepartment.text = l.departmentName.ifBlank { "—" }
        holder.binding.tvUsername.text = "@${l.username}"

        val isTemp = l.mustChangePassword
        holder.binding.tvStatus.text = if (isTemp)
            ctx.getString(R.string.lecturer_status_temp)
        else
            ctx.getString(R.string.lecturer_status_active)
        holder.binding.tvStatus.setBackgroundResource(
            if (isTemp) R.drawable.bg_status_chip else R.drawable.bg_status_chip_active
        )
        holder.binding.tvStatus.setTextColor(
            android.graphics.Color.parseColor(if (isTemp) "#E65100" else "#2E7D32")
        )

        holder.binding.btnDelete.visibility = View.VISIBLE
        holder.binding.btnDelete.setOnClickListener { onDelete(l) }
        holder.binding.btnMore.setOnClickListener { v -> onMore(l, v) }
        holder.binding.root.setOnLongClickListener { v -> onMore(l, v); true }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Lecturer>() {
            override fun areItemsTheSame(old: Lecturer, new: Lecturer)    = old.id == new.id
            override fun areContentsTheSame(old: Lecturer, new: Lecturer) = old == new
        }
    }
}
