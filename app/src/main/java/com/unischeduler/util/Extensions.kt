// Reusable extension helpers used across ViewModels and Fragments.
package com.unischeduler.util

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.unischeduler.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Collects a Flow safely tied to STARTED lifecycle (Lab Task 2 pattern)
fun <T> Fragment.collectFlow(flow: Flow<T>, action: suspend (T) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { action(it) }
        }
    }
}

/**
 * Material 3 önerisi: kısa kullanıcı geri bildirimi için Snackbar tercih edilir
 * (Toast yerine). Snackbar ekrana asılı olduğu için Bottom Navigation üstüne
 * doğru oturur ve TalkBack ile düzgün duyurulur. Fragment view'ı henüz hazır
 * değilse sessizce yutarız — onCreateView öncesi tetiklenen IO callback'leri
 * için crash önler.
 */
fun Fragment.showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT): Snackbar? {
    val root = view ?: return null
    return Snackbar.make(root, message, duration).also { it.show() }
}

fun Fragment.showSnackbar(@StringRes messageRes: Int, duration: Int = Snackbar.LENGTH_SHORT): Snackbar? =
    showSnackbar(getString(messageRes), duration)

fun Fragment.showSnackbar(@StringRes messageRes: Int, vararg formatArgs: Any, duration: Int = Snackbar.LENGTH_LONG): Snackbar? =
    showSnackbar(getString(messageRes, *formatArgs), duration)

/**
 * Hata için kırmızı vurgu — Snackbar'ın action text'ini colorError ile boyar
 * ve daha uzun gösterir. Mesajın kendi rengi Material varsayılan (light/dark
 * auto) kalır; renk tonu sadece "Tamam" gibi minimal action butonu üstünde.
 */
fun Fragment.showErrorSnackbar(message: String): Snackbar? {
    val root = view ?: return null
    // Detached fragment'ta context null olabilir — requireContext() o senaryoda
    // crash atar. View hâlâ tutuluyorsa context yine valid ama paranoid kontrol
    // ucuz; arka plan IO callback'leri çok zaman gecikebiliyor.
    val ctx = context ?: return null
    return Snackbar.make(root, message, Snackbar.LENGTH_LONG).also { bar ->
        val errorColor = androidx.core.content.ContextCompat.getColor(ctx, R.color.color_error_dark)
        bar.setActionTextColor(errorColor)
        bar.show()
    }
}

fun Fragment.showErrorSnackbar(@StringRes messageRes: Int): Snackbar? =
    showErrorSnackbar(getString(messageRes))

/**
 * Run [produce] on Dispatchers.IO and stream its bytes into the document
 * picked by the user via Storage Access Framework (writes to [destination]).
 *
 * On success calls [onDone] with the destination URI so the fragment can
 * surface a "Share" follow-up. On failure shows a Snackbar with the localised
 * error message.
 *
 * Safe to call after the fragment has been destroyed — the returning
 * `if (!isAdded) return` guard prevents UI access on a detached fragment.
 */
fun Fragment.writeBytesToUri(
    destination: Uri,
    produce: suspend () -> ByteArray,
    onDone: (Uri) -> Unit = { _ -> showSnackbar(R.string.export_success) }
) {
    val ctx = requireContext().applicationContext
    lifecycleScope.launch {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val bytes = produce()
                ctx.contentResolver.openOutputStream(destination)?.use { it.write(bytes) }
                    ?: error("Output stream null")
                bytes.size
            }
        }
        if (!isAdded) return@launch
        result
            .onSuccess { onDone(destination) }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                showErrorSnackbar(
                    getString(R.string.export_failed, it.message ?: it::class.java.simpleName)
                )
            }
    }
}

/** Build a SEND intent for an already-saved document URI and start a chooser. */
fun Fragment.shareDocument(uri: Uri, mimeType: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, getString(R.string.export_share_title)))
}

/**
 * Lifecycle-safe wrapper for findNavController().navigate(). Returns false (no-op)
 * if the fragment is detached or its NavController is unavailable. Eski pattern
 * `findNavController().navigate(...)` doğrudan çağırınca isAdded=false durumunda
 * IllegalStateException atıyordu — özellikle async callback'lerden Fragment
 * navigate edildiğinde (login success → fragment hızlıca destroy ediliyorsa).
 */
fun Fragment.navigateSafe(actionId: Int): Boolean {
    if (!isAdded || view == null) return false
    return runCatching {
        androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(actionId)
        true
    }.getOrDefault(false)
}

/**
 * Click debounce: aynı butona art arda tıklama → ilk tıklamadan sonra
 * [windowMs] süresi boyunca diğer tıklamaları yutar. setOnClickListener
 * yerine bunu kullan. Eski pattern: kullanıcı kayıt ekle butonuna 3 kez
 * tıklarsa 3 ayrı insert ediliyordu — şimdi tek başarılı insert.
 *
 * Kullanım:
 *   binding.btnAdd.setDebouncedClickListener {
 *       viewModel.addLecturer(...)
 *   }
 */
fun android.view.View.setDebouncedClickListener(
    windowMs: Long = 600L,
    onClick: (android.view.View) -> Unit
) {
    var lastClickAt = 0L
    setOnClickListener { v ->
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastClickAt < windowMs) return@setOnClickListener
        lastClickAt = now
        onClick(v)
    }
}
