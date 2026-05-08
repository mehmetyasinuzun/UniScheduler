// Lecturer home — welcome message, department name, weekly course count.
package com.unischeduler.ui.lecturer

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.appcompat.app.AlertDialog
import com.unischeduler.App
import com.unischeduler.MainActivity
import com.unischeduler.R
import com.unischeduler.databinding.FragmentLecturerHomeBinding
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow

class LecturerHomeFragment : Fragment() {

    private var _binding: FragmentLecturerHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LecturerHomeViewModel by viewModels()
    private var isFirstResume = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLecturerHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRetry.setOnClickListener { viewModel.load() }
        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        setupThemeSelector()
        setupLanguageSelector()

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setPositiveButton(R.string.logout_button) { _, _ ->
                    (requireActivity() as? MainActivity)?.logout()
                }
                .setNegativeButton(R.string.common_cancel, null)
                .show()
        }

        collectFlow(viewModel.state) { state ->
            when (state) {
                is UiState.Idle    -> viewModel.load()
                is UiState.Loading -> showLoading(true)
                is UiState.Error   -> {
                    binding.swipeRefresh.isRefreshing = false
                    showError(state.message, state.retryable)
                }
                is UiState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    showLoading(false)
                    val d = state.data
                    binding.tvWelcome.text      = getString(R.string.lecturer_home_welcome, d.lecturer.fullName)
                    binding.tvDepartment.text   = d.lecturer.departmentName
                    binding.tvWeeklyCount.text  = d.weeklyCount.toString()
                    binding.tvWelcome.visibility    = View.VISIBLE
                    binding.tvDepartment.visibility = View.VISIBLE
                    binding.cardWeekly.visibility   = View.VISIBLE
                }
            }
        }
    }

    private fun setupThemeSelector() {
        val prefs = requireContext().getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
        val currentMode = prefs.getInt(App.KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        val checkedId = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO  -> binding.rbThemeLight.id
            AppCompatDelegate.MODE_NIGHT_YES -> binding.rbThemeDark.id
            else                             -> binding.rbThemeSystem.id
        }
        binding.rgTheme.check(checkedId)

        binding.rgTheme.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                binding.rbThemeLight.id -> AppCompatDelegate.MODE_NIGHT_NO
                binding.rbThemeDark.id  -> AppCompatDelegate.MODE_NIGHT_YES
                else                    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            prefs.edit().putInt(App.KEY_THEME_MODE, mode).apply()
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    private fun setupLanguageSelector() {
        val prefs = requireContext().getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
        val currentLang = prefs.getString(App.KEY_LANGUAGE, null)

        val checkedId = when (currentLang) {
            "tr" -> binding.rbLangTurkish.id
            "en" -> binding.rbLangEnglish.id
            else -> binding.rbLangSystem.id
        }
        binding.rgLanguage.check(checkedId)

        binding.rgLanguage.setOnCheckedChangeListener { _, id ->
            val lang = when (id) {
                binding.rbLangTurkish.id -> "tr"
                binding.rbLangEnglish.id -> "en"
                else -> null
            }
            prefs.edit().putString(App.KEY_LANGUAGE, lang).apply()
            val locales = if (lang != null) {
                LocaleListCompat.forLanguageTags(lang)
            } else {
                LocaleListCompat.getEmptyLocaleList()
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading && !binding.swipeRefresh.isRefreshing) View.VISIBLE else View.GONE
        binding.tvError.visibility     = View.GONE
        binding.btnRetry.visibility    = View.GONE
    }

    private fun showError(msg: String, retryable: Boolean) {
        binding.progressBar.visibility = View.GONE
        binding.tvError.text           = msg
        binding.tvError.visibility     = View.VISIBLE
        binding.btnRetry.visibility    = if (retryable) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (isFirstResume) { isFirstResume = false; return }
        viewModel.load()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
