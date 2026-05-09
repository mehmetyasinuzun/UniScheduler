// OnboardingActivity — 4-page intro shown once, on first launch.
//
// We use ViewPager2 + a hand-rolled RecyclerView.Adapter (no extra
// dependency for indicator dots — four <View>s tinted on/off in
// onPageSelected). Total weight: ~3 KB of code, no Firebase, no
// Lottie, no third-party indicator library.
//
// Trigger: MainActivity decides whether to launch this on the FIRST
// app launch (BuildConfig.VERSION_CODE first time) before the login
// screen. After completion the flag is persisted to app_prefs so we
// never show it again — even on app updates.
package com.unischeduler.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.unischeduler.App
import com.unischeduler.R
import com.unischeduler.databinding.ActivityOnboardingBinding
import com.unischeduler.databinding.ItemOnboardingPageBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private data class Page(val drawableRes: Int, val titleRes: Int, val bodyRes: Int)

    private val pages = listOf(
        Page(R.drawable.ic_empty_lecturers, R.string.onboarding_p1_title, R.string.onboarding_p1_body),
        Page(R.drawable.ic_empty_classrooms, R.string.onboarding_p2_title, R.string.onboarding_p2_body),
        Page(R.drawable.ic_empty_courses,    R.string.onboarding_p3_title, R.string.onboarding_p3_body),
        Page(R.drawable.ic_empty_schedule,   R.string.onboarding_p4_title, R.string.onboarding_p4_body)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pager.adapter = PageAdapter(pages)
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateForPage(position)
        })
        updateForPage(0)

        binding.btnSkip.setOnClickListener { finishOnboarding() }
        binding.btnNext.setOnClickListener {
            val current = binding.pager.currentItem
            if (current == pages.lastIndex) finishOnboarding()
            else binding.pager.setCurrentItem(current + 1, true)
        }
    }

    private fun updateForPage(position: Int) {
        // Last page → "Get Started" instead of "Next"
        binding.btnNext.setText(
            if (position == pages.lastIndex) R.string.onboarding_finish
            else R.string.onboarding_next
        )
        // Skip is hidden on the last page (the next button finishes anyway)
        binding.btnSkip.visibility = if (position == pages.lastIndex)
            android.view.View.INVISIBLE else android.view.View.VISIBLE

        val active   = ContextCompat.getColor(this, android.R.color.holo_blue_dark)
        val inactive = ContextCompat.getColor(this, android.R.color.darker_gray)
        listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)
            .forEachIndexed { i, dot ->
                dot.setBackgroundColor(if (i == position) active else inactive)
            }
    }

    private fun finishOnboarding() {
        getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_DONE, true)
            .apply()
        setResult(RESULT_OK)
        finish()
    }

    private inner class PageAdapter(private val items: List<Page>) :
        RecyclerView.Adapter<PageAdapter.VH>() {

        inner class VH(val b: ItemOnboardingPageBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            ItemOnboardingPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = items[position]
            holder.b.imageOnboarding.setImageResource(p.drawableRes)
            holder.b.tvOnboardingTitle.setText(p.titleRes)
            holder.b.tvOnboardingBody.setText(p.bodyRes)
        }

        override fun getItemCount() = items.size
    }

    companion object {
        const val KEY_ONBOARDING_DONE = "onboarding_done_v1"

        /** True if the user hasn't completed onboarding yet. */
        fun isPending(context: Context): Boolean =
            !context.getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ONBOARDING_DONE, false)

        fun start(context: Context) {
            context.startActivity(Intent(context, OnboardingActivity::class.java))
        }
    }
}
