// Lecturer home — welcome message, department name, weekly course count.
package com.unischeduler.ui.lecturer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.unischeduler.databinding.FragmentLecturerHomeBinding
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow

class LecturerHomeFragment : Fragment() {

    private var _binding: FragmentLecturerHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LecturerHomeViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLecturerHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRetry.setOnClickListener { viewModel.load() }

        collectFlow(viewModel.state) { state ->
            when (state) {
                is UiState.Idle    -> viewModel.load()
                is UiState.Loading -> showLoading(true)
                is UiState.Error   -> showError(state.message, state.retryable)
                is UiState.Success -> {
                    showLoading(false)
                    val d = state.data
                    binding.tvWelcome.text      = "Welcome, ${d.lecturer.fullName}"
                    binding.tvDepartment.text   = d.lecturer.departmentName
                    binding.tvWeeklyCount.text  = d.weeklyCount.toString()
                    binding.tvWelcome.visibility    = View.VISIBLE
                    binding.tvDepartment.visibility = View.VISIBLE
                    binding.cardWeekly.visibility   = View.VISIBLE
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

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
