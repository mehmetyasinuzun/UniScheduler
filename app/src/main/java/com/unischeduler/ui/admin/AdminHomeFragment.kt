// Admin dashboard — shows 3 real-time summary panels.
// Retry button on error (Task 3 pattern). Parallel load (Task 5 pattern).
package com.unischeduler.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.unischeduler.databinding.FragmentAdminHomeBinding
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow

class AdminHomeFragment : Fragment() {

    private var _binding: FragmentAdminHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminHomeViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Show logged-in user and org info so user can verify correct account
        val session = com.unischeduler.util.SessionManager(requireContext())
        android.util.Log.d("AdminHome", "Session: user=${session.username}, orgId=${session.orgId}, role=${session.role}")
        (requireActivity() as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.subtitle =
            "${session.username} • Org #${session.orgId}"

        binding.rvUnassignedLecturers.layoutManager  = LinearLayoutManager(requireContext())
        binding.rvUnassignedCourses.layoutManager    = LinearLayoutManager(requireContext())
        binding.rvAvailableClassrooms.layoutManager  = LinearLayoutManager(requireContext())

        binding.btnRetry.setOnClickListener { viewModel.loadDashboard() }

        collectFlow(viewModel.state) { state ->
            when (state) {
                is UiState.Idle    -> viewModel.loadDashboard()
                is UiState.Loading -> showLoading(true)
                is UiState.Error   -> showError(state.message, state.retryable)
                is UiState.Success -> {
                    showLoading(false)
                    val d = state.data
                    binding.tvLecturerPanelTitle.text = "Atanmamış Hocalar (${d.unassignedLecturers.size})"
                    binding.rvUnassignedLecturers.adapter = SimpleTextAdapter(
                        d.unassignedLecturers.map { "${it.fullName} • ${it.departmentName}" }
                    )
                    binding.tvCoursePanelTitle.text = "Ders Açılmamış Dersler (${d.unassignedCourses.size})"
                    binding.rvUnassignedCourses.adapter = SimpleTextAdapter(
                        d.unassignedCourses.map { "${it.code} — ${it.name}" }
                    )
                    binding.tvClassroomPanelTitle.text = "Mevcut Derslikler (${d.classrooms.size})"
                    binding.rvAvailableClassrooms.adapter = SimpleTextAdapter(
                        d.classrooms.map { "${it.roomCode} (kap. ${it.capacity})" }
                    )
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

// Minimal single-line text adapter shared across panels
class SimpleTextAdapter(private val items: List<String>) :
    RecyclerView.Adapter<SimpleTextAdapter.VH>() {

    inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(TextView(parent.context).apply { setPadding(0, 8, 0, 8) })

    // Always return at least 1 so the "empty" row is rendered
    override fun getItemCount() = maxOf(items.size, 1)

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = if (items.isEmpty()) "— Yok —" else items[position]
    }
}
