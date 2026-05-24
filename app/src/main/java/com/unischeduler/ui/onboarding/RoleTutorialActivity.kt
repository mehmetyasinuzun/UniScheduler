// RoleTutorialActivity — Admin ve Lecturer tutorial'larının ortak base'i.
//
// Mimari:
//   - OnboardingActivity (parent class değil, kardeş) → login öncesi 3-sayfa
//   - RoleTutorialActivity (bu, abstract) → rol-bazlı detaylı tur base'i
//   - AdminTutorialActivity (subclass) → sayfa listesi 9
//   - LecturerTutorialActivity (subclass) → sayfa listesi 5
//
// Subclass'lar sadece `pages` listesi ve `onCompleted()` davranışı sağlar.
// ViewPager, progress bar, sayfa sayacı, Geri/İleri butonları, "Atla" —
// hepsi base'te.
package com.unischeduler.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.unischeduler.R
import com.unischeduler.databinding.ActivityRoleTutorialBinding
import com.unischeduler.databinding.ItemOnboardingPageBinding

abstract class RoleTutorialActivity : AppCompatActivity() {

    protected data class TutorialPage(
        @DrawableRes val drawableRes: Int,
        @StringRes val titleRes: Int,
        @StringRes val bodyRes: Int
    )

    private lateinit var binding: ActivityRoleTutorialBinding

    /** Subclass sayfa listesini sağlar. */
    protected abstract val pages: List<TutorialPage>

    /** Subclass tamamlanma davranışını tanımlar (örn. TutorialPrefs flag set). */
    protected abstract fun onTutorialCompleted()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleTutorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pager.adapter = PageAdapter(pages)
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateForPage(position)
        })
        updateForPage(0)

        binding.btnSkipTop.setOnClickListener { finishTutorial() }
        binding.btnPrev.setOnClickListener {
            val current = binding.pager.currentItem
            if (current > 0) binding.pager.setCurrentItem(current - 1, true)
        }
        binding.btnNext.setOnClickListener {
            val current = binding.pager.currentItem
            if (current == pages.lastIndex) finishTutorial()
            else binding.pager.setCurrentItem(current + 1, true)
        }
    }

    private fun updateForPage(position: Int) {
        val total = pages.size
        binding.tvPageCounter.text = getString(R.string.tutorial_page_counter, position + 1, total)

        // Progress bar: 0% (1. sayfa) ... 100% (son sayfa). Lerp ile yumuşak.
        val progress = ((position + 1).toFloat() / total * 100).toInt()
        binding.progressBar.setProgressCompat(progress, true)

        // Geri butonu ilk sayfada gizli (basacak yer yok)
        binding.btnPrev.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE

        // İleri → son sayfada "Hadi Başla"ya dönüşür
        binding.btnNext.setText(
            if (position == pages.lastIndex) R.string.onboarding_finish
            else R.string.onboarding_next
        )
        // Son sayfada "Atla" gizlensin — zaten son sayfa.
        binding.btnSkipTop.visibility = if (position == pages.lastIndex) View.INVISIBLE else View.VISIBLE
    }

    private fun finishTutorial() {
        onTutorialCompleted()
        setResult(RESULT_OK)
        finish()
    }

    private inner class PageAdapter(private val items: List<TutorialPage>) :
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
}
