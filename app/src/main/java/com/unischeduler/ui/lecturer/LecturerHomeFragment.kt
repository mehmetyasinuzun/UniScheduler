// Lecturer home — welcome message, department name, weekly course count.
package com.unischeduler.ui.lecturer

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.unischeduler.App
import com.unischeduler.MainActivity
import com.unischeduler.R
import com.unischeduler.data.model.OrgSettings
import com.unischeduler.data.model.ScheduleEntry
import com.unischeduler.data.repository.OrgSettingsRepository
import com.unischeduler.data.repository.ScheduleRepository
import com.unischeduler.databinding.FragmentLecturerHomeBinding
import com.unischeduler.util.IcsExporter
import com.unischeduler.util.NotificationPreferences
import com.unischeduler.util.NotificationPreferencesUi
import com.unischeduler.util.PdfExporter
import com.unischeduler.util.SessionManager
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow
import com.unischeduler.util.shareDocument
import com.unischeduler.util.writeBytesToUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class LecturerHomeFragment : Fragment() {

    private var _binding: FragmentLecturerHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LecturerHomeViewModel by viewModels()
    private var isFirstResume = true

    // SAF launchers — both must be registered in onCreate per Android contract.
    private lateinit var pdfSaveLauncher: ActivityResultLauncher<String>
    private lateinit var icsSaveLauncher: ActivityResultLauncher<String>
    private lateinit var notifPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // PDF export contract
        pdfSaveLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf")
        ) { uri ->
            uri ?: return@registerForActivityResult
            val title = getString(R.string.export_pdf_lecturer_title, currentLecturerLabel())
            writeBytesToUri(
                destination = uri,
                produce = { renderPdfBytes(title) },
                onDone = { savedUri ->
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.export_success)
                        .setPositiveButton(R.string.export_pdf_share) { _, _ ->
                            shareDocument(savedUri, "application/pdf")
                        }
                        .setNegativeButton(R.string.common_ok, null)
                        .show()
                }
            )
        }
        // iCal export contract — text/calendar is the canonical MIME for .ics
        icsSaveLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("text/calendar")
        ) { uri ->
            uri ?: return@registerForActivityResult
            writeBytesToUri(
                destination = uri,
                produce = { renderIcsBytes() }
            )
        }
        // POST_NOTIFICATIONS runtime permission for Android 13+.
        // Only result we care about is "denied" → revert master switch.
        notifPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            // Persist the preference flip even if the fragment was
            // destroyed by the time the system permission dialog returns
            // — the user's intent is unambiguous. Touching `binding`
            // requires the view to still be attached.
            val appCtx = context?.applicationContext ?: return@registerForActivityResult
            if (!granted) {
                NotificationPreferences(appCtx).enabled = false
            }
            if (!isAdded || _binding == null) return@registerForActivityResult
            if (!granted) {
                binding.cardNotifications.root.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(
                    R.id.swNotifMaster
                )?.isChecked = false
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLecturerHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRetry.setOnClickListener { viewModel.load() }
        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }
        binding.btnExportPdf.setOnClickListener {
            pdfSaveLauncher.launch(getString(R.string.export_pdf_default_filename))
        }

        // Coach mark — lecturer PDF butonu için ilk-açılış balonu.
        com.unischeduler.util.CoachMarks.showOnce(
            fragment = this,
            target   = binding.btnExportPdf,
            titleRes = R.string.coach_lecturer_pdf_title,
            bodyRes  = R.string.coach_lecturer_pdf_body,
            key      = com.unischeduler.util.CoachKey.LECTURER_PDF
        )
        binding.btnExportIcs.setOnClickListener {
            icsSaveLauncher.launch(getString(R.string.export_ics_default_filename))
        }

        // Notification preferences card (B3) — also re-arms tomorrow's
        // alarms whenever the user changes any setting so the change is
        // reflected immediately, not at the next 23:00 worker run.
        // Tüm setup'lar defansif sarmalandı: card layout'u eksik veya
        // tema değişimi sonrası view restoration başarısızsa fragment
        // çökmesin, kullanıcı en azından çıkış yapabilsin.
        val ctx = requireContext().applicationContext
        runCatching {
            NotificationPreferencesUi.bind(
                root = binding.cardNotifications.root,
                prefs = NotificationPreferences(ctx),
                permissionLauncher = notifPermissionLauncher,
                onAnyChange = { NotificationPreferencesUi.rescheduleAfterChange(ctx) }
            )
        }.onFailure {
            android.util.Log.e("LecturerHome", "Notif card bind failed", it)
            binding.cardNotifications.root.visibility = View.GONE
        }

        runCatching { setupThemeSelector() }
            .onFailure { android.util.Log.e("LecturerHome", "Theme setup failed", it) }
        runCatching { setupLanguageSelector() }
            .onFailure { android.util.Log.e("LecturerHome", "Lang setup failed", it) }

        binding.btnReplayTutorial.setOnClickListener {
            com.unischeduler.util.TutorialPrefs(requireContext()).resetLecturerTutorial()
            com.unischeduler.ui.onboarding.LecturerTutorialActivity.start(requireContext())
        }

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
                    // KRITIK: Error state'inde önceki Success view'larını gizle.
                    // Yoksa kullanıcı "Hoş geldiniz" mesajını ve "Bu hesap bir
                    // kuruma bağlı değil" hatasını AYNI ANDA görüyor — kafa
                    // karıştırıcı. Her state geçişi tüm view'ları tutarlı
                    // hâle getirir.
                    hideContentCards()
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
                    binding.cardExport.visibility   = View.VISIBLE
                    binding.cardNotifications.root.visibility = View.VISIBLE
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

    /** Success state için doldurulan kartları gizler. Error / Loading state'inde
     *  çağrılır — stale veri ile hata mesajının birlikte görünmesi önlenir. */
    private fun hideContentCards() {
        binding.tvWelcome.visibility    = View.GONE
        binding.tvDepartment.visibility = View.GONE
        binding.cardWeekly.visibility   = View.GONE
        binding.cardExport.visibility   = View.GONE
        binding.cardNotifications.root.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (isFirstResume) { isFirstResume = false; return }
        viewModel.load()
    }

    // ── Export helpers ─────────────────────────────────────────────────────-

    private fun currentLecturerLabel(): String =
        (viewModel.state.value as? UiState.Success)?.data?.lecturer?.fullName
            ?: SessionManager(requireContext().applicationContext).username

    /** Pulls the latest entries + settings from the network so the PDF
     *  matches what the user actually sees, not whatever was cached. */
    private suspend fun loadExportInputs(): Pair<List<ScheduleEntry>, OrgSettings> = coroutineScope {
        val ctx = requireContext().applicationContext
        val session = SessionManager(ctx)
        val orgId = session.orgId
        val lecturerId = session.lecturerId
        if (orgId <= 0 || lecturerId <= 0) {
            // Bozuk session — health check de fail eder. Kullanıcıya
            // net mesaj ver, "çıkış yapıp tekrar gir" diye yönlendir.
            // MainActivity.onCreate sonraki açılışta otomatik temizleyecek
            // ama burada anında bilgilendirme.
            error(
                "Oturum bilgileri eksik veya bozuk (orgId=$orgId, lecturerId=$lecturerId). " +
                "Lütfen sağ alttan Çıkış Yap ve tekrar giriş yapın."
            )
        }

        val scheduleRepo  = ScheduleRepository()
        val settingsRepo  = OrgSettingsRepository()
        val entriesAsync  = async { scheduleRepo.getEntriesForLecturer(lecturerId, orgId) }
        val settingsAsync = async {
            runCatching { settingsRepo.getSettings(orgId) }.getOrNull() ?: OrgSettings(orgId = orgId)
        }
        entriesAsync.await() to settingsAsync.await()
    }

    private suspend fun renderPdfBytes(title: String): ByteArray =
        withContext(Dispatchers.IO) {
            val (entries, settings) = loadExportInputs()
            ByteArrayOutputStream().use { buf ->
                PdfExporter.exportSchedule(
                    out = buf,
                    title = title,
                    entries = entries,
                    settings = settings
                )
                buf.toByteArray()
            }
        }

    private suspend fun renderIcsBytes(): ByteArray =
        withContext(Dispatchers.IO) {
            val (entries, _) = loadExportInputs()
            val calendarName = getString(R.string.export_ics_calendar_name, currentLecturerLabel())
            IcsExporter.export(entries, calendarName).toByteArray(Charsets.UTF_8)
        }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
