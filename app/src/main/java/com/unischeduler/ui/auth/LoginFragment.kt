// Login screen — entry point for all users.
// Navigates to: AdminHome (admin) | PasswordChange (first login) | LecturerHome (lecturer)
package com.unischeduler.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.unischeduler.R
import com.unischeduler.databinding.FragmentLoginBinding
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow
import com.unischeduler.util.setDebouncedClickListener

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Debounced — kullanıcı hızlıca 2 kez basarsa 2. tıklama yutulur,
        // tek bir signIn request gider. Aksi durumda Supabase 5 req/dk
        // rate-limit'e takılma veya double-session race riski vardı.
        binding.btnLogin.setDebouncedClickListener {
            val username = binding.etUsername.text?.toString().orEmpty().trim()
            val password = binding.etPassword.text?.toString().orEmpty()
            viewModel.login(username, password)
        }

        collectFlow(viewModel.state) { state ->
            // Fragment detached olduktan sonra StateFlow emit gelebilir
            // (login background'da tamamlanırsa). isAdded kontrolü
            // findNavController() IllegalStateException'ını önler.
            if (!isAdded) return@collectFlow
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> showLoading(true)
                is UiState.Error   -> {
                    showLoading(false)
                    showError(state.message)
                }
                is UiState.Success -> {
                    showLoading(false)
                    navigate(state.data)
                }
            }
        }
    }

    private fun navigate(result: LoginResult) {
        if (!isAdded) return
        // Lecturer just finished logging in → arm tomorrow's reminders so
        // they don't have to wait for the next 23:00 worker tick. No-op
        // for admins (cancelAll inside scheduleNextDayReminders bails out
        // on non-lecturer sessions).
        if (result.role == "lecturer" && !result.mustChangePassword) {
            com.unischeduler.notif.ReminderScheduler
                .scheduleNextDayReminders(requireContext().applicationContext)
        }
        when {
            result.mustChangePassword                -> findNavController().navigate(R.id.action_login_to_passwordChange)
            result.role == "admin"                   -> findNavController().navigate(R.id.action_login_to_adminHome)
            else                                     -> findNavController().navigate(R.id.action_login_to_lecturerHome)
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled     = !loading
        binding.tvError.visibility     = View.GONE
    }

    private fun showError(message: String) {
        binding.tvError.text       = message
        binding.tvError.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
