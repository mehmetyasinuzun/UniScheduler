// PendingDelete — implements Gmail-style "Item moved to trash. UNDO"
// pattern with a 5-second grace window before the actual delete fires.
//
// How it works:
//   1. UI calls PendingDelete.schedule(message) { performActualDelete() }.
//   2. A Snackbar appears with an UNDO button. The list item is hidden
//      from the local view immediately (caller is expected to optimistic-
//      remove it).
//   3. If the user taps UNDO before timeout, performActualDelete() is
//      NEVER called and onUndo() is invoked so the caller can re-show
//      the item.
//   4. If the timeout elapses, performActualDelete() runs.
//
// Lifecycle: tied to a Fragment's view-lifecycle scope so that navigating
// away mid-grace performs the delete (same as Gmail). If the user pulls
// the network plug during the grace period the delete just fails silently
// and the next data load shows the item again — acceptable trade-off,
// alternative is "block UI for 5 seconds before delete commits" which
// users hate.
package com.unischeduler.util

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.unischeduler.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object PendingDelete {

    private const val DELAY_MS = 4_500L  // matches Snackbar.LENGTH_LONG

    /**
     * Show an UNDO snackbar and schedule [performDelete] to run after the
     * timeout unless the user undoes.
     *
     * @param anchor       view to anchor the snackbar to (any view in the
     *                     current screen)
     * @param owner        lifecycle owner (usually viewLifecycleOwner of the
     *                     hosting fragment) — when this owner is destroyed
     *                     while in grace period, the delete is fired so
     *                     the change is committed.
     * @param message      "Hocam silindi" / "Course deleted" / etc.
     * @param onUndo       called if the user taps UNDO. Caller should
     *                     re-insert the item into the local list and
     *                     refresh UI. NOT called if grace expires.
     * @param performDelete called after grace if UNDO was not pressed.
     *                      Should perform the actual network delete.
     */
    fun schedule(
        anchor: View,
        owner: LifecycleOwner,
        message: String,
        onUndo: () -> Unit,
        performDelete: suspend () -> Unit
    ) {
        // Single source of truth for "did the delete already happen?"
        // Snackbar callback and the lifecycle-scope coroutine race; the
        // first one to flip this flag wins. Any subsequent attempt is a
        // no-op so we never double-fire performDelete.
        val committed = java.util.concurrent.atomic.AtomicBoolean(false)
        val undone    = java.util.concurrent.atomic.AtomicBoolean(false)

        fun commitOnce() {
            if (undone.get()) return
            if (!committed.compareAndSet(false, true)) return
            owner.lifecycleScope.launch {
                runCatching { performDelete() }
            }
        }

        val snackbar = Snackbar.make(anchor, message, Snackbar.LENGTH_LONG)
            .setAction(anchor.context.getString(R.string.ux_undo_action)) {
                undone.set(true)
                onUndo()
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event == DISMISS_EVENT_ACTION) return     // user undid
                    if (event == DISMISS_EVENT_MANUAL) return     // programmatic dismiss
                    commitOnce()
                }
            })

        // Belt-and-braces: if the snackbar host is destroyed before
        // onDismissed fires (rare — can happen if user immediately
        // navigates away), commit on grace timeout.
        owner.lifecycleScope.launch {
            delay(DELAY_MS + 500)  // fire just after Snackbar would have
            if (owner.lifecycle.currentState != Lifecycle.State.DESTROYED) commitOnce()
        }

        snackbar.show()
    }
}
