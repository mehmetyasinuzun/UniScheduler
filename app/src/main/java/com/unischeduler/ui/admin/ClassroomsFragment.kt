package com.unischeduler.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
import com.unischeduler.data.model.Classroom
import com.unischeduler.data.model.Department
import com.unischeduler.databinding.FragmentClassroomsBinding
import com.unischeduler.databinding.ItemClassroomBinding
import com.unischeduler.util.CsvImporter
import com.unischeduler.util.ErrorReporter
import com.unischeduler.util.ExcelHelper
import com.unischeduler.util.FileTypeDetector
import com.unischeduler.util.ImportPreviewDialog
import com.unischeduler.util.DropdownController
import com.unischeduler.util.PendingDelete
import com.unischeduler.util.UiState
import com.unischeduler.util.showErrorSnackbar
import com.unischeduler.util.showSnackbar
import com.unischeduler.util.collectFlow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClassroomsFragment : Fragment() {

    private var _binding: FragmentClassroomsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ClassroomsViewModel by viewModels()

    private var departments: List<Department> = emptyList()
    private var classrooms: List<Classroom>   = emptyList()
    private val classroomTypes = listOf("theory", "lab")

    private var isFirstResume = true
    private var classroomQuery: String = ""

    private lateinit var classroomAdapter: ClassroomAdapter
    private var typeDropdown: DropdownController<String>? = null
    private var deptDropdown: DropdownController<String>? = null

    private lateinit var classroomImportLauncher: ActivityResultLauncher<String>
    private lateinit var classroomExportLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        classroomImportLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            handleClassroomImport(uri)
        }

        classroomExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
            uri ?: return@registerForActivityResult
            val appCtx = requireContext().applicationContext
            val items = classrooms
            lifecycleScope.launch {
                val result = runCatching {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        val out = appCtx.contentResolver.openOutputStream(uri)
                            ?: error("Dosya açılamadı")
                        out.use { ExcelHelper.exportClassrooms(items, it) }
                    }
                }
                if (!isAdded) return@launch
                result
                    .onSuccess { showSnackbar(R.string.classroom_export_success) }
                    .onFailure {
                        if (it is kotlinx.coroutines.CancellationException) throw it
                        showErrorSnackbar(getString(R.string.classroom_export_fail, it.message))
                    }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentClassroomsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvClassrooms.layoutManager = LinearLayoutManager(requireContext())
        // RV is wrap_content inside the page-level NestedScrollView, so a
        // fixed-size hint is incorrect. Bigger view cache instead, since
        // the RV won't recycle as aggressively.
        binding.rvClassrooms.setItemViewCacheSize(20)
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadClassrooms() }

        // B8 — search
        binding.etSearchClassrooms.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                classroomQuery = s?.toString()?.trim()?.lowercase() ?: ""
                refreshClassroomList()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        classroomAdapter = ClassroomAdapter(
            onEditClick   = { showEditDialog(it) },
            onDeleteClick = { showDeleteDialog(it) }
        )
        binding.rvClassrooms.adapter = classroomAdapter

        // Type dropdown (theory/lab) — Material 3 ExposedDropdownMenu
        typeDropdown = DropdownController(
            binding.actvType,
            classroomTypes.map { it.replaceFirstChar { c -> c.uppercase() } }
        )

        binding.btnRetry.setOnClickListener            { viewModel.loadClassrooms() }
        binding.btnGoAssignment.setOnClickListener     { findNavController().navigate(R.id.action_classrooms_to_assignment) }
        binding.btnAddClassroom.setOnClickListener     { onAddClicked() }
        binding.btnImportClassrooms.setOnClickListener { classroomImportLauncher.launch("*/*") }
        binding.btnExportClassrooms.setOnClickListener { classroomExportLauncher.launch("classrooms.xlsx") }

        collectFlow(viewModel.departments) { depts ->
            departments = depts
            val names = listOf(getString(R.string.common_none_option)) + depts.map { it.name }
            val previous = deptDropdown
            if (previous == null) {
                deptDropdown = DropdownController(binding.actvDept, names)
            } else {
                previous.setItems(names)
            }
        }

        collectFlow(viewModel.state) { state ->
            when (state) {
                is UiState.Idle    -> viewModel.loadClassrooms()
                is UiState.Loading -> showLoading(true)
                is UiState.Error   -> {
                    binding.swipeRefresh.isRefreshing = false
                    showError(state.message, state.retryable)
                }
                is UiState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    showLoading(false)
                    classrooms = state.data
                    refreshClassroomList()
                }
            }
        }

        collectFlow(viewModel.addState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> binding.btnAddClassroom.isEnabled = false
                is UiState.Error   -> {
                    binding.btnAddClassroom.isEnabled = true
                    binding.tvAddError.text           = state.message
                    binding.tvAddError.visibility     = View.VISIBLE
                }
                is UiState.Success -> {
                    binding.btnAddClassroom.isEnabled = true
                    binding.tvAddError.visibility     = View.GONE
                    binding.etRoomCode.text?.clear()
                    binding.etCapacity.text?.clear()
                    typeDropdown?.setSelection(0)
                    deptDropdown?.setSelection(0)
                    showSnackbar(R.string.classroom_added)
                    viewModel.resetAddState()
                }
            }
        }

        collectFlow(viewModel.editState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> Unit
                is UiState.Error   -> {
                    showErrorSnackbar(state.message)
                    viewModel.resetEditState()
                }
                is UiState.Success -> {
                    showSnackbar(R.string.classroom_updated)
                    viewModel.resetEditState()
                }
            }
        }

        collectFlow(viewModel.importState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> binding.btnImportClassrooms.isEnabled = false
                is UiState.Error   -> {
                    binding.btnImportClassrooms.isEnabled = true
                    showErrorSnackbar(state.message)
                    viewModel.resetImportState()
                }
                is UiState.Success -> {
                    binding.btnImportClassrooms.isEnabled = true
                    showSnackbar(getString(R.string.classroom_import_count, state.data))
                    viewModel.resetImportState()
                }
            }
        }
    }

    private fun onAddClicked() {
        val selectedPos  = deptDropdown?.selectedPosition() ?: 0
        val departmentId = if (selectedPos > 0 && departments.isNotEmpty())
            departments[selectedPos - 1].id else null
        val type = classroomTypes[typeDropdown?.selectedPosition() ?: 0]
        viewModel.addClassroom(
            roomCode     = binding.etRoomCode.text?.toString().orEmpty(),
            capacity     = binding.etCapacity.text?.toString().orEmpty(),
            departmentId = departmentId,
            type         = type
        )
    }

    private fun showDeleteDialog(classroom: Classroom) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.classroom_delete_title))
            .setMessage(getString(R.string.classroom_delete_message, classroom.roomCode))
            .setPositiveButton(getString(R.string.common_delete)) { _, _ ->
                classrooms = classrooms.filter { it.id != classroom.id }
                refreshClassroomList()
                PendingDelete.schedule(
                    anchor = binding.root,
                    owner = viewLifecycleOwner,
                    message = getString(R.string.ux_undo_deleted_classroom, classroom.roomCode),
                    onUndo = {
                        classrooms = (classrooms + classroom).sortedBy { it.roomCode }
                        refreshClassroomList()
                    },
                    performDelete = { viewModel.deleteClassroom(classroom.id) }
                )
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showEditDialog(classroom: Classroom) {
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val etCode = android.widget.EditText(requireContext()).apply {
            setText(classroom.roomCode); hint = getString(R.string.classroom_edit_code_hint)
        }
        val etCap = android.widget.EditText(requireContext()).apply {
            setText(classroom.capacity.toString())
            hint = getString(R.string.classroom_edit_capacity_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val typeSpinner = android.widget.Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item,
                classroomTypes.map { if (it == "lab") getString(R.string.classroom_type_lab) else getString(R.string.classroom_type_theory) }
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(classroomTypes.indexOf(classroom.type).coerceAtLeast(0))
        }
        val deptSpinner = android.widget.Spinner(requireContext()).apply {
            val names = listOf(getString(R.string.classroom_unassigned_option)) + departments.map { it.name }
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            val curIdx = departments.indexOfFirst { it.id == classroom.departmentId }
            setSelection(if (curIdx >= 0) curIdx + 1 else 0)
        }

        container.addView(etCode)
        container.addView(etCap)
        container.addView(android.widget.TextView(requireContext()).apply { text = getString(R.string.classroom_edit_type_label) })
        container.addView(typeSpinner)
        container.addView(android.widget.TextView(requireContext()).apply { text = getString(R.string.classroom_edit_dept_label) })
        container.addView(deptSpinner)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.classroom_edit_title))
            .setView(container)
            .setPositiveButton(getString(R.string.common_save)) { _, _ ->
                val deptIdx = deptSpinner.selectedItemPosition
                val deptId  = if (deptIdx > 0) departments.getOrNull(deptIdx - 1)?.id else null
                val type    = classroomTypes[typeSpinner.selectedItemPosition]
                viewModel.updateClassroom(
                    id = classroom.id,
                    roomCode = etCode.text.toString(),
                    capacity = etCap.text.toString(),
                    departmentId = deptId,
                    type = type
                )
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showImportPreview(result: CsvImporter.ParseResult<CsvImporter.ClassroomRow>) {
        val selectedPos  = deptDropdown?.selectedPosition() ?: 0
        val deptName     = if (selectedPos > 0) departments.getOrNull(selectedPos - 1)?.name else null
        val departmentId = if (selectedPos > 0) departments.getOrNull(selectedPos - 1)?.id else null

        if (result.valid.isEmpty()) {
            val errMsg = if (result.errors.isEmpty()) getString(R.string.import_preview_no_rows)
            else "Geçerli satır yok. ${result.errors.size} satır atlandı:\n" +
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
            title = getString(R.string.import_preview_title_classrooms),
            targetDescription = getString(R.string.import_preview_dept_target, deptName ?: getString(R.string.common_unassigned)),
            rows = result.valid,
            errors = result.errors,
            titleProvider = { it.roomCode },
            subtitleProvider = { "Kapasite: ${it.capacity}  •  ${if (it.type == "lab") "Laboratuvar" else "Teorik"}" },
            onImport = { chosen -> viewModel.importClassrooms(chosen, departmentId) }
        )
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.tvError.visibility     = View.GONE
        binding.btnRetry.visibility    = View.GONE
    }

    private fun showError(msg: String, retryable: Boolean) {
        binding.progressBar.visibility = View.GONE
        binding.tvError.text           = msg
        binding.tvError.visibility     = View.VISIBLE
        binding.btnRetry.visibility    = if (retryable) View.VISIBLE else View.GONE
    }

    private fun handleClassroomImport(uri: android.net.Uri) {
        val appCtx = requireContext().applicationContext
        binding.btnImportClassrooms.isEnabled = false
        lifecycleScope.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val kind = FileTypeDetector.detect(appCtx, uri)
                    val parsed: CsvImporter.ParseResult<CsvImporter.ClassroomRow> = when (kind) {
                        FileTypeDetector.Kind.XLSX, FileTypeDetector.Kind.XLS -> {
                            val stream = appCtx.contentResolver.openInputStream(uri)
                                ?: error("Dosya açılamadı.")
                            stream.use {
                                val r = ExcelHelper.importClassrooms(it)
                                CsvImporter.ParseResult(r.valid, r.errors)
                            }
                        }
                        FileTypeDetector.Kind.CSV -> {
                            val stream = appCtx.contentResolver.openInputStream(uri)
                                ?: error("Dosya açılamadı.")
                            val text = stream.use { it.bufferedReader().readText() }
                            CsvImporter.parseClassrooms(text)
                        }
                        FileTypeDetector.Kind.UNKNOWN ->
                            error("Dosya tipi tanınamadı. Lütfen .xlsx, .xls veya .csv yükleyin.")
                    }
                    kind to parsed
                }
            }
            if (!isAdded) return@launch
            binding.btnImportClassrooms.isEnabled = true
            result
                .onSuccess { (kind, parseResult) ->
                    logImportErrors("import_classrooms", uri, kind, parseResult.errors, parseResult.valid.size)
                    showImportPreview(parseResult)
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    reportImportFatal("import_classrooms", uri, it)
                    showImportFatal("Dosya okuma hatası: ${it.message ?: it::class.java.simpleName}")
                }
        }
    }

    private fun showImportFatal(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.import_error_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.common_ok), null)
            .show()
    }

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
            runCatching { reporter.reportMessage("ClassroomsFragment", action, summary, stackTrace = uri.toString()) }
        }
    }

    private fun reportImportFatal(action: String, uri: android.net.Uri, e: Throwable) {
        val reporter = ErrorReporter(requireActivity().application)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { reporter.reportException("ClassroomsFragment", "$action[${uri}]", e) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFirstResume) { isFirstResume = false; return }
        viewModel.loadClassrooms()
        viewModel.loadDepartmentsSilently()
    }

    /** Apply the current search query to the in-memory list. */
    private fun refreshClassroomList() {
        val filtered = if (classroomQuery.isBlank()) classrooms else classrooms.filter {
            it.roomCode.lowercase().contains(classroomQuery) ||
            it.type.lowercase().contains(classroomQuery) ||
            (it.departmentName.lowercase().contains(classroomQuery))
        }
        classroomAdapter.submitList(filtered)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class ClassroomAdapter(
    private val onEditClick: (Classroom) -> Unit,
    private val onDeleteClick: (Classroom) -> Unit
) : ListAdapter<Classroom, ClassroomAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemClassroomBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemClassroomBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = getItem(position)
        holder.binding.tvRoomCode.text   = c.roomCode
        val typeLabel = if (c.type == "lab") "Laboratuvar" else "Teorik"
        holder.binding.tvCapacity.text   = "Kapasite: ${c.capacity}  •  $typeLabel"
        holder.binding.tvDepartment.text = c.departmentName
        holder.binding.btnEditClassroom.visibility   = View.VISIBLE
        holder.binding.btnDeleteClassroom.visibility = View.VISIBLE
        holder.binding.btnEditClassroom.setOnClickListener   { onEditClick(c) }
        holder.binding.btnDeleteClassroom.setOnClickListener { onDeleteClick(c) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Classroom>() {
            override fun areItemsTheSame(old: Classroom, new: Classroom) = old.id == new.id
            override fun areContentsTheSame(old: Classroom, new: Classroom) = old == new
        }
    }
}
