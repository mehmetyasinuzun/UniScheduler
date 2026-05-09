package com.unischeduler.ui.admin

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.os.LocaleListCompat
import com.unischeduler.App
import com.unischeduler.BuildConfig
import com.unischeduler.MainActivity
import com.unischeduler.R
import com.unischeduler.data.model.Department
import com.unischeduler.databinding.FragmentSettingsBinding
import com.unischeduler.databinding.ItemDepartmentBinding
import com.unischeduler.util.BackupManager
import com.unischeduler.util.ExcelHelper
import com.unischeduler.util.SessionManager
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow
import com.unischeduler.util.writeBytesToUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private enum class TemplateKind { COURSES, LECTURERS, CLASSROOMS }

    /** Single Json instance — Kotlin lint warns on per-call construction
     *  because the parser caches descriptors per Json instance. */
    private companion object {
        val laxJson = Json { ignoreUnknownKeys = true }
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()
    private var isFirstResume = true

    private lateinit var departmentAdapter: DepartmentAdapter

    // Pending template kind, captured at click time so the SAF callback knows
    // which exporter to invoke once the user picks the destination URI.
    private var pendingTemplateKind: TemplateKind? = null

    private lateinit var templateSaveLauncher: ActivityResultLauncher<String>
    private lateinit var backupSaveLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        templateSaveLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
        ) { uri ->
            uri ?: return@registerForActivityResult
            val kind = pendingTemplateKind ?: return@registerForActivityResult
            pendingTemplateKind = null
            writeBytesToUri(
                destination = uri,
                produce = { renderTemplateBytes(kind) }
            )
        }
        backupSaveLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            uri ?: return@registerForActivityResult
            val ctx = requireContext().applicationContext
            val orgId = SessionManager(ctx).orgId
            if (orgId <= 0) {
                Toast.makeText(ctx, R.string.export_failed, Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            writeBytesToUri(
                destination = uri,
                produce = {
                    withContext(Dispatchers.IO) {
                        BackupManager.createBackup(orgId, BuildConfig.VERSION_NAME)
                            .toByteArray(Charsets.UTF_8)
                    }
                },
                onDone = { _ ->
                    // Surface a summary so the user knows what's inside.
                    showBackupDoneDialog(uri)
                }
            )
        }
    }

    private fun renderTemplateBytes(kind: TemplateKind): ByteArray =
        ByteArrayOutputStream().use { buf ->
            when (kind) {
                TemplateKind.COURSES    -> ExcelHelper.exportCourses(emptyList(), buf)
                TemplateKind.LECTURERS  -> ExcelHelper.exportLecturers(emptyList(), buf)
                TemplateKind.CLASSROOMS -> ExcelHelper.exportClassrooms(emptyList(), buf)
            }
            buf.toByteArray()
        }

    private fun showBackupDoneDialog(uri: android.net.Uri) {
        // Re-read the saved file to compute a quick summary. Cheap because
        // we just wrote it; the OS page cache still has it hot. Bound to
        // viewLifecycleOwner so dialog can't leak past onDestroyView.
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val summary = runCatching {
                withContext(Dispatchers.IO) {
                    val text = ctx.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                        ?: return@withContext "—"
                    val backup = laxJson.decodeFromString(BackupManager.Backup.serializer(), text)
                    BackupManager.summarise(backup)
                }
            }.getOrDefault("—")
            if (!isAdded) return@launch
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_backup_done_title)
                .setMessage(getString(R.string.settings_backup_done_message, summary))
                .setPositiveButton(R.string.common_ok, null)
                .show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvDepartments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDepartments.isNestedScrollingEnabled = false
        departmentAdapter = DepartmentAdapter(
            onEditClick   = { showEditDialog(it) },
            onDeleteClick = { showDeleteDialog(it) }
        )
        binding.rvDepartments.adapter = departmentAdapter
        binding.btnRetry.setOnClickListener { viewModel.loadDepartments() }
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadDepartments() }

        binding.btnAddDept.setOnClickListener {
            viewModel.addDepartment(binding.etDeptName.text?.toString().orEmpty())
        }

        setupThemeSelector()
        setupLanguageSelector()

        // ── Excel templates (B6) ──
        binding.btnTemplateCourses.setOnClickListener {
            pendingTemplateKind = TemplateKind.COURSES
            templateSaveLauncher.launch("courses-template.xlsx")
        }
        binding.btnTemplateLecturers.setOnClickListener {
            pendingTemplateKind = TemplateKind.LECTURERS
            templateSaveLauncher.launch("lecturers-template.xlsx")
        }
        binding.btnTemplateClassrooms.setOnClickListener {
            pendingTemplateKind = TemplateKind.CLASSROOMS
            templateSaveLauncher.launch("classrooms-template.xlsx")
        }

        // ── Backup (B7) ──
        binding.btnBackup.setOnClickListener {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val filename = getString(R.string.settings_backup_default_filename, today)
            backupSaveLauncher.launch(filename)
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
                is UiState.Idle    -> viewModel.loadDepartments()
                is UiState.Loading -> showLoading(true)
                is UiState.Error   -> {
                    binding.swipeRefresh.isRefreshing = false
                    showError(state.message, state.retryable)
                }
                is UiState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    showLoading(false)
                    departmentAdapter.submitList(state.data)
                }
            }
        }

        collectFlow(viewModel.addState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> binding.btnAddDept.isEnabled = false
                is UiState.Error   -> {
                    binding.btnAddDept.isEnabled    = true
                    binding.tvDeptAddError.text     = state.message
                    binding.tvDeptAddError.visibility = View.VISIBLE
                }
                is UiState.Success -> {
                    binding.btnAddDept.isEnabled      = true
                    binding.tvDeptAddError.visibility = View.GONE
                    binding.etDeptName.text?.clear()
                    Toast.makeText(requireContext(), getString(R.string.settings_dept_added), Toast.LENGTH_SHORT).show()
                    viewModel.resetAddState()
                }
            }
        }

        collectFlow(viewModel.editState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> Unit
                is UiState.Error   -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetEditState()
                }
                is UiState.Success -> {
                    Toast.makeText(requireContext(), getString(R.string.settings_dept_updated), Toast.LENGTH_SHORT).show()
                    viewModel.resetEditState()
                }
            }
        }

        collectFlow(viewModel.deleteState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> Unit
                is UiState.Error   -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetDeleteState()
                }
                is UiState.Success -> {
                    Toast.makeText(requireContext(), getString(R.string.settings_dept_deleted), Toast.LENGTH_SHORT).show()
                    viewModel.resetDeleteState()
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

    private fun showEditDialog(dept: Department) {
        val input = EditText(requireContext()).apply {
            setText(dept.name)
            setPadding(48, 32, 48, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_dept_edit_title))
            .setView(input)
            .setPositiveButton(getString(R.string.common_save)) { _, _ ->
                viewModel.editDepartment(dept.id, input.text.toString())
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showDeleteDialog(dept: Department) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_dept_delete_title))
            .setMessage(getString(R.string.settings_dept_delete_message, dept.name))
            .setPositiveButton(getString(R.string.common_delete)) { _, _ ->
                com.unischeduler.util.PendingDelete.schedule(
                    anchor = binding.root,
                    owner = viewLifecycleOwner,
                    message = getString(R.string.ux_undo_deleted_department, dept.name),
                    onUndo = { viewModel.loadDepartments() },
                    performDelete = { viewModel.deleteDepartment(dept.id) }
                )
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
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

    override fun onResume() {
        super.onResume()
        if (isFirstResume) { isFirstResume = false; return }
        viewModel.loadDepartments()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class DepartmentAdapter(
    private val onEditClick: (Department) -> Unit,
    private val onDeleteClick: (Department) -> Unit
) : ListAdapter<Department, DepartmentAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemDepartmentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemDepartmentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val d = getItem(position)
        holder.binding.tvDeptName.text = d.name
        holder.binding.btnEditDept.visibility = View.VISIBLE
        holder.binding.btnDeleteDept.visibility = View.VISIBLE
        holder.binding.btnEditDept.setOnClickListener { onEditClick(d) }
        holder.binding.btnDeleteDept.setOnClickListener { onDeleteClick(d) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Department>() {
            override fun areItemsTheSame(old: Department, new: Department) = old.id == new.id
            override fun areContentsTheSame(old: Department, new: Department) = old == new
        }
    }
}
