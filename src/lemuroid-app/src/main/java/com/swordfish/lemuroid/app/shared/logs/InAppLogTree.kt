package com.swordfish.lemuroid.app.shared.logs

import android.util.Log
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InAppLogTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val timestamp = formatter.get().format(Date())
        val priorityLetter = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "?"
        }

        InAppLogStore.append("$timestamp $priorityLetter/${tag ?: "Lemuroid"}: $message")
        if (t != null) {
            InAppLogStore.append(t.stackTraceToString())
        }
    }

    companion object {
        private val formatter = ThreadLocal.withInitial {
            SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        }
    }
}
