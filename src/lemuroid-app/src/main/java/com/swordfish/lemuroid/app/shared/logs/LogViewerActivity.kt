package com.swordfish.lemuroid.app.shared.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.swordfish.lemuroid.R

class LogViewerActivity : FragmentActivity() {
    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)
        title = getString(R.string.settings_title_log_viewer)

        logText = findViewById(R.id.logs_text)
        findViewById<Button>(R.id.logs_refresh_button).setOnClickListener { renderLogs() }
        findViewById<Button>(R.id.logs_copy_button).setOnClickListener { copyLogs() }
        findViewById<Button>(R.id.logs_clear_button).setOnClickListener {
            InAppLogStore.clear()
            renderLogs()
            Toast.makeText(this, getString(R.string.settings_logs_cleared), Toast.LENGTH_SHORT).show()
        }

        renderLogs()
    }

    override fun onResume() {
        super.onResume()
        renderLogs()
    }

    private fun renderLogs() {
        val content = InAppLogStore.dump()
        logText.text = if (content.isBlank()) {
            getString(R.string.settings_logs_empty)
        } else {
            content
        }
    }

    private fun copyLogs() {
        val content = InAppLogStore.dump()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("timber_logs", content))
        Toast.makeText(this, getString(R.string.settings_logs_copied), Toast.LENGTH_SHORT).show()
    }
}
