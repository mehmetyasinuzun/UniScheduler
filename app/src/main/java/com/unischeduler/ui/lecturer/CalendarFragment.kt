// Lecturer calendar — weekly grid showing own assigned courses and classrooms.
// Real-time update via collectFlow + repeatOnLifecycle (Lab Task 2 pattern).
package com.unischeduler.ui.lecturer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.unischeduler.databinding.FragmentCalendarBinding
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CalendarViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRetry.setOnClickListener { viewModel.loadEntries() }

        collectFlow(viewModel.state) { state ->
            when (state) {
                is UiState.Idle    -> viewModel.loadEntries()
                is UiState.Loading -> binding.progressBar.visibility = View.VISIBLE
                is UiState.Error   -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.text           = state.message
                    binding.tvError.visibility     = View.VISIBLE
                    binding.btnRetry.visibility    = if (state.retryable) View.VISIBLE else View.GONE
                }
                is UiState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.visibility     = View.GONE
                    binding.btnRetry.visibility    = View.GONE
                    CalendarRenderer.render(binding.calendarGrid, state.data)
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
