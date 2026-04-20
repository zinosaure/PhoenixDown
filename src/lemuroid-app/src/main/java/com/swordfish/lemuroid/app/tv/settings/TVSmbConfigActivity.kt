package com.swordfish.lemuroid.app.tv.settings

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.smb.SmbClient
import com.swordfish.lemuroid.lib.storage.smb.SmbCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TV-optimized Activity for configuring SMB library source.
 * Uses standard Android EditText layout for reliable input handling.
 */
class TVSmbConfigActivity : FragmentActivity() {
    
    private lateinit var serverInput: EditText
    private lateinit var pathInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var statusText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_smb_config)
        
        // Find views
        serverInput = findViewById(R.id.smb_server_input)
        pathInput = findViewById(R.id.smb_path_input)
        usernameInput = findViewById(R.id.smb_username_input)
        passwordInput = findViewById(R.id.smb_password_input)
        statusText = findViewById(R.id.smb_status_text)
        
        // Load existing values
        loadExistingConfig()
        
        // Set up buttons
        findViewById<Button>(R.id.smb_test_button).setOnClickListener { testConnection() }
        findViewById<Button>(R.id.smb_save_button).setOnClickListener { saveAndFinish() }
        findViewById<Button>(R.id.smb_cancel_button).setOnClickListener { finish() }
        
        // Request focus on first input
        serverInput.requestFocus()
    }
    
    private val KEY_RAW_PATH = "smb_library_raw_path_ui_v2"

    private fun loadExistingConfig() {
        val prefs = SharedPreferencesHelper.getSharedPreferences(this)
        serverInput.setText(prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SERVER, ""))
        
        // V2 Strategy: Try to load the RAW path specifically saved for UI to prevent degradation
        val rawPath = prefs.getString(KEY_RAW_PATH, null)
        
        if (rawPath != null) {
            android.util.Log.e("ANTIGRAVITY", "UI Load: Found RAW path: '$rawPath'")
            pathInput.setText(rawPath)
        } else {
            // Fallback: Reconstruct full path for display: /Share/Path
            val share = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SHARE, "")
            val path = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PATH, "") ?: ""
            val fullPath = if (!share.isNullOrBlank()) "/$share$path" else path
            android.util.Log.e("ANTIGRAVITY", "UI Load: Reconstructed path: '$fullPath' (Share='$share')")
            pathInput.setText(fullPath)
        }
        
        usernameInput.setText(prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_USERNAME, ""))
        passwordInput.setText(prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PASSWORD, ""))
    }

    private fun testConnection() {
        val server = serverInput.text.toString().trim()
        val path = pathInput.text.toString().trim()
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString()
        
        if (server.isBlank() || path.isBlank()) {
            statusText.text = getString(R.string.tv_smb_enter_server_and_path)
            statusText.setTextColor(getColor(android.R.color.holo_red_light))
            statusText.visibility = View.VISIBLE
            return
        }
        
        statusText.text = getString(R.string.sources_smb_testing)
        statusText.setTextColor(getColor(android.R.color.white))
        statusText.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.IO).launch {
            val smbClient = SmbClient()
            val credentials = if (username.isNotBlank()) {
                SmbCredentials(username, password)
            } else null
            
            // Parse share from path (first segment)
            val shareName = path.removePrefix("/").split("/").firstOrNull() ?: ""
            
            val result = smbClient.testConnection(server, shareName, credentials)
            
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    statusText.text = getString(R.string.tv_smb_connection_success)
                    statusText.setTextColor(getColor(android.R.color.holo_green_light))
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    statusText.text = getString(R.string.tv_smb_connection_failed, errorMsg)
                    statusText.setTextColor(getColor(android.R.color.holo_red_light))
                }
            }
        }
    }

    private fun saveAndFinish() {
        val server = serverInput.text.toString().trim()
        val fullPath = pathInput.text.toString().trim()
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString()
        
        android.util.Log.e("ANTIGRAVITY", "UI Save: User Input Path: '$fullPath'")
        
        if (server.isBlank() || fullPath.isBlank()) {
            statusText.text = getString(R.string.tv_smb_enter_server_and_path)
            statusText.setTextColor(getColor(android.R.color.holo_red_light))
            statusText.visibility = View.VISIBLE
            return
        }
        
        // Parse share name from path (first segment after /)
        // e.g., "/almacen/juegos/roms" -> share="almacen", path="/juegos/roms"
        val pathSegments = fullPath.removePrefix("/").split("/")
        val shareName = pathSegments.firstOrNull() ?: ""
        val subPath = if (pathSegments.size > 1) {
            "/" + pathSegments.drop(1).joinToString("/")
        } else {
            ""
        }
        
        android.util.Log.e("ANTIGRAVITY", "UI Parse: Share='$shareName', SubPath='$subPath'")
        
        if (shareName.isBlank()) {
            statusText.text = getString(R.string.tv_smb_path_must_start_with_share)
            statusText.setTextColor(getColor(android.R.color.holo_red_light))
            statusText.visibility = View.VISIBLE
            return
        }
        
        val prefs = SharedPreferencesHelper.getSharedPreferences(this)
        prefs.edit().apply {
            putString(SharedPreferencesHelper.KEY_LIBRARY_TYPE, "smb")
            putString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SERVER, server)
            putString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SHARE, shareName)
            putString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PATH, subPath)
            putString(SharedPreferencesHelper.KEY_SMB_LIBRARY_USERNAME, username.takeIf { it.isNotBlank() })
            putString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PASSWORD, password.takeIf { it.isNotBlank() })
            
            // Save RAW path for UI persistence
            putString(KEY_RAW_PATH, fullPath)
            
            apply()
        }
        
        // Trigger library rescan
        LibraryIndexScheduler.scheduleLibrarySync(this)
        
        Toast.makeText(this, getString(R.string.tv_smb_configured_successfully), Toast.LENGTH_SHORT).show()
        finish()
    }
}
