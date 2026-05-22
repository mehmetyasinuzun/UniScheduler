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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.unischeduler.R
import com.unischeduler.databinding.FragmentAdminHomeBinding
import com.unischeduler.util.UiState
import com.unischeduler.util.collectFlow

class AdminHomeFragment : Fragment() {

    private var _binding: FragmentAdminHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminHomeViewModel by viewModels()

    private lateinit var lecturersAdapter: SimpleTextAdapter
    private lateinit var coursesAdapter: SimpleTextAdapter
    private lateinit var classroomsAdapter: SimpleTextAdapter

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

        binding.rvUnassignedLecturers.isNestedScrollingEnabled  = false
        binding.rvUnassignedCourses.isNestedScrollingEnabled    = false
        binding.rvAvailableClassrooms.isNestedScrollingEnabled  = false

        // wrap_content + ScrollView içindeyiz; setHasFixedSize false bırakmak gerek
        // (her item bind sonrası tekrar measure). Ama view cache'i artırmak GC
        // baskısını azaltıyor.
        binding.rvUnassignedLecturers.setItemViewCacheSize(20)
        binding.rvUnassignedCourses.setItemViewCacheSize(20)
        binding.rvAvailableClassrooms.setItemViewCacheSize(20)

        lecturersAdapter   = SimpleTextAdapter()
        coursesAdapter     = SimpleTextAdapter()
        classroomsAdapter  = SimpleTextAdapter()

        binding.rvUnassignedLecturers.adapter  = lecturersAdapter
        binding.rvUnassignedCourses.adapter    = coursesAdapter
        binding.rvAvailableClassrooms.adapter  = classroomsAdapter

        binding.btnRetry.setOnClickListener { viewModel.loadDashboard() }
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadDashboard() }

        collectFlow(viewModel.state) { state ->
            when (state) {
                is UiState.Idle    -> viewModel.loadDashboard()
                is UiState.Loading -> showLoading(true)
                is UiState.Error   -> {
                    binding.swipeRefresh.isRefreshing = false
                    showError(state.message, state.retryable)
                    // Stale data ile hata mesajının birlikte görünmesini engelle
                    lecturersAdapter.submitList(emptyList())
                    coursesAdapter.submitList(emptyList())
                    classroomsAdapter.submitList(emptyList())
                }
                is UiState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    showLoading(false)
                    val d = state.data

                    binding.tvLecturerPanelTitle.text = getString(
                        R.string.admin_home_panel_title_with_count,
                        getString(R.string.admin_home_unassigned_lecturers),
                        d.unassignedLecturers.size
                    )
                    lecturersAdapter.submitList(d.unassignedLecturers.map {
                        getString(R.string.admin_home_lecturer_item, it.fullName, it.departmentName)
                    })

                    binding.tvCoursePanelTitle.text = getString(
                        R.string.admin_home_panel_title_with_count,
                        getString(R.string.admin_home_unassigned_courses),
                        d.unassignedCourses.size
                    )
                    coursesAdapter.submitList(d.unassignedCourses.map {
                        getString(R.string.admin_home_course_item, it.code, it.name)
                    })

                    binding.tvClassroomPanelTitle.text = getString(
                        R.string.admin_home_panel_title_with_count,
                        getString(R.string.admin_home_available_classrooms),
                        d.classrooms.size
                    )
                    classroomsAdapter.submitList(d.classrooms.map {
                        getString(R.string.admin_home_classroom_item, it.roomCode, it.capacity)
                    })
                }
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        // SwipeRefresh kendi spinner'ını gösteriyorsa ProgressBar'ı çift göstermeyelim.
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

    private var isFirstResume = true
    override fun onResume() {
        super.onResume()
        if (isFirstResume) { isFirstResume = false; return }
        viewModel.loadDashboard()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class SimpleTextAdapter : ListAdapter<String, SimpleTextAdapter.VH>(DIFF) {

    inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(TextView(parent.context).apply { setPadding(0, 8, 0, 8) })

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = getItem(position)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(old: String, new: String) = old == new
            override fun areContentsTheSame(old: String, new: String) = old == new
        }
    }
}
