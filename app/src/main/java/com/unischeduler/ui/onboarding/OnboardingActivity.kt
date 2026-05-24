// OnboardingActivity — login ÖNCESİ 3-sayfa genel hoş geldin akışı.
//
// (Mevcut sürüm 4 sayfalık admin-specific intro idi; admin akışı yeni
// AdminTutorialActivity'ye taşındı — login sonrası rol bazlı tetiklenir.)
//
// Bu activity sadece app-level "ne sunar" tanıtımı yapar. ViewPager2 +
// el yapımı RecyclerView.Adapter — ekstra dependency yok.
//
// Trigger: MainActivity.onCreate sadece ilk app açılışında çağırır
// (TutorialPrefs.welcomeDone flag'i ile bir kez gösterilir).
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

    // 3 sayfa: app tanıtımı + 2 rol vurgusu + "Hadi başla".
    // T5'te custom telefon-mockup illustration'ları gelecek; şimdilik mevcut
    // empty-state vector drawable'ları kullanılıyor (placeholder).
    private val pages = listOf(
        Page(R.drawable.ic_intro_welcome,    R.string.onboarding_p1_title, R.string.onboarding_p1_body),
        Page(R.drawable.ic_intro_two_roles,  R.string.onboarding_p2_title, R.string.onboarding_p2_body),
        Page(R.drawable.ic_intro_signin,     R.string.onboarding_p3_title, R.string.onboarding_p3_body)
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
        // 3-dot indicator (welcome 3-page intro). Layout'ta dot4 kaldırıldı.
        listOf(binding.dot1, binding.dot2, binding.dot3)
            .forEachIndexed { i, dot ->
                dot.setBackgroundColor(if (i == position) active else inactive)
            }
    }

    private fun finishOnboarding() {
        // Hem eski hem yeni flag — geriye dönük uyumluluk için her ikisi de
        // set ediliyor. MainActivity.isPending() yine eski flag'i okuyor.
        getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_DONE, true)
            .apply()
        com.unischeduler.util.TutorialPrefs(this).welcomeDone = true
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
