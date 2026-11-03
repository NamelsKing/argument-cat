package dev.namelsking.argumentcat.mascot

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.Choreographer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

data class CatPose(val emotion: String = "neutral", val activity: String = "idle", val speech: Float = 0f)

@Composable
fun CatMascot(pose: CatPose, modifier: Modifier = Modifier) {
    val owner = LocalLifecycleOwner.current
    var surface by remember { mutableStateOf<CatSurface?>(null) }
    AndroidView(
        factory = { context -> CatSurface(context).also { surface = it; it.cat.pose = pose } },
        update = {
            it.cat.pose = pose
            it.contentDescription = "Cat: ${pose.emotion}, ${pose.activity}"
        },
        modifier = modifier,
    )
    DisposableEffect(owner, surface) {
        val view = surface
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view?.animate(true)
                Lifecycle.Event.ON_PAUSE -> view?.animate(false)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view?.animate(true)
        onDispose { owner.lifecycle.removeObserver(observer); view?.animate(false) }
    }
}

private class CatSurface(context: Context) : GLSurfaceView(context), Choreographer.FrameCallback {
    val cat = CatRenderer()
    private var running = false
    private var lastFrame = 0L

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(cat)
        renderMode = RENDERMODE_WHEN_DIRTY
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun animate(enabled: Boolean) {
        if (enabled == running) return
        running = enabled
        if (enabled) {
            onResume()
            Choreographer.getInstance().postFrameCallback(this)
        } else {
            Choreographer.getInstance().removeFrameCallback(this)
            onPause()
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        // The mascot does not need a 60 fps render loop alongside local inference.
        if (frameTimeNanos - lastFrame >= 32_000_000L) {
            lastFrame = frameTimeNanos
            requestRender()
        }
        Choreographer.getInstance().postFrameCallback(this)
    }
}
