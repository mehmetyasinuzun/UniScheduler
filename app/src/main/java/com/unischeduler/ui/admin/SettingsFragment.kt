package com.unischeduler.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.unischeduler.data.model.Department
import com.unischeduler.databinding.FragmentSettingsBinding
import com.unischeduler.databinding.ItemDepartmentBinding
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvDepartments.layoutManager = LinearLayoutManager(requireContext())
        binding.btnRetry.setOnClickListener { viewModel.loadDepartments() }

        binding.btnAddDept.setOnClickListener {
            viewModel.addDepartment(binding.etDeptName.text?.toString().orEmpty())
        }

        collectFlow(viewModel.state) { state ->
            when (state) {
                is UiState.Idle    -> viewModel.loadDepartments()
                is UiState.Loading -> showLoading(true)
                is UiState.Error   -> showError(state.message, state.retryable)
                is UiState.Success -> {
                    showLoading(false)
                    binding.rvDepartments.adapter = DepartmentAdapter(
                        state.data,
                        onEditClick = { showEditDialog(it) },
                        onDeleteClick = { showDeleteDialog(it) }
                    )
                }
            }
        }

        collectFlow(viewModel.addState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> binding.btnAddDept.isEnabled = false
                is UiState.Error   -> {
                    binding.btnAddDept.isEnabled    = true
                    binding.tvDeptAddError.text     = state.message
                    binding.tvDeptAddError.visibility = View.VISIBLE
                }
                is UiState.Success -> {
                    binding.btnAddDept.isEnabled      = true
                    binding.tvDeptAddError.visibility = View.GONE
                    binding.etDeptName.text?.clear()
                    Toast.makeText(requireContext(), "Department added.", Toast.LENGTH_SHORT).show()
                    viewModel.resetAddState()
                }
            }
        }

        collectFlow(viewModel.editState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> Unit
                is UiState.Error   -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetEditState()
                }
                is UiState.Success -> {
                    Toast.makeText(requireContext(), "Department updated.", Toast.LENGTH_SHORT).show()
                    viewModel.resetEditState()
                }
            }
        }

        collectFlow(viewModel.deleteState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> Unit
                is UiState.Error   -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetDeleteState()
                }
                is UiState.Success -> {
                    Toast.makeText(requireContext(), "Department deleted.", Toast.LENGTH_SHORT).show()
                    viewModel.resetDeleteState()
                }
            }
        }
    }

    private fun showEditDialog(dept: Department) {
        val input = EditText(requireContext()).apply {
            setText(dept.name)
            setPadding(48, 32, 48, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Edit Department")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                viewModel.editDepartment(dept.id, input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteDialog(dept: Department) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Department")
            .setMessage("Delete \"${dept.name}\"? Related lecturers and courses may be affected.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteDepartment(dept.id) }
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

class DepartmentAdapter(
    private val items: List<Department>,
    private val onEditClick: (Department) -> Unit,
    private val onDeleteClick: (Department) -> Unit
) : RecyclerView.Adapter<DepartmentAdapter.VH>() {

    inner class VH(val binding: ItemDepartmentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemDepartmentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = maxOf(items.size, 1)

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (items.isEmpty()) {
            holder.binding.tvDeptName.text = "No departments yet."
            holder.binding.btnEditDept.visibility = View.GONE
            holder.binding.btnDeleteDept.visibility = View.GONE
            return
        }
        val d = items[position]
        holder.binding.tvDeptName.text = d.name
        holder.binding.btnEditDept.visibility = View.VISIBLE
        holder.binding.btnDeleteDept.visibility = View.VISIBLE
        holder.binding.btnEditDept.setOnClickListener { onEditClick(d) }
        holder.binding.btnDeleteDept.setOnClickListener { onDeleteClick(d) }
    }
}
