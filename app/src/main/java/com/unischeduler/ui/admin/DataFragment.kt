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
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.unischeduler.util.CsvExporter
import com.unischeduler.util.CsvImporter
import com.unischeduler.util.ExcelHelper
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow

class DataFragment : Fragment() {

    private var _binding: FragmentDataBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DataViewModel by viewModels()

    private var departments: List<Department> = emptyList()
    private var courses: List<Course>         = emptyList()
    private var lecturers: List<Lecturer>     = emptyList()

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
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return@registerForActivityResult
            val fileName = uri.lastPathSegment ?: ""

            if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                val result = ExcelHelper.importCourses(inputStream)
                inputStream.close()
                showCourseImportPreview(CsvImporter.ParseResult(result.valid, result.errors))
            } else {
                val text = inputStream.bufferedReader().readText()
                inputStream.close()
                val result = CsvImporter.parseCourses(text)
                showCourseImportPreview(result)
            }
        }

        lecturerImportLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return@registerForActivityResult
            val fileName = uri.lastPathSegment ?: ""

            if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                val result = ExcelHelper.importLecturers(inputStream)
                inputStream.close()
                showLecturerImportPreview(CsvImporter.ParseResult(result.valid, result.errors))
            } else {
                val text = inputStream.bufferedReader().readText()
                inputStream.close()
                val result = CsvImporter.parseLecturers(text)
                showLecturerImportPreview(result)
            }
        }

        courseExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
            uri ?: return@registerForActivityResult
            try {
                val outputStream = requireContext().contentResolver.openOutputStream(uri) ?: return@registerForActivityResult
                ExcelHelper.exportCourses(courses, outputStream)
                outputStream.close()
                Toast.makeText(requireContext(), "Courses exported successfully.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        lecturerExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
            uri ?: return@registerForActivityResult
            try {
                val outputStream = requireContext().contentResolver.openOutputStream(uri) ?: return@registerForActivityResult
                ExcelHelper.exportLecturers(lecturers, outputStream)
                outputStream.close()
                Toast.makeText(requireContext(), "Lecturers exported successfully.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupStaticSpinners()
        binding.rvLecturers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCourses.layoutManager   = LinearLayoutManager(requireContext())

        // Button listeners
        binding.btnRetryLecturers.setOnClickListener    { viewModel.loadLecturers() }
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
                }
                is UiState.Success -> {
                    binding.progressLecturers.visibility   = View.GONE
                    binding.tvLecturerListError.visibility = View.GONE
                    binding.btnRetryLecturers.visibility   = View.GONE
                    lecturers = state.data
                    binding.rvLecturers.adapter = LecturerAdapter(
                        state.data,
                        departments,
                        onEditClick = { showEditLecturerDialog(it) },
                        onDeleteClick = { showDeleteLecturerDialog(it) }
                    )
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
                    Toast.makeText(requireContext(), "Lecturer deleted.", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(requireContext(), "Course saved.", Toast.LENGTH_SHORT).show()
                    viewModel.resetCourseAddState()
                    viewModel.loadCourses()
                }
            }
        }

        collectFlow(viewModel.courseListState) { state ->
            when (state) {
                is UiState.Idle    -> viewModel.loadCourses()
                is UiState.Success -> {
                    courses = state.data
                    populateCourseSpinner(state.data)
                    binding.rvCourses.adapter = CourseAdapter(state.data,
                        onEdit   = { showEditCourseDialog(it) },
                        onDelete = { showDeleteCourseDialog(it) }
                    )
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
                    Toast.makeText(requireContext(), "${state.data} course(s) imported successfully.", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(requireContext(), "Offering added.", Toast.LENGTH_SHORT).show()
                    viewModel.resetOfferingAddState()
                }
            }
        }

        // ── Settings navigation ────────────────────────────────────────────────
        binding.btnGoSettings.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }

        // ── Offering list ──────────────────────────────────────────────────────
        binding.rvOfferings.layoutManager = LinearLayoutManager(requireContext())

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
                    binding.rvOfferings.adapter = OfferingAdapter(
                        state.data,
                        onEditClick = { showEditOfferingDialog(it) },
                        onDeleteClick = { showDeleteOfferingDialog(it) }
                    )
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
                    Toast.makeText(requireContext(), "Lecturer updated.", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(requireContext(), "Offering updated.", Toast.LENGTH_SHORT).show()
                    viewModel.resetOfferingEditState()
                }
            }
        }
    }

    // ── Form actions ──────────────────────────────────────────────────────────

    private fun onAddLecturerClicked() {
        if (departments.isEmpty()) {
            showFieldError(binding.tvLecturerAddError, "No departments yet. Add one in Settings.")
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
            showFieldError(binding.tvCourseAddError, "No departments yet. Add one in Settings.")
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
            showFieldError(binding.tvOfferingError, "No courses yet. Add a course first.")
            return
        }
        viewModel.addOffering(
            courseId      = courses[binding.spinnerOfferingCourse.selectedItemPosition].id,
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
            .setTitle("Edit Course")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                viewModel.updateCourse(
                    id = course.id,
                    code = etCode.text.toString(),
                    name = etName.text.toString(),
                    theoryHours = etTheory.text.toString().toIntOrNull() ?: 0,
                    labHours = etLab.text.toString().toIntOrNull() ?: 0,
                    credits = etCredits.text.toString().toIntOrNull() ?: 0
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteCourseDialog(course: Course) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Course")
            .setMessage("Delete ${course.code} — ${course.name}?\nThis will also remove related offerings.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteCourse(course.id) }
            .setNegativeButton("Cancel", null)
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
            .setTitle("Edit Lecturer")
            .setView(ScrollView(requireContext()).apply { addView(layout) })
            .setPositiveButton("Save") { _, _ ->
                viewModel.editLecturer(
                    id = lecturer.id,
                    title = academicTitles[spinnerTitle.selectedItemPosition],
                    firstName = etFirst.text.toString(),
                    lastName = etLast.text.toString(),
                    departmentId = departments[spinnerDept.selectedItemPosition].id,
                    email = etEmail.text.toString().takeIf { it.isNotBlank() }
                )
            }
            .setNegativeButton("Cancel", null)
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

        layout.addView(etYear)
        layout.addView(TextView(requireContext()).apply { text = "Term" })
        layout.addView(spinnerTerm)
        layout.addView(TextView(requireContext()).apply { text = "Class Year" })
        layout.addView(spinnerYear)
        layout.addView(etSection)
        layout.addView(etCap)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Offering")
            .setView(ScrollView(requireContext()).apply { addView(layout) })
            .setPositiveButton("Save") { _, _ ->
                viewModel.editOffering(
                    id = offering.id,
                    academicYear = etYear.text.toString(),
                    term = terms[spinnerTerm.selectedItemPosition],
                    classYear = classYears[spinnerYear.selectedItemPosition],
                    section = etSection.text.toString(),
                    capacity = etCap.text.toString().toIntOrNull() ?: 0
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteOfferingDialog(offering: Offering) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Offering")
            .setMessage("Delete ${offering.courseName} ${offering.section}? Related schedule entries will also be removed.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteOffering(offering.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Import preview dialogs ────────────────────────────────────────────────

    private fun showCourseImportPreview(result: CsvImporter.ParseResult<CsvImporter.CourseRow>) {
        if (departments.isEmpty()) {
            Toast.makeText(requireContext(), "Load departments first before importing.", Toast.LENGTH_LONG).show()
            return
        }
        val deptPos  = binding.spinnerCourseDept.selectedItemPosition
        val deptName = departments.getOrNull(deptPos)?.name ?: "Unknown"
        val deptId   = departments.getOrNull(deptPos)?.id ?: return

        val msg = buildString {
            append("Found ${result.valid.size} valid row(s).")
            if (result.errors.isNotEmpty()) {
                append("\n\nSkipped ${result.errors.size} row(s):")
                result.errors.take(5).forEach { append("\n• $it") }
                if (result.errors.size > 5) append("\n…and ${result.errors.size - 5} more")
            }
            append("\n\nDepartment: $deptName")
            append("\nImport now?")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Import Courses")
            .setMessage(msg)
            .setPositiveButton("Import") { _, _ ->
                if (result.valid.isNotEmpty()) viewModel.importCourses(result.valid, deptId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLecturerImportPreview(result: CsvImporter.ParseResult<CsvImporter.LecturerRow>) {
        if (departments.isEmpty()) {
            Toast.makeText(requireContext(), "Load departments first before importing.", Toast.LENGTH_LONG).show()
            return
        }
        val deptPos  = binding.spinnerLecturerDept.selectedItemPosition
        val deptName = departments.getOrNull(deptPos)?.name ?: "Unknown"
        val deptId   = departments.getOrNull(deptPos)?.id ?: return

        val msg = buildString {
            append("Found ${result.valid.size} valid row(s).")
            if (result.errors.isNotEmpty()) {
                append("\n\nSkipped ${result.errors.size} row(s):")
                result.errors.take(5).forEach { append("\n• $it") }
            }
            append("\n\nDepartment: $deptName")
            append("\nCredentials will be generated automatically.")
            append("\nImport now?")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Import Lecturers")
            .setMessage(msg)
            .setPositiveButton("Import") { _, _ ->
                if (result.valid.isNotEmpty()) viewModel.importLecturers(result.valid, deptId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLecturerImportResult(result: LecturerImportResult) {
        val sb = StringBuilder()
        sb.appendLine("Imported: ${result.imported}")
        if (result.errors.isNotEmpty()) sb.appendLine("Failed: ${result.errors.size}")
        sb.appendLine()
        sb.appendLine("Generated credentials:")
        result.credentials.forEach { (u, p) -> sb.appendLine("$u  /  $p") }
        if (result.errors.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Errors:")
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
            .setTitle("Import Complete")
            .setView(scroll)
            .setPositiveButton("OK", null)
            .show()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun showCredentialsDialog(username: String, password: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Lecturer Added")
            .setMessage(
                "Share these credentials with the lecturer:\n\n" +
                "Username: $username\n" +
                "Password: $password\n\n" +
                "They must change their password on first login."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showDeleteLecturerDialog(lecturer: Lecturer) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Lecturer")
            .setMessage("Delete ${lecturer.fullName}? This also removes their login account.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteLecturer(lecturer) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFieldError(tv: TextView, msg: String) {
        tv.text       = msg
        tv.visibility = View.VISIBLE
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
    }

    private fun populateCourseSpinner(courseList: List<Course>) {
        binding.spinnerOfferingCourse.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, courseList.map { it.displayName }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
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
        binding.etAcademicYear.text?.clear()
        binding.etSection.text?.clear()
        binding.etOfferingCapacity.text?.clear()
        binding.spinnerTerm.setSelection(0)
        binding.spinnerClassYear.setSelection(0)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Course Adapter ─────────���─────────────────────────────────────────────────

class CourseAdapter(
    private val items: List<Course>,
    private val onEdit: (Course) -> Unit,
    private val onDelete: (Course) -> Unit
) : RecyclerView.Adapter<CourseAdapter.VH>() {

    inner class VH(val binding: ItemCourseBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCourseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = maxOf(items.size, 1)

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (items.isEmpty()) {
            holder.binding.tvCourseCode.text    = "No courses yet."
            holder.binding.tvCourseName.text    = ""
            holder.binding.tvCourseDetails.text = ""
            holder.binding.btnEditCourse.visibility   = View.GONE
            holder.binding.btnDeleteCourse.visibility = View.GONE
            return
        }
        val c = items[position]
        holder.binding.tvCourseCode.text    = c.code
        holder.binding.tvCourseName.text    = c.name
        holder.binding.tvCourseDetails.text = "T:${c.theoryHours} L:${c.labHours} C:${c.credits} • ${c.departmentName}"
        holder.binding.btnEditCourse.visibility   = View.VISIBLE
        holder.binding.btnDeleteCourse.visibility = View.VISIBLE
        holder.binding.btnEditCourse.setOnClickListener   { onEdit(c) }
        holder.binding.btnDeleteCourse.setOnClickListener { onDelete(c) }
    }
}

// ── Lecturer Adapter ─────────────────────────────────────────────────────────

class LecturerAdapter(
    private val items: List<Lecturer>,
    private val departments: List<Department>,
    private val onEditClick: (Lecturer) -> Unit,
    private val onDeleteClick: (Lecturer) -> Unit
) : RecyclerView.Adapter<LecturerAdapter.VH>() {

    inner class VH(val binding: ItemLecturerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemLecturerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = maxOf(items.size, 1)

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (items.isEmpty()) {
            holder.binding.tvName.text       = "No lecturers yet."
            holder.binding.tvDepartment.text = ""
            holder.binding.tvUsername.text   = ""
            holder.binding.btnDelete.visibility = View.GONE
            return
        }
        val lecturer = items[position]
        holder.binding.tvName.text          = lecturer.fullName
        holder.binding.tvDepartment.text    = lecturer.departmentName
        holder.binding.tvUsername.text      = "@${lecturer.username}"
        holder.binding.btnDelete.visibility = View.VISIBLE
        holder.binding.btnDelete.setOnClickListener { onDeleteClick(lecturer) }
        // Long-press to edit
        holder.itemView.setOnLongClickListener { onEditClick(lecturer); true }
    }
}

// ── Offering Adapter ─────────────────────────────────────────────────────────

class OfferingAdapter(
    private val items: List<Offering>,
    private val onEditClick: (Offering) -> Unit,
    private val onDeleteClick: (Offering) -> Unit
) : RecyclerView.Adapter<OfferingAdapter.VH>() {

    inner class VH(val binding: ItemOfferingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemOfferingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = maxOf(items.size, 1)

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (items.isEmpty()) {
            holder.binding.tvOfferingName.text = "No offerings yet."
            holder.binding.tvOfferingDetails.text = ""
            holder.binding.btnEditOffering.visibility = View.GONE
            holder.binding.btnDeleteOffering.visibility = View.GONE
            return
        }
        val o = items[position]
        holder.binding.tvOfferingName.text = o.courseName
        holder.binding.tvOfferingDetails.text = "${o.academicYear} • ${o.term} • Year ${o.classYear} • Sec ${o.section} • Cap ${o.capacity}"
        holder.binding.btnEditOffering.visibility = View.VISIBLE
        holder.binding.btnDeleteOffering.visibility = View.VISIBLE
        holder.binding.btnEditOffering.setOnClickListener { onEditClick(o) }
        holder.binding.btnDeleteOffering.setOnClickListener { onDeleteClick(o) }
    }
}
