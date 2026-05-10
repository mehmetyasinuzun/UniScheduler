package com.unischeduler.ui.admin

import android.app.Application
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.unischeduler.R
import com.unischeduler.data.model.OrgSettings
import com.unischeduler.data.model.ScheduleEntry
import com.unischeduler.data.repository.AvailabilityRepository
import com.unischeduler.data.repository.OrgSettingsRepository
import com.unischeduler.data.repository.ScheduleRepository
import com.unischeduler.databinding.SheetLecturerScheduleBinding
import com.unischeduler.ui.shared.ScheduleViewConfig
import com.unischeduler.util.ErrorMessages
import com.unischeduler.util.SessionManager
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Admin'in mobilden tek tıkla bir hocanın haftalık programını
 * görmesi için BottomSheet. Liste görünümünden çıkmadan inceleme
 * yapsın diye sheet olarak sunuldu — uzun navigation'a gerek
 * kalmıyor; sheet kapanınca admin yine listede.
 */
class LecturerScheduleSheet : BottomSheetDialogFragment() {

    private var _binding: SheetLecturerScheduleBinding? = null
    private val binding get() = _binding!!

    private val args by lazy {
        requireArguments().let {
            Args(
                lecturerId = it.getInt(ARG_LECTURER_ID),
                lecturerName = it.getString(ARG_LECTURER_NAME).orEmpty()
            )
        }
    }

    private data class Args(val lecturerId: Int, val lecturerName: String)

    data class SheetData(
        val entries: List<ScheduleEntry>,
        val settings: OrgSettings
    )

    class SheetViewModel(app: Application) : AndroidViewModel(app) {
        private val scheduleRepo = ScheduleRepository()
        private val availabilityRepo = AvailabilityRepository()
        private val orgSettingsRepo = OrgSettingsRepository()
        private val session = SessionManager(app)

        private val _state = MutableStateFlow<UiState<SheetData>>(UiState.Idle)
        val state: StateFlow<UiState<SheetData>> = _state

        fun load(lecturerId: Int) {
            viewModelScope.launch {
                _state.value = UiState.Loading
                runCatching {
                    withContext(Dispatchers.IO) {
                        coroutineScope {
                            val orgId = session.orgId
                            val schedD    = async { scheduleRepo.getEntriesForLecturer(lecturerId, orgId) }
                            val availD    = async { availabilityRepo.getForLecturer(lecturerId, orgId) }
                            val settingsD = async { orgSettingsRepo.getSettings(orgId) }

                            val realEntries = schedD.await()
                            val busy = availD.await()
                            val settings = settingsD.await()

                            // Hocanın "müsait değilim" işaretlediği bloklar için
                            // sentetik ScheduleEntry üret. WeeklyScheduleView tek
                            // tip kabul ediyor, böylece atanmış dersler + meşgul
                            // bloklar tek haftalık takvimde görünüyor — admin tek
                            // bakışta "boş mu, dersli mi, manuel kapatılmış mı"
                            // sorusunu yanıtlayabilsin diye birleştirildi.
                            // Negatif id + offeringId=0 sayesinde
                            // WeeklyScheduleView synthetic'leri gri renkle çizer.
                            val syntheticBusy = busy.mapIndexed { idx, b ->
                                ScheduleEntry(
                                    id = -(idx + 1),
                                    orgId = orgId,
                                    offeringId = 0,
                                    lecturerId = lecturerId,
                                    classroomId = 0,
                                    day = b.day,
                                    startTime = b.startTime,
                                    endTime = b.endTime
                                )
                            }
                            SheetData(realEntries + syntheticBusy, settings)
                        }
                    }
                }.onSuccess { _state.value = UiState.Success(it) }
                 .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.value = UiState.Error(ErrorMessages.map(e), retryable = true)
                }
            }
        }
    }

    private val viewModel: SheetViewModel by viewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val parent = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            val behavior = BottomSheetBehavior.from(parent)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            parent.layoutParams.height = (resources.displayMetrics.heightPixels * 0.85).toInt()
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetLecturerScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvSheetTitle.text = getString(R.string.lecturer_schedule_title, args.lecturerName)
        // PDF butonu sheet'te şimdilik gizli — admin AdminCalendar'da
        // tam filtre + dosya adı seçimiyle daha iyi PDF üretiyor.
        binding.btnSheetExport.visibility = View.GONE

        collectFlow(viewModel.state) { state ->
            when (state) {
                is UiState.Idle -> viewModel.load(args.lecturerId)
                is UiState.Loading -> {
                    binding.progressSheet.visibility = View.VISIBLE
                    binding.tvSheetEmpty.visibility = View.GONE
                    binding.tvSheetError.visibility = View.GONE
                }
                is UiState.Error -> {
                    binding.progressSheet.visibility = View.GONE
                    binding.tvSheetError.text = state.message
                    binding.tvSheetError.visibility = View.VISIBLE
                }
                is UiState.Success -> {
                    binding.progressSheet.visibility = View.GONE
                    binding.tvSheetError.visibility = View.GONE
                    val (entries, settings) = state.data
                    binding.sheetWeeklySchedule.setConfig(
                        ScheduleViewConfig(
                            dayStart = settings.dayStart,
                            dayEnd = settings.dayEnd,
                            timeStepMinutes = settings.timeStepMinutes.coerceAtLeast(30),
                            activeDays = settings.activeDays.ifEmpty {
                                listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
                            }
                        )
                    )
                    if (entries.isEmpty()) {
                        binding.tvSheetEmpty.visibility = View.VISIBLE
                        binding.sheetWeeklySchedule.setEntries(emptyList())
                    } else {
                        binding.tvSheetEmpty.visibility = View.GONE
                        binding.sheetWeeklySchedule.setEntries(entries)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_LECTURER_ID   = "lecturer_id"
        private const val ARG_LECTURER_NAME = "lecturer_name"

        fun newInstance(lecturerId: Int, lecturerName: String): LecturerScheduleSheet {
            return LecturerScheduleSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_LECTURER_ID, lecturerId)
                    putString(ARG_LECTURER_NAME, lecturerName)
                }
            }
        }
    }
}
