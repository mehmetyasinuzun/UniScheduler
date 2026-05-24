package com.unischeduler.ui.lecturer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.unischeduler.util.DropdownController
import com.unischeduler.util.showSnackbar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.unischeduler.R
import com.unischeduler.data.model.DAYS
import com.unischeduler.databinding.FragmentAvailabilityBinding
import com.unischeduler.ui.shared.AvailabilityGridConfig
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow
import com.unischeduler.util.setDebouncedClickListener

class AvailabilityFragment : Fragment() {

    private var _binding: FragmentAvailabilityBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AvailabilityViewModel by viewModels()

    private lateinit var dayDropdown: DropdownController<String>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAvailabilityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dayDropdown = DropdownController(binding.actvDay, DAYS)

        binding.etStartTime.setOnClickListener { showTimePicker { binding.etStartTime.setText(it) } }
        binding.etEndTime.setOnClickListener   { showTimePicker { binding.etEndTime.setText(it) } }
        // Mutation butonu — 600ms debounce ile duplicate availability slot engellenir.
        binding.btnAdd.setDebouncedClickListener { onAddClicked() }
        binding.btnRetry.setOnClickListener    { viewModel.load() }

        binding.availabilityGrid.setOnBusySlotClickListener { slot ->
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.availability_remove_title))
                .setMessage(getString(R.string.availability_remove_message, slot.day, slot.timeRange))
                .setPositiveButton(getString(R.string.common_remove)) { _, _ -> viewModel.deleteSlot(slot.id) }
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show()
        }

        binding.availabilityGrid.setOnEmptyCellClickListener { day, startTime, endTime ->
            dayDropdown.setSelection(DAYS.indexOf(day).coerceAtLeast(0))
            binding.etStartTime.setText(startTime)
            binding.etEndTime.setText(endTime)
        }

        collectFlow(viewModel.state) { state ->
            when (state) {
                is UiState.Idle -> viewModel.load()
                is UiState.Loading -> showLoading(true)
                is UiState.Error -> showError(state.message, state.retryable)
                is UiState.Success -> {
                    showLoading(false)
                    val data = state.data
                    val settings = data.settings

                    binding.availabilityGrid.setConfig(
                        AvailabilityGridConfig(
                            dayStart = settings.dayStart,
                            dayEnd = settings.dayEnd,
                            timeStepMinutes = settings.timeStepMinutes.coerceAtLeast(30),
                            activeDays = settings.activeDays.ifEmpty { DAYS }
                        )
                    )
                    binding.availabilityGrid.setBusySlots(data.busySlots)
                    binding.availabilityGrid.setScheduleEntries(data.scheduleEntries)
                }
            }
        }

        collectFlow(viewModel.saveState) { state ->
            when (state) {
                is UiState.Idle -> Unit
                is UiState.Loading -> binding.btnAdd.isEnabled = false
                is UiState.Error -> {
                    binding.btnAdd.isEnabled       = true
                    binding.tvSaveError.text       = state.message
                    binding.tvSaveError.visibility = View.VISIBLE
                }
                is UiState.Success -> {
                    binding.btnAdd.isEnabled       = true
                    binding.tvSaveError.visibility = View.GONE
                    binding.etStartTime.text?.clear()
                    binding.etEndTime.text?.clear()
                    showSnackbar(R.string.availability_slot_added)
                    viewModel.resetSaveState()
                }
            }
        }
    }

    private fun onAddClicked() {
        viewModel.addSlot(
            day       = DAYS[dayDropdown.selectedPosition()],
            startTime = binding.etStartTime.text?.toString().orEmpty(),
            endTime   = binding.etEndTime.text?.toString().orEmpty()
        )
    }

    private fun showTimePicker(onSelected: (String) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(9).setMinute(0)
            .setTitleText(getString(R.string.availability_time_pick_title))
            .build()
        picker.addOnPositiveButtonClickListener {
            onSelected(String.format(java.util.Locale.US, "%02d:%02d", picker.hour, picker.minute))
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
