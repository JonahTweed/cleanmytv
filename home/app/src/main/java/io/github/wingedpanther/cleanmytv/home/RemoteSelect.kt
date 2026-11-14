package io.github.wingedpanther.cleanmytv.home

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Selection identity, one hold per press, no click after a hold or an interrupted press. */
class SelectPress {
    private var identity: String? = null
    private var held = false
    fun down(key: String, fresh: Boolean = true): Boolean {
        if (identity != null || !fresh) return false
        identity = key; held = false; return true
    }
    fun hold(key: String): Boolean {
        if (identity != key || held) return false
        held = true; return true
    }
    fun up(key: String): Boolean {
        val click = identity == key && !held
        cancel(); return click
    }
    fun cancel() { identity = null; held = false }
}

/** Compose-compatible remote handling; pointer long-click remains Surface's responsibility. */
fun Modifier.remoteSelect(identity: String, click: () -> Unit, hold: () -> Unit): Modifier = composed {
    val state = remember(identity) { SelectPress() }
    val scope = rememberCoroutineScope()
    val latestClick by rememberUpdatedState(click)
    val latestHold by rememberUpdatedState(hold)
    var job by remember { mutableStateOf<Job?>(null) }
    fun cancel() { job?.cancel(); job = null; state.cancel() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, identity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) cancel() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); cancel() }
    }
    this.onFocusChanged { if (!it.hasFocus) cancel() }.onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        val ok = native.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A)
        when {
            native.keyCode == KeyEvent.KEYCODE_MENU -> {
                if (native.action == KeyEvent.ACTION_UP && !native.isCanceled) latestHold()
                true
            }
            !ok -> false
            native.action == KeyEvent.ACTION_DOWN -> {
                if (state.down(identity, native.repeatCount == 0)) job = scope.launch {
                    delay(550)
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && state.hold(identity)) latestHold()
                }
                true
            }
            native.action == KeyEvent.ACTION_UP -> {
                job?.cancel(); job = null
                if (native.isCanceled) state.cancel() else if (state.up(identity)) latestClick()
                true
            }
            else -> true
        }
    }
}
