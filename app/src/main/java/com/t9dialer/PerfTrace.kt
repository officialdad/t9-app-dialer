package com.t9dialer

import android.content.Context
import android.os.Environment
import android.os.Looper
import android.util.Log
import android.util.Printer
import android.view.Choreographer
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Lightweight programmatic profiler for measuring app performance without Android Studio.
 *
 * Output goes to both logcat AND a file readable from Termux:
 *   logcat -s T9Perf:*
 *   cat /sdcard/Download/t9perf.log
 *
 * Usage:
 *   PerfTrace.init(context)                     // Initialize with context in onCreate
 *   PerfTrace.begin("loadApps")                 // Start timing a section
 *   PerfTrace.end("loadApps")                   // End timing (logs duration)
 *   val result = PerfTrace.measure("search") { doSearch() }  // Inline timing
 *   PerfTrace.disable()                         // Disable in onDestroy
 */
object PerfTrace {
    private const val TAG = "T9Perf"
    private const val LOG_FILE = "t9perf.log"
    private const val MAIN_THREAD_BLOCK_THRESHOLD_MS = 16L  // 1 frame at 60fps

    @Volatile
    var enabled = false
        private set

    private var logFile: File? = null

    // Active trace sections
    private val activeTraces = HashMap<String, Long>()

    // Main thread monitor state
    private var mainThreadMonitorActive = false
    private var dispatchStartTime = 0L

    // FPS monitor state
    private var fpsCallback: FpsCallback? = null

    /** Initialize with context and enable profiling */
    fun init(context: Context) {
        try {
            // Write to Downloads folder — accessible from Termux without special permissions
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (dir != null && (dir.exists() || dir.mkdirs())) {
                logFile = File(dir, LOG_FILE)
                // Clear previous log on fresh session
                logFile?.writeText("")
            }
        } catch (_: Exception) {
            // File output unavailable, logcat only
        }
        enabled = true
        log("I", "=== Profiling enabled ===")
    }

    fun disable() {
        stopFpsMonitor()
        stopMainThreadMonitor()
        log("I", "=== Profiling disabled ===")
        enabled = false
    }

    private fun log(level: String, message: String) {
        // Always log to logcat
        when (level) {
            "W" -> Log.w(TAG, message)
            "I" -> Log.i(TAG, message)
            else -> Log.d(TAG, message)
        }
        // Also write to file for Termux access
        try {
            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            logFile?.appendText("$ts [$level] $message\n")
        } catch (_: Exception) {
            // Silently ignore file write failures
        }
    }

    /** Start timing a named section */
    fun begin(label: String) {
        if (!enabled) return
        activeTraces[label] = System.nanoTime()
    }

    /** End timing a named section and log the duration */
    fun end(label: String) {
        if (!enabled) return
        val startNanos = activeTraces.remove(label) ?: return
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0
        log("D", "$label: %.2fms".format(elapsedMs))
    }

    /** Measure a block and return its result */
    fun <T> measure(label: String, block: () -> T): T {
        if (!enabled) return block()
        val start = System.nanoTime()
        val result = block()
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        log("D", "$label: %.2fms".format(elapsedMs))
        return result
    }

    /** Log current memory usage */
    fun logMemory() {
        if (!enabled) return
        val runtime = Runtime.getRuntime()
        val used = (runtime.totalMemory() - runtime.freeMemory()) / 1024
        val max = runtime.maxMemory() / 1024
        log("D", "Memory: ${used}KB / ${max}KB (${used * 100 / max}%%)")
    }

    /**
     * Start monitoring main thread for blocking operations.
     * Logs a warning when any message dispatch exceeds the frame threshold.
     */
    fun startMainThreadMonitor() {
        if (!enabled || mainThreadMonitorActive) return
        mainThreadMonitorActive = true
        Looper.getMainLooper().setMessageLogging(object : Printer {
            override fun println(message: String) {
                if (message.startsWith(">>>>> Dispatching")) {
                    dispatchStartTime = System.currentTimeMillis()
                } else if (message.startsWith("<<<<< Finished")) {
                    val duration = System.currentTimeMillis() - dispatchStartTime
                    if (duration > MAIN_THREAD_BLOCK_THRESHOLD_MS) {
                        log("W", "Main thread blocked: ${duration}ms")
                    }
                }
            }
        })
        log("D", "Main thread monitor started (threshold: ${MAIN_THREAD_BLOCK_THRESHOLD_MS}ms)")
    }

    fun stopMainThreadMonitor() {
        if (!mainThreadMonitorActive) return
        mainThreadMonitorActive = false
        Looper.getMainLooper().setMessageLogging(null)
    }

    /**
     * Start FPS monitoring via Choreographer. Logs FPS every second.
     */
    fun startFpsMonitor() {
        if (!enabled || fpsCallback != null) return
        fpsCallback = FpsCallback().also {
            Choreographer.getInstance().postFrameCallback(it)
        }
        log("D", "FPS monitor started")
    }

    fun stopFpsMonitor() {
        fpsCallback?.let {
            Choreographer.getInstance().removeFrameCallback(it)
            fpsCallback = null
        }
    }

    private class FpsCallback : Choreographer.FrameCallback {
        private var frameCount = 0
        private var intervalStartNanos = 0L

        override fun doFrame(frameTimeNanos: Long) {
            if (intervalStartNanos == 0L) {
                intervalStartNanos = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
                return
            }

            frameCount++
            val elapsed = frameTimeNanos - intervalStartNanos

            if (elapsed >= TimeUnit.SECONDS.toNanos(1)) {
                val fps = frameCount * 1_000_000_000.0 / elapsed
                log("D", "FPS: %.1f (%d frames)".format(fps, frameCount))
                frameCount = 0
                intervalStartNanos = frameTimeNanos
            }

            Choreographer.getInstance().postFrameCallback(this)
        }
    }
}
