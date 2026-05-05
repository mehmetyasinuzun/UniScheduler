package com.unischeduler.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
import com.unischeduler.data.model.Classroom
import com.unischeduler.data.model.Department
import com.unischeduler.databinding.FragmentClassroomsBinding
import com.unischeduler.databinding.ItemClassroomBinding
import com.unischeduler.util.CsvExporter
import com.unischeduler.util.CsvImporter
import com.unischeduler.util.ExcelHelper
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow

class ClassroomsFragment : Fragment() {

    private var _binding: FragmentClassroomsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ClassroomsViewModel by viewModels()

    private var departments: List<Department> = emptyList()
    private var classrooms: List<Classroom>   = emptyList()
    private val classroomTypes = listOf("theory", "lab")

    private lateinit var classroomImportLauncher: ActivityResultLauncher<String>
    private lateinit var classroomExportLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        classroomImportLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return@registerForActivityResult
            val fileName = uri.lastPathSegment ?: ""

            if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                val result = ExcelHelper.importClassrooms(inputStream)
                inputStream.close()
                showImportPreview(CsvImporter.ParseResult(result.valid, result.errors))
            } else {
                val text = inputStream.bufferedReader().readText()
                inputStream.close()
                val result = CsvImporter.parseClassrooms(text)
                showImportPreview(result)
            }
        }

        classroomExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
            uri ?: return@registerForActivityResult
            try {
                val outputStream = requireContext().contentResolver.openOutputStream(uri) ?: return@registerForActivityResult
                ExcelHelper.exportClassrooms(classrooms, outputStream)
                outputStream.close()
                Toast.makeText(requireContext(), "Classrooms exported successfully.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
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

        // Type spinner (theory/lab)
        binding.spinnerType.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, classroomTypes.map { it.replaceFirstChar { c -> c.uppercase() } }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.btnRetry.setOnClickListener            { viewModel.loadClassrooms() }
        binding.btnGoAssignment.setOnClickListener     { findNavController().navigate(R.id.action_classrooms_to_assignment) }
        binding.btnAddClassroom.setOnClickListener     { onAddClicked() }
        binding.btnImportClassrooms.setOnClickListener { classroomImportLauncher.launch("*/*") }
        binding.btnExportClassrooms.setOnClickListener { classroomExportLauncher.launch("classrooms.xlsx") }

        collectFlow(viewModel.departments) { depts ->
            departments = depts
            val names = listOf("— None —") + depts.map { it.name }
            binding.spinnerDept.adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_item, names
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }

        collectFlow(viewModel.state) { state ->
            when (state) {
                is UiState.Idle    -> viewModel.loadClassrooms()
                is UiState.Loading -> showLoading(true)
                is UiState.Error   -> showError(state.message, state.retryable)
                is UiState.Success -> {
                    showLoading(false)
                    classrooms = state.data
                    binding.rvClassrooms.adapter = ClassroomAdapter(state.data) { showDeleteDialog(it) }
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
                    binding.spinnerType.setSelection(0)
                    binding.spinnerDept.setSelection(0)
                    Toast.makeText(requireContext(), "Classroom added.", Toast.LENGTH_SHORT).show()
                    viewModel.resetAddState()
                }
            }
        }

        collectFlow(viewModel.importState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> binding.btnImportClassrooms.isEnabled = false
                is UiState.Error   -> {
                    binding.btnImportClassrooms.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetImportState()
                }
                is UiState.Success -> {
                    binding.btnImportClassrooms.isEnabled = true
                    Toast.makeText(requireContext(), "${state.data} classroom(s) imported.", Toast.LENGTH_SHORT).show()
                    viewModel.resetImportState()
                }
            }
        }
    }

    private fun onAddClicked() {
        val selectedPos  = binding.spinnerDept.selectedItemPosition
        val departmentId = if (selectedPos > 0 && departments.isNotEmpty())
            departments[selectedPos - 1].id else null
        val type = classroomTypes[binding.spinnerType.selectedItemPosition]
        viewModel.addClassroom(
            roomCode     = binding.etRoomCode.text?.toString().orEmpty(),
            capacity     = binding.etCapacity.text?.toString().orEmpty(),
            departmentId = departmentId,
            type         = type
        )
    }

    private fun showDeleteDialog(classroom: Classroom) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Classroom")
            .setMessage("Delete ${classroom.roomCode}? Any related schedule entries will also be removed.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteClassroom(classroom.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showImportPreview(result: CsvImporter.ParseResult<CsvImporter.ClassroomRow>) {
        val selectedPos  = binding.spinnerDept.selectedItemPosition
        val deptName     = if (selectedPos > 0) departments.getOrNull(selectedPos - 1)?.name else null
        val departmentId = if (selectedPos > 0) departments.getOrNull(selectedPos - 1)?.id else null

        val msg = buildString {
            append("Found ${result.valid.size} valid row(s).")
            if (result.errors.isNotEmpty()) {
                append("\n\nSkipped ${result.errors.size} row(s):")
                result.errors.take(5).forEach { append("\n• $it") }
            }
            append("\n\nDepartment: ${deptName ?: "None (unassigned)"}")
            append("\nImport now?")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Import Classrooms")
            .setMessage(msg)
            .setPositiveButton("Import") { _, _ ->
                if (result.valid.isNotEmpty()) viewModel.importClassrooms(result.valid, departmentId)
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class ClassroomAdapter(
    private val items: List<Classroom>,
    private val onDeleteClick: (Classroom) -> Unit
) : RecyclerView.Adapter<ClassroomAdapter.VH>() {

    inner class VH(val binding: ItemClassroomBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemClassroomBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = maxOf(items.size, 1)

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (items.isEmpty()) {
            holder.binding.tvRoomCode.text   = "No classrooms yet."
            holder.binding.tvCapacity.text   = ""
            holder.binding.tvDepartment.text = ""
            holder.binding.btnDeleteClassroom.visibility = View.GONE
            return
        }
        val c = items[position]
        holder.binding.tvRoomCode.text   = c.roomCode
        holder.binding.tvCapacity.text   = "Capacity: ${c.capacity}  •  ${c.type}"
        holder.binding.tvDepartment.text = c.departmentName
        holder.binding.btnDeleteClassroom.visibility = View.VISIBLE
        holder.binding.btnDeleteClassroom.setOnClickListener { onDeleteClick(c) }
    }
}
