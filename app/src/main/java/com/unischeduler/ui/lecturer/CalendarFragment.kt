package com.unischeduler.ui.lecturer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.unischeduler.R
import com.unischeduler.data.model.ScheduleEntry
import com.unischeduler.databinding.FragmentWeeklyScheduleBinding
import com.unischeduler.ui.shared.ScheduleViewConfig
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow

class CalendarFragment : Fragment() {

    private var _binding: FragmentWeeklyScheduleBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CalendarViewModel by viewModels()
    private var isFirstResume = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWeeklyScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTitle.setText(R.string.calendar_my_schedule_title)
        binding.btnRetry.setOnClickListener { viewModel.loadEntries() }
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadEntries() }

        binding.weeklySchedule.setOnEntryClickListener { entry ->
            showEntryDetail(entry)
        }

        collectFlow(viewModel.state) { state ->
            when (state) {
                is UiState.Idle -> viewModel.loadEntries()
                is UiState.Loading -> {
                    if (!binding.swipeRefresh.isRefreshing) binding.progressBar.visibility = View.VISIBLE
                    binding.tvError.visibility = View.GONE
                    binding.tvEmpty.visibility = View.GONE
                    binding.btnRetry.visibility = View.GONE
                }
                is UiState.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.text = state.message
                    binding.tvError.visibility = View.VISIBLE
                    binding.btnRetry.visibility = if (state.retryable) View.VISIBLE else View.GONE
                }
                is UiState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.visibility = View.GONE
                    binding.btnRetry.visibility = View.GONE

                    val data = state.data
                    val settings = data.settings

                    binding.weeklySchedule.setConfig(
                        ScheduleViewConfig(
                            dayStart = settings.dayStart,
                            dayEnd = settings.dayEnd,
                            timeStepMinutes = settings.timeStepMinutes.coerceAtLeast(30),
                            activeDays = settings.activeDays.ifEmpty {
                                listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
                            }
                        )
                    )

                    if (data.entries.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                    } else {
                        binding.tvEmpty.visibility = View.GONE
                        binding.weeklySchedule.setEntries(data.entries)
                    }
                }
            }
        }
    }

    private fun showEntryDetail(entry: ScheduleEntry) {
        val msg = buildString {
            append("Ders: ${entry.courseCode} — ${entry.courseName}\n")
            append("Hoca: ${entry.lecturerName}\n")
            append("Sınıf: ${entry.classroomCode}\n")
            append("Saat: ${entry.timeRange}")
        }
        AlertDialog.Builder(requireContext())
            .setTitle("${entry.day} — ${entry.courseCode}")
            .setMessage(msg)
            .setPositiveButton(getString(R.string.common_ok), null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (isFirstResume) { isFirstResume = false; return }
        viewModel.loadEntries()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
