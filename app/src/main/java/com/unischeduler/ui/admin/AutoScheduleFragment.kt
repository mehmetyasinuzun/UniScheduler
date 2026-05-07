package com.unischeduler.ui.admin

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.unischeduler.R
import com.unischeduler.databinding.FragmentAutoScheduleBinding
import com.unischeduler.scheduler.ProposedEntry
import com.unischeduler.scheduler.SchedulePreferences
import com.unischeduler.scheduler.ScheduleResult
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow

class AutoScheduleFragment : Fragment() {

    private var _binding: FragmentAutoScheduleBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AutoScheduleViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAutoScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.btnGenerate.setOnClickListener {
            syncPreferencesToViewModel()
            viewModel.generateSchedule()
        }
        binding.btnRegenerate.setOnClickListener {
            syncPreferencesToViewModel()
            viewModel.generateSchedule()
        }
        binding.btnRetry.setOnClickListener {
            syncPreferencesToViewModel()
            viewModel.generateSchedule()
        }

        binding.btnPrevAlt.setOnClickListener {
            val idx = viewModel.selectedIndex.value
            if (idx > 0) viewModel.selectAlternative(idx - 1)
        }
        binding.btnNextAlt.setOnClickListener {
            val idx = viewModel.selectedIndex.value
            val max = viewModel.alternatives.value.size - 1
            if (idx < max) viewModel.selectAlternative(idx + 1)
        }

        binding.btnApprove.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Programı Onayla")
                .setMessage("Oluşturulan program kaydedilecek. Devam etmek istiyor musunuz?")
                .setPositiveButton("Onayla ve Kaydet") { _, _ -> viewModel.saveSchedule() }
                .setNegativeButton("Vazgeç", null)
                .show()
        }

        setupSettingsListeners()

        collectFlow(viewModel.state) { state ->
            when (state) {
                is UiState.Idle -> showScreen("initial")
                is UiState.Loading -> showScreen("loading")
                is UiState.Error -> {
                    showScreen("error")
                    binding.tvError.text = state.message
                }
                is UiState.Success -> {
                    showScreen("result")
                    displayResult(state.data)
                }
            }
        }

        collectFlow(viewModel.saveState) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.btnApprove.isEnabled = false
                    binding.btnApprove.text = "Kaydediliyor..."
                }
                is UiState.Success -> {
                    Toast.makeText(requireContext(), "${state.data} ders başarıyla kaydedildi!", Toast.LENGTH_LONG).show()
                    findNavController().popBackStack()
                }
                is UiState.Error -> {
                    binding.btnApprove.isEnabled = true
                    binding.btnApprove.text = "Onayla ve Kaydet"
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
                else -> {
                    binding.btnApprove.isEnabled = true
                    binding.btnApprove.text = "Onayla ve Kaydet"
                }
            }
        }
    }

    private fun setupSettingsListeners() {
        binding.sliderMaxDaily.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            val v = value.toInt()
            binding.tvMaxDailyValue.text = if (v == 0) "Yok" else "$v"
        })

        binding.sliderAltCount.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            binding.tvAltCountValue.text = value.toInt().toString()
        })

        binding.etPrefStart.setOnClickListener { showTimePicker(true) }
        binding.etPrefEnd.setOnClickListener { showTimePicker(false) }

        binding.etPrefStart.setOnLongClickListener {
            binding.etPrefStart.setText("")
            true
        }
        binding.etPrefEnd.setOnLongClickListener {
            binding.etPrefEnd.setText("")
            true
        }
    }

    private fun showTimePicker(isStart: Boolean) {
        val current = if (isStart) binding.etPrefStart.text.toString() else binding.etPrefEnd.text.toString()
        val parts = current.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: if (isStart) 8 else 17
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        TimePickerDialog(requireContext(), { _, h, m ->
            val formatted = "%02d:%02d".format(h, m)
            if (isStart) binding.etPrefStart.setText(formatted)
            else binding.etPrefEnd.setText(formatted)
        }, hour, minute, true).show()
    }

    private fun syncPreferencesToViewModel() {
        val compactness = when (binding.rgCompactness.checkedRadioButtonId) {
            R.id.rbCompact -> SchedulePreferences.CompactnessMode.COMPACT
            R.id.rbSpread -> SchedulePreferences.CompactnessMode.SPREAD
            else -> SchedulePreferences.CompactnessMode.NONE
        }
        viewModel.updatePreferences(
            SchedulePreferences(
                studentCompactness = compactness,
                lecturerMaxDailySlots = binding.sliderMaxDaily.value.toInt(),
                dayBalancing = binding.switchDayBalance.isChecked,
                preferredStartTime = binding.etPrefStart.text.toString().trim(),
                preferredEndTime = binding.etPrefEnd.text.toString().trim(),
                alternativeCount = binding.sliderAltCount.value.toInt()
            )
        )
    }

    private fun showScreen(screen: String) {
        binding.layoutInitial.visibility = if (screen == "initial") View.VISIBLE else View.GONE
        binding.layoutLoading.visibility = if (screen == "loading") View.VISIBLE else View.GONE
        binding.layoutResult.visibility = if (screen == "result") View.VISIBLE else View.GONE
        binding.layoutError.visibility = if (screen == "error") View.VISIBLE else View.GONE
    }

    private fun displayResult(result: ScheduleResult) {
        val total = result.assigned.size + result.unassigned.size
        val pct = if (total > 0) (result.assigned.size * 100 / total) else 0

        binding.tvResultTitle.text = if (result.unassigned.isEmpty())
            "Program Hazır" else "Kısmi Program Oluşturuldu"

        binding.tvAssignedCount.text = "Yerleşen: ${result.assigned.size} ders"
        binding.tvUnassignedCount.text = "Yerleşemeyen: ${result.unassigned.size} ders"
        binding.tvScore.text = "Başarı: %$pct • Kalite puanı: ${result.score}/100"

        val alts = viewModel.alternatives.value
        if (alts.size > 1) {
            binding.cardAlternatives.visibility = View.VISIBLE
            val idx = viewModel.selectedIndex.value
            binding.tvAlternativeInfo.text = "Alternatif ${idx + 1} / ${alts.size}"
            binding.btnPrevAlt.isEnabled = idx > 0
            binding.btnNextAlt.isEnabled = idx < alts.size - 1
        } else {
            binding.cardAlternatives.visibility = View.GONE
        }

        if (result.failures.isNotEmpty()) {
            binding.cardUnassigned.visibility = View.VISIBLE
            binding.llUnassignedList.removeAllViews()
            result.failures.forEach { failure ->
                val titleTv = TextView(requireContext()).apply {
                    text = "• ${failure.offering.displayName}"
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(0xFFE65100.toInt())
                    setPadding(0, 8, 0, 2)
                }
                binding.llUnassignedList.addView(titleTv)

                failure.reasons.forEach { reason ->
                    val reasonTv = TextView(requireContext()).apply {
                        text = "  → $reason"
                        textSize = 12f
                        setTextColor(0xFF888888.toInt())
                        setPadding(16, 0, 0, 2)
                    }
                    binding.llUnassignedList.addView(reasonTv)
                }
            }
        } else {
            binding.cardUnassigned.visibility = View.GONE
        }

        binding.llProposedEntries.removeAllViews()

        val dayOrder = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val dayNames = mapOf(
            "Monday" to "Pazartesi", "Tuesday" to "Salı", "Wednesday" to "Çarşamba",
            "Thursday" to "Perşembe", "Friday" to "Cuma", "Saturday" to "Cumartesi", "Sunday" to "Pazar"
        )

        val grouped = result.assigned.groupBy { it.day }
            .toSortedMap(compareBy { dayOrder.indexOf(it) })

        for ((day, entries) in grouped) {
            val dayLabel = TextView(requireContext()).apply {
                text = dayNames[day] ?: day
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 24, 0, 8)
            }
            binding.llProposedEntries.addView(dayLabel)

            val sorted = entries.sortedBy { timeToMinutes(it.startTime) }
            for (entry in sorted) {
                binding.llProposedEntries.addView(buildEntryCard(entry))
            }
        }

        binding.btnApprove.isEnabled = result.assigned.isNotEmpty()
    }

    private fun buildEntryCard(entry: ProposedEntry): View {
        val card = MaterialCardView(requireContext()).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
            radius = 12f
            cardElevation = 2f
            setContentPadding(16, 12, 16, 12)
            strokeWidth = 1
            strokeColor = 0xFFE0E0E0.toInt()
        }

        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        val courseCode = entry.offering.courses?.code ?: ""
        val courseName = entry.offering.courses?.name ?: ""
        val lecturerName = entry.offering.lecturerName
        val roomCode = entry.classroom.roomCode
        val classInfo = "${entry.offering.classYear}. Sınıf ${entry.offering.section}"

        val pinLabel = if (viewModel.isPinned(entry)) " [SABİT]" else ""
        val tvTitle = TextView(requireContext()).apply {
            text = "$courseCode — $courseName$pinLabel"
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            if (viewModel.isPinned(entry)) setTextColor(0xFF2E7D32.toInt())
        }

        val tvDetails = TextView(requireContext()).apply {
            text = "${entry.startTime} - ${entry.endTime}  •  $roomCode  •  $lecturerName"
            textSize = 13f
            setTextColor(0xFF666666.toInt())
        }

        val tvClass = TextView(requireContext()).apply {
            text = classInfo
            textSize = 12f
            setTextColor(0xFF999999.toInt())
        }

        container.addView(tvTitle)
        container.addView(tvDetails)
        container.addView(tvClass)

        card.addView(container)

        val isPinned = viewModel.isPinned(entry)
        if (isPinned) {
            card.strokeColor = 0xFF2E7D32.toInt()
            card.strokeWidth = 3
        }

        card.setOnClickListener {
            val items = mutableListOf<String>()
            val actions = mutableListOf<() -> Unit>()

            if (viewModel.isPinned(entry)) {
                items.add("Sabitlemeyi Kaldır")
                actions.add { viewModel.unpinEntry(entry); displayResult(viewModel.state.value.let { (it as? UiState.Success)?.data } ?: return@add) }
            } else {
                items.add("Sabitle (Yeniden oluşturmada korunsun)")
                actions.add { viewModel.pinEntry(entry); displayResult(viewModel.state.value.let { (it as? UiState.Success)?.data } ?: return@add) }
            }
            items.add("Kaldır")
            actions.add { viewModel.removeEntry(entry) }

            AlertDialog.Builder(requireContext())
                .setTitle("$courseCode — ${entry.startTime}-${entry.endTime}")
                .setItems(items.toTypedArray()) { _, which -> actions[which]() }
                .setNegativeButton("Vazgeç", null)
                .show()
        }

        return card
    }

    private fun timeToMinutes(time: String): Int {
        val parts = time.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
               (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
