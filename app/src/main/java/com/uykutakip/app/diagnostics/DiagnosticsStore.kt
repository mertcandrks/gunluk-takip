package com.uykutakip.app.diagnostics

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.AtomicFile
import com.uykutakip.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DiagnosticsStore(context: Context) {
    private val preferences = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
    private val file = AtomicFile(File(context.noBackupFilesDir, "diagnostics.json"))
    private val worker = HandlerThread("tracker-diagnostics").apply { start() }
    val handler = Handler(worker.looper)
    private val entries = ArrayDeque<String>()
    private val mutableLogs = MutableStateFlow<List<String>>(emptyList())
    val logs = mutableLogs.asStateFlow()
    private val mutableEnabled = MutableStateFlow(preferences.getBoolean("enabled", false))
    val enabled = mutableEnabled.asStateFlow()
    @Volatile var screen = "startup"
        private set

    init {
        handler.post {
            synchronized(this) {
                runCatching {
                    val array = JSONArray(file.openRead().bufferedReader().use { it.readText() })
                    for (i in maxOf(0, array.length() - 200) until array.length()) entries.addLast(array.getString(i))
                }
                mutableLogs.value = entries.toList().asReversed()
            }
            event("APP_START build=${BuildConfig.BUILD_TYPE} version=${BuildConfig.VERSION_NAME}")
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                if (enabled.value) {
                    // Never persist exception messages, which can include user-entered values.
                    append("CRASH ${error.javaClass.name} " + error.stackTrace.take(12).joinToString(" | "))
                }
            } finally {
                previous?.uncaughtException(thread, error)
            }
        }
    }

    fun setEnabled(value: Boolean) {
        mutableEnabled.value = value
        preferences.edit().putBoolean("enabled", value).apply()
        handler.post { append(if (value) "DIAGNOSTICS_ON" else "DIAGNOSTICS_OFF") }
    }

    fun setScreen(route: String) {
        screen = if (route in setOf("sleep", "nutrition", "hydration", "agenda", "stats", "diagnostics")) route else "other"
        event("SCREEN $screen")
    }

    fun event(message: String) {
        if (enabled.value) handler.post { if (enabled.value) append(message) }
    }

    @Synchronized private fun append(message: String) {
        entries.addLast("${java.time.Instant.now()} $message")
        while (entries.size > 200) entries.removeFirst()
        mutableLogs.value = entries.toList().asReversed()
        var output: java.io.FileOutputStream? = null
        try {
            output = file.startWrite()
            output.write(JSONArray(entries.toList()).toString().toByteArray())
            file.finishWrite(output)
        } catch (_: Exception) {
            file.failWrite(output)
        }
    }

    fun clear() = handler.post {
        synchronized(this) {
            entries.clear()
            file.delete()
            mutableLogs.value = emptyList()
        }
    }

    fun report(): String = JSONObject()
        .put("version", BuildConfig.VERSION_NAME)
        .put("build", BuildConfig.BUILD_TYPE)
        .put("androidApi", Build.VERSION.SDK_INT)
        .put("model", Build.MODEL)
        .put("logs", JSONArray(logs.value))
        .toString(2)
}
