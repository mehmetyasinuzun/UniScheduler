package com.unischeduler.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.unischeduler.data.model.Classroom
import com.unischeduler.data.model.Lecturer
import com.unischeduler.data.model.Offering
import com.unischeduler.data.model.ScheduleEntry
import com.unischeduler.R
import com.unischeduler.databinding.FragmentAssignmentBinding
import com.unischeduler.databinding.ItemScheduleEntryBinding
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow

class AssignmentFragment : Fragment() {

    private var _binding: FragmentAssignmentBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AssignmentViewModel by viewModels()

    private var offerings: List<Offering>   = emptyList()
    private var lecturers: List<Lecturer>   = emptyList()
    private var classrooms: List<Classroom> = emptyList()
    private var isFirstResume = true

    private lateinit var scheduleEntryAdapter: ScheduleEntryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAssignmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvEntries.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEntries.isNestedScrollingEnabled = false
        scheduleEntryAdapter = ScheduleEntryAdapter { showDeleteConfirmation(it) }
        binding.rvEntries.adapter = scheduleEntryAdapter
        binding.btnRetry.setOnClickListener { viewModel.loadForm() }
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadForm() }
        binding.btnAssign.setOnClickListener { onAssignClicked(force = false) }
        binding.btnAutoSchedule.setOnClickListener {
            findNavController().navigate(R.id.action_assignment_to_autoSchedule)
        }

        binding.etStartTime.setOnClickListener {
            showTimePicker { binding.etStartTime.setText(it) }
        }
        binding.etEndTime.setOnClickListener {
            showTimePicker { binding.etEndTime.setText(it) }
        }

        collectFlow(viewModel.formState) { state ->
            when (state) {
                is UiState.Idle    -> viewModel.loadForm()
                is UiState.Loading -> showLoading(true)
                is UiState.Error   -> {
                    binding.swipeRefresh.isRefreshing = false
                    showError(state.message, state.retryable)
                }
                is UiState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    showLoading(false)
                    populateForm(state.data)
                }
            }
        }

        collectFlow(viewModel.saveState) { state ->
            when (state) {
                is UiState.Idle    -> Unit
                is UiState.Loading -> binding.btnAssign.isEnabled = false
                is UiState.Error   -> {
                    binding.btnAssign.isEnabled    = true
                    binding.tvSaveError.text       = state.message
                    binding.tvSaveError.visibility = View.VISIBLE
                }
                is UiState.Success -> {
                    binding.btnAssign.isEnabled    = true
                    binding.tvSaveError.visibility = View.GONE
                    Toast.makeText(requireContext(), getString(R.string.assignment_success), Toast.LENGTH_SHORT).show()
                    viewModel.resetSaveState()
                }
            }
        }

        collectFlow(viewModel.warningState) { warnings ->
            if (warnings != null && warnings.hasIssues) {
                showWarningDialog(warnings)
            }
        }
    }

    private fun populateForm(data: AssignmentFormData) {
        offerings  = data.offerings
        lecturers  = data.lecturers
        classrooms = data.classrooms

        val offeringNames  = offerings.map { it.displayName }
        val lecturerNames  = lecturers.map { it.fullName }
        val classroomNames = classrooms.map { it.roomCode }

        binding.actvOffering.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, offeringNames))
        binding.actvLecturer.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, lecturerNames))
        binding.actvClassroom.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, classroomNames))
        binding.spinnerDay.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, data.days)

        binding.actvOffering.threshold = 1
        binding.actvLecturer.threshold = 1
        binding.actvClassroom.threshold = 1

        binding.actvOffering.setOnClickListener { binding.actvOffering.showDropDown() }
        binding.actvLecturer.setOnClickListener { binding.actvLecturer.showDropDown() }
        binding.actvClassroom.setOnClickListener { binding.actvClassroom.showDropDown() }
        binding.actvOffering.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) binding.actvOffering.showDropDown() }
        binding.actvLecturer.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) binding.actvLecturer.showDropDown() }
        binding.actvClassroom.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) binding.actvClassroom.showDropDown() }

        binding.actvOffering.setOnItemClickListener { _, _, pos, _ ->
            if (pos in offerings.indices) {
                val offering = offerings[pos]
                val defaultLecturer = offering.lecturers
                if (defaultLecturer != null) {
                    binding.actvLecturer.setText(defaultLecturer.fullName, false)
                }
            }
        }

        scheduleEntryAdapter.submitList(data.entries)
    }

    private fun onAssignClicked(force: Boolean) {
        if (offerings.isEmpty() || classrooms.isEmpty()) return

        val offeringText  = binding.actvOffering.text.toString()
        val lecturerText  = binding.actvLecturer.text.toString().trim()
        val classroomText = binding.actvClassroom.text.toString()

        val offeringIdx  = offerings.indexOfFirst { it.displayName == offeringText }
        val classroomIdx = classrooms.indexOfFirst { it.roomCode == classroomText }

        if (offeringIdx < 0 || classroomIdx < 0) {
            binding.tvSaveError.text = getString(R.string.assignment_invalid_selection)
            binding.tvSaveError.visibility = View.VISIBLE
            return
        }

        val lecturerIdx = lecturers.indexOfFirst { it.fullName == lecturerText }
        val selectedLecturerId: Int? = if (lecturerIdx >= 0) lecturers[lecturerIdx].id else null

        viewModel.assign(
            offeringId = offerings[offeringIdx].id,
            lecturerId = selectedLecturerId,
            classroomId = classrooms[classroomIdx].id,
            day = binding.spinnerDay.selectedItem.toString(),
            startTime = binding.etStartTime.text?.toString().orEmpty(),
            endTime = binding.etEndTime.text?.toString().orEmpty(),
            force = force
        )
    }

    private fun showDeleteConfirmation(entry: ScheduleEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.assignment_delete_title))
            .setMessage(getString(R.string.assignment_delete_message, entry.courseCode, entry.day, entry.timeRange))
            .setPositiveButton(getString(R.string.common_delete)) { _, _ -> viewModel.deleteEntry(entry.id) }
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

    private fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(9)
            .setMinute(0)
            .setTitleText(getString(R.string.common_select_time))
            .build()

        picker.addOnPositiveButtonClickListener {
            val time = String.format("%02d:%02d", picker.hour, picker.minute)
            onTimeSelected(time)
        }

        picker.show(parentFragmentManager, "timePicker")
    }

    private fun showWarningDialog(warnings: AssignmentWarnings) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_conflict, null)

        val layoutAvail = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutAvailability)
        val tvAvailMsg = dialogView.findViewById<android.widget.TextView>(R.id.tvAvailabilityMessage)
        val layoutLecturer = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutLecturerConflicts)
        val containerLecturer = dialogView.findViewById<android.widget.LinearLayout>(R.id.containerLecturerConflicts)
        val layoutClassroom = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutClassroomConflicts)
        val containerClassroom = dialogView.findViewById<android.widget.LinearLayout>(R.id.containerClassroomConflicts)

        if (warnings.availabilityWarning != null) {
            layoutAvail.visibility = View.VISIBLE
            tvAvailMsg.text = warnings.availabilityWarning
        }

        val conflicts = warnings.conflicts
        if (conflicts != null) {
            if (conflicts.lecturerConflicts.isNotEmpty()) {
                layoutLecturer.visibility = View.VISIBLE
                conflicts.lecturerConflicts.forEach { entry ->
                    containerLecturer.addView(buildConflictEntryView(entry))
                }
            }
            if (conflicts.classroomConflicts.isNotEmpty()) {
                layoutClassroom.visibility = View.VISIBLE
                conflicts.classroomConflicts.forEach { entry ->
                    containerClassroom.addView(buildConflictEntryView(entry))
                }
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.assignment_conflict_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.assignment_force_assign)) { dialog, _ ->
                viewModel.clearWarnings()
                onAssignClicked(force = true)
            }
            .setNegativeButton(getString(R.string.common_cancel)) { dialog, _ -> viewModel.clearWarnings() }
            .show()
    }

    private fun buildConflictEntryView(entry: ScheduleEntry): View {
        val ctx = requireContext()
        return android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)

            addView(android.widget.TextView(ctx).apply {
                text = "${entry.courseCode} — ${entry.courseName}"
                setTypeface(null, android.graphics.Typeface.BOLD)
                textSize = 13f
            })
            addView(android.widget.TextView(ctx).apply {
                text = "Hoca: ${entry.lecturerName}"
                textSize = 12f
            })
            addView(android.widget.TextView(ctx).apply {
                text = getString(R.string.assignment_classroom_short, entry.classroomCode)
                textSize = 12f
            })
            addView(android.widget.TextView(ctx).apply {
                text = "${entry.day} ${entry.timeRange}"
                textSize = 12f
                setTextColor(android.graphics.Color.parseColor("#D32F2F"))
            })
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFirstResume) { isFirstResume = false; return }
        viewModel.loadForm()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class ScheduleEntryAdapter(
    private val onDeleteClick: (ScheduleEntry) -> Unit
) : ListAdapter<ScheduleEntry, ScheduleEntryAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemScheduleEntryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemScheduleEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = getItem(position)
        holder.binding.tvCourse.text        = "${e.courseCode} — ${e.courseName}"
        holder.binding.tvLecturer.text      = e.lecturerName
        holder.binding.tvSlot.text          = "${e.day} ${e.timeRange} • ${e.classroomCode}"
        holder.binding.btnDelete.visibility = View.VISIBLE
        holder.binding.btnDelete.setOnClickListener { onDeleteClick(e) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ScheduleEntry>() {
            override fun areItemsTheSame(old: ScheduleEntry, new: ScheduleEntry) = old.id == new.id
            override fun areContentsTheSame(old: ScheduleEntry, new: ScheduleEntry) = old == new
        }
    }
}
