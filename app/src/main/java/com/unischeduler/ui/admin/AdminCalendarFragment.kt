// Admin calendar — shows full department schedule (all lecturers).
// Reuses ScheduleRepository.getAllEntries() + same grid layout as Lecturer calendar.
package com.unischeduler.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.unischeduler.data.model.ScheduleEntry
import com.unischeduler.databinding.FragmentCalendarBinding
import com.unischeduler.ui.lecturer.CalendarRenderer
import com.unischeduler.ui.lecturer.CalendarViewModel
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow

class AdminCalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CalendarViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRetry.setOnClickListener { viewModel.loadAllEntries() }

        collectFlow(viewModel.state) { state ->
            when (state) {
                is UiState.Idle    -> viewModel.loadAllEntries()
                is UiState.Loading -> showLoading(true)
                is UiState.Error   -> showError(state.message, state.retryable)
                is UiState.Success -> {
                    showLoading(false)
                    CalendarRenderer.render(binding.calendarGrid, state.data) { day, timeSlot, entries ->
                        showSlotDetails(day, timeSlot, entries)
                    }
                }
            }
        }
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

    private fun showSlotDetails(day: String, timeSlot: String, entries: List<ScheduleEntry>) {
        if (entries.isEmpty()) return
        val message = entries.joinToString("\n") {
            "${it.courseCode} - ${it.courseName} | ${it.lecturerName} | ${it.classroomCode}"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("$day $timeSlot")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
