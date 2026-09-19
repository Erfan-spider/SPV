package com.spiderv2ray.spv

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import java.io.File

class SpvApplication : Application() {

    private val watchdogHandler = Handler(Looper.getMainLooper())
    @Volatile private var lastTick = System.currentTimeMillis()

    companion object {
        private const val PREFS_NAME = "spv_settings"
        private const val KEY_DARK_MODE = "dark_mode_enabled"

        // App defaults to dark + sky-blue regardless of the phone's system
        // light/dark setting, since that's the only look this app is designed
        // for. The user can flip this from More > ظاهر برنامه, and the choice
        // is remembered from then on.
        fun isDarkModeEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_DARK_MODE, true)

        fun setDarkModeEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkModeEnabled(this)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        startMainThreadTicker()
        startWatchdogThread()
    }

    private fun startMainThreadTicker() {
        val ticker = object : Runnable {
            override fun run() {
                lastTick = System.currentTimeMillis()
                watchdogHandler.postDelayed(this, 500)
            }
        }
        watchdogHandler.post(ticker)
    }

    private fun startWatchdogThread() {
        Thread {
            while (true) {
                Thread.sleep(1000)
                val stalledFor = System.currentTimeMillis() - lastTick
                if (stalledFor > 4000) {
                    dumpAllThreads(stalledFor)
                    // Avoid spamming: wait a bit before checking again
                    Thread.sleep(5000)
                }
            }
        }.start()
    }

    private fun dumpAllThreads(stalledFor: Long) {
        try {
            val logFile = File(filesDir, "spv_anr_dump.log")
            val sb = StringBuilder()
            sb.append("=== ANR-like stall detected, main thread frozen for ${stalledFor}ms ===\n")
            sb.append("Time: ${java.util.Date()}\n\n")
            val allThreads = Thread.getAllStackTraces()
            for ((thread, stack) in allThreads) {
                sb.append("--- Thread: ${thread.name} (state=${thread.state}) ---\n")
                for (frame in stack) {
                    sb.append("    at $frame\n")
                }
                sb.append("\n")
            }
            logFile.appendText(sb.toString())
        } catch (e: Exception) {
            // ignore
        }
    }
}
