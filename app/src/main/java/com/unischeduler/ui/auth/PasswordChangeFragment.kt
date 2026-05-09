// First-login mandatory password change screen.
// Lecturer cannot navigate away until password is changed.
package com.unischeduler.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.unischeduler.R
import com.unischeduler.databinding.FragmentPasswordChangeBinding
import com.unischeduler.util.SessionManager
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow

class PasswordChangeFragment : Fragment() {

    private var _binding: FragmentPasswordChangeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PasswordChangeViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPasswordChangeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Block back navigation — password change is mandatory
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { }

        binding.btnSave.setOnClickListener {
            viewModel.changePassword(
                current = binding.etCurrentPassword.text?.toString().orEmpty(),
                newPass = binding.etNewPassword.text?.toString().orEmpty(),
                confirm = binding.etConfirmPassword.text?.toString().orEmpty()
            )
        }

        collectFlow(viewModel.state) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> showLoading(true)
                is UiState.Error   -> { showLoading(false); showError(state.message) }
                is UiState.Success -> {
                    val session = SessionManager(requireContext())
                    if (session.isAdmin) {
                        findNavController().navigate(R.id.action_passwordChange_to_adminHome)
                    } else {
                        // Same as fresh login: arm tomorrow's reminders.
                        com.unischeduler.notif.ReminderScheduler
                            .scheduleNextDayReminders(requireContext().applicationContext)
                        findNavController().navigate(R.id.action_passwordChange_to_lecturerHome)
                    }
                }
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled      = !loading
        binding.tvError.visibility     = View.GONE
    }

    private fun showError(msg: String) {
        binding.tvError.text       = msg
        binding.tvError.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
