// Reusable extension helpers used across ViewModels and Fragments.
package com.unischeduler.util

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
 * Run [produce] on Dispatchers.IO and stream its bytes into the document
 * picked by the user via Storage Access Framework (writes to [destination]).
 *
 * On success calls [onDone] with the destination URI so the fragment can
 * surface a "Share" follow-up. On failure shows a Toast with the localised
 * error message.
 *
 * Safe to call after the fragment has been destroyed — the returning
 * `if (!isAdded) return` guard prevents Toast / dialog access on a
 * detached fragment, which would crash with IllegalStateException.
 */
fun Fragment.writeBytesToUri(
    destination: Uri,
    produce: suspend () -> ByteArray,
    onDone: (Uri) -> Unit = { _ ->
        Toast.makeText(requireContext(), getString(R.string.export_success), Toast.LENGTH_SHORT).show()
    }
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
                Toast.makeText(
                    requireContext(),
                    getString(R.string.export_failed, it.message ?: it::class.java.simpleName),
                    Toast.LENGTH_LONG
                ).show()
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
