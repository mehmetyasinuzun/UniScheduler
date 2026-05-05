// AvailabilityFragment — Lecturer marks free-time blocks.
package com.unischeduler.ui.lecturer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.unischeduler.data.model.DAYS
import com.unischeduler.data.model.LecturerAvailability
import com.unischeduler.databinding.FragmentAvailabilityBinding
import com.unischeduler.databinding.ItemAvailabilityBinding
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow

class AvailabilityFragment : Fragment() {

    private var _binding: FragmentAvailabilityBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AvailabilityViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAvailabilityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.spinnerDay.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, DAYS
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.rvSlots.layoutManager = LinearLayoutManager(requireContext())

        binding.etStartTime.setOnClickListener { showTimePicker { binding.etStartTime.setText(it) } }
        binding.etEndTime.setOnClickListener   { showTimePicker { binding.etEndTime.setText(it) } }
        binding.btnAdd.setOnClickListener      { onAddClicked() }
        binding.btnRetry.setOnClickListener    { viewModel.load() }

        collectFlow(viewModel.state) { state ->
            when (state) {
                is UiState.Idle    -> viewModel.load()
                is UiState.Loading -> showLoading(true)
                is UiState.Error   -> showError(state.message, state.retryable)
                is UiState.Success -> {
                    showLoading(false)
                    binding.rvSlots.adapter = AvailabilityAdapter(state.data) { slot ->
                        viewModel.deleteSlot(slot.id)
                    }
                }
            }
        }

        collectFlow(viewModel.saveState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> binding.btnAdd.isEnabled = false
                is UiState.Error   -> {
                    binding.btnAdd.isEnabled       = true
                    binding.tvSaveError.text       = state.message
                    binding.tvSaveError.visibility = View.VISIBLE
                }
                is UiState.Success -> {
                    binding.btnAdd.isEnabled       = true
                    binding.tvSaveError.visibility = View.GONE
                    binding.etStartTime.text?.clear()
                    binding.etEndTime.text?.clear()
                    Toast.makeText(requireContext(), "Slot added.", Toast.LENGTH_SHORT).show()
                    viewModel.resetSaveState()
                }
            }
        }
    }

    private fun onAddClicked() {
        viewModel.addSlot(
            day       = DAYS[binding.spinnerDay.selectedItemPosition],
            startTime = binding.etStartTime.text?.toString().orEmpty(),
            endTime   = binding.etEndTime.text?.toString().orEmpty()
        )
    }

    private fun showTimePicker(onSelected: (String) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(9).setMinute(0)
            .setTitleText("Select time")
            .build()
        picker.addOnPositiveButtonClickListener {
            onSelected(String.format("%02d:%02d", picker.hour, picker.minute))
        }
        picker.show(parentFragmentManager, "timePicker")
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

class AvailabilityAdapter(
    private val items: List<LecturerAvailability>,
    private val onDelete: (LecturerAvailability) -> Unit
) : RecyclerView.Adapter<AvailabilityAdapter.VH>() {

    inner class VH(val binding: ItemAvailabilityBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAvailabilityBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = maxOf(items.size, 1)

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (items.isEmpty()) {
            holder.binding.tvDay.text  = "No availability set yet."
            holder.binding.tvTime.text = "Add your free time blocks above."
            holder.binding.btnDelete.visibility = View.GONE
            return
        }
        val slot = items[position]
        holder.binding.tvDay.text  = slot.day
        holder.binding.tvTime.text = slot.timeRange
        holder.binding.btnDelete.visibility = View.VISIBLE
        holder.binding.btnDelete.setOnClickListener { onDelete(slot) }
    }
}
