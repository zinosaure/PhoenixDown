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
import com.swordfish.lemuroid.lib.storage.source.SmbLoginProfile
import com.swordfish.lemuroid.lib.storage.source.SmbLoginProfileRepository
import com.swordfish.lemuroid.lib.storage.source.SourceCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TV-optimized Activity for configuring SMB library source.
 * Uses standard Android EditText layout for reliable input handling.
 */
class TVSmbConfigActivity : FragmentActivity() {
    private lateinit var titleText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var pathLabel: TextView
    
    private lateinit var savedLoginButton: Button
    private lateinit var serverInput: EditText
    private lateinit var portInput: EditText
    private lateinit var pathInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var statusText: TextView
    private lateinit var testButton: Button

    private val smbLoginProfileRepository by lazy { SmbLoginProfileRepository(this) }

    private val mode: String by lazy {
        intent.getStringExtra(EXTRA_MODE) ?: MODE_LIBRARY
    }

    private val profileId: String? by lazy {
        intent.getStringExtra(EXTRA_PROFILE_ID)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_smb_config)
        
        // Find views
        titleText = findViewById(R.id.smb_title_text)
        descriptionText = findViewById(R.id.smb_description_text)
        pathLabel = findViewById(R.id.smb_path_label)
        savedLoginButton = findViewById(R.id.smb_saved_login_button)
        serverInput = findViewById(R.id.smb_server_input)
        portInput = findViewById(R.id.smb_port_input)
        pathInput = findViewById(R.id.smb_path_input)
        usernameInput = findViewById(R.id.smb_username_input)
        passwordInput = findViewById(R.id.smb_password_input)
        statusText = findViewById(R.id.smb_status_text)
        testButton = findViewById(R.id.smb_test_button)

        configureTextsForMode()
        configureVisibilityForMode()
        
        // Load existing values
        loadExistingConfig()
        
        // Set up buttons
        savedLoginButton.setOnClickListener { showSavedLoginPicker() }
        testButton.setOnClickListener { testConnection() }
        findViewById<Button>(R.id.smb_save_button).setOnClickListener { saveAndFinish() }
        findViewById<Button>(R.id.smb_cancel_button).setOnClickListener { finish() }
        
        // Request focus on first input
        serverInput.requestFocus()
    }
    
    private val KEY_RAW_PATH = "smb_library_raw_path_ui_v2"

    private fun configureTextsForMode() {
        when (mode) {
            MODE_SAVE -> {
                titleText.setText(R.string.tv_smb_save_title)
                descriptionText.setText(R.string.tv_smb_save_description)
            }
            MODE_DOWNLOAD -> {
                titleText.setText(R.string.tv_smb_download_title)
                descriptionText.setText(R.string.tv_smb_download_description)
            }
            MODE_PROFILE -> {
                titleText.setText(R.string.tv_smb_profile_title)
                descriptionText.setText(R.string.tv_smb_profile_description)
            }
            else -> {
                titleText.setText(com.swordfish.lemuroid.lib.R.string.smb_library_title)
                descriptionText.setText(com.swordfish.lemuroid.lib.R.string.smb_library_description)
            }
        }
    }

    private fun configureVisibilityForMode() {
        val isProfileMode = mode == MODE_PROFILE
        savedLoginButton.visibility = if (isProfileMode) View.GONE else View.VISIBLE
        pathLabel.visibility = if (isProfileMode) View.GONE else View.VISIBLE
        pathInput.visibility = if (isProfileMode) View.GONE else View.VISIBLE
        testButton.visibility = if (isProfileMode) View.GONE else View.VISIBLE
    }

    private fun loadExistingConfig() {
        val prefs = SharedPreferencesHelper.getSharedPreferences(this)

        when (mode) {
            MODE_SAVE -> {
                val current = prefs.getString(SharedPreferencesHelper.KEY_SAVE_LOCATION_URI, "") ?: ""
                val (server, fullPath) = parseSmbUri(current)
                val (host, port) = splitHostAndPort(server)
                serverInput.setText(host)
                portInput.setText(port)
                pathInput.setText(fullPath)
                usernameInput.setText(prefs.getString(SharedPreferencesHelper.KEY_SAVE_SMB_USERNAME, ""))
                passwordInput.setText(prefs.getString(SharedPreferencesHelper.KEY_SAVE_SMB_PASSWORD, ""))
            }
            MODE_DOWNLOAD -> {
                val current = prefs.getString(SharedPreferencesHelper.KEY_DOWNLOAD_SOURCE_ID, "") ?: ""
                val (server, fullPath) = parseSmbUri(current)
                val (host, port) = splitHostAndPort(server)
                serverInput.setText(host)
                portInput.setText(port)
                pathInput.setText(fullPath)
                usernameInput.setText(prefs.getString(SharedPreferencesHelper.KEY_DOWNLOAD_SMB_USERNAME, ""))
                passwordInput.setText(prefs.getString(SharedPreferencesHelper.KEY_DOWNLOAD_SMB_PASSWORD, ""))
            }
            MODE_PROFILE -> {
                val profile = smbLoginProfileRepository.getProfiles().firstOrNull { it.id == profileId }
                val (host, port) = splitHostAndPort(profile?.server.orEmpty())
                serverInput.setText(host)
                portInput.setText(port)
                usernameInput.setText(profile?.username.orEmpty())
                passwordInput.setText(profile?.password.orEmpty())
            }
            else -> {
                val savedServer = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SERVER, "") ?: ""
                val (host, port) = splitHostAndPort(savedServer)
                serverInput.setText(host)
                portInput.setText(port)

                val rawPath = prefs.getString(KEY_RAW_PATH, null)
                if (rawPath != null) {
                    pathInput.setText(rawPath)
                } else {
                    val share = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SHARE, "")
                    val path = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PATH, "") ?: ""
                    val fullPath = if (!share.isNullOrBlank()) "/$share$path" else path
                    pathInput.setText(fullPath)
                }

                usernameInput.setText(prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_USERNAME, ""))
                passwordInput.setText(prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PASSWORD, ""))
            }
        }
    }

    private fun showSavedLoginPicker() {
        seedExistingProfiles()
        val profiles = smbLoginProfileRepository.getProfiles()
        if (profiles.isEmpty()) {
            Toast.makeText(this, getString(R.string.tv_smb_saved_login_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val labels = profiles.map { profile ->
            if (profile.username.isNotBlank()) {
                "${profile.name} (${profile.username}@${profile.server})"
            } else {
                "${profile.name} (${profile.server})"
            }
        }.toTypedArray()

        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.sources_smb_saved_profile_title)
            .setItems(labels) { _, which ->
                applyProfile(profiles[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyProfile(profile: SmbLoginProfile) {
        val (host, port) = splitHostAndPort(profile.server)
        serverInput.setText(host)
        portInput.setText(port)
        usernameInput.setText(profile.username)
        passwordInput.setText(profile.password)
        statusText.visibility = View.GONE
    }

    private fun testConnection() {
        val server = buildServerAddress()
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
        val server = buildServerAddress()
        val fullPath = normalizePath(pathInput.text.toString().trim())
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (mode == MODE_PROFILE) {
            if (server.isBlank()) {
                statusText.text = getString(R.string.tv_smb_enter_server_and_path)
                statusText.setTextColor(getColor(android.R.color.holo_red_light))
                statusText.visibility = View.VISIBLE
                return
            }

            val existing = smbLoginProfileRepository.getProfiles().firstOrNull { it.id == profileId }
            val profileName = existing?.name ?: server.substringBefore(':').ifBlank { server }
            smbLoginProfileRepository.addOrUpdateProfile(
                SmbLoginProfile(
                    id = existing?.id ?: SmbLoginProfile(name = profileName, server = server).id,
                    name = profileName,
                    server = server,
                    username = username,
                    password = password,
                ),
            )
            Toast.makeText(this, getString(R.string.tv_smb_profile_configured_successfully), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
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
        when (mode) {
            MODE_SAVE -> {
                prefs.edit().apply {
                    putString(SharedPreferencesHelper.KEY_SAVE_LOCATION_URI, "smb://$server$fullPath")
                    putString(SharedPreferencesHelper.KEY_SAVE_SMB_USERNAME, username)
                    putString(SharedPreferencesHelper.KEY_SAVE_SMB_PASSWORD, password)
                    apply()
                }
                rememberLogin(server, username, password)
                Toast.makeText(this, getString(R.string.tv_smb_save_configured_successfully), Toast.LENGTH_SHORT).show()
            }
            MODE_DOWNLOAD -> {
                prefs.edit().apply {
                    putString(SharedPreferencesHelper.KEY_DOWNLOAD_SOURCE_ID, "smb://$server$fullPath")
                    putString(SharedPreferencesHelper.KEY_DOWNLOAD_SMB_USERNAME, username)
                    putString(SharedPreferencesHelper.KEY_DOWNLOAD_SMB_PASSWORD, password)
                    apply()
                }
                rememberLogin(server, username, password)
                Toast.makeText(this, getString(R.string.tv_smb_download_configured_successfully), Toast.LENGTH_SHORT).show()
            }
            else -> {
                prefs.edit().apply {
                    putString(SharedPreferencesHelper.KEY_LIBRARY_TYPE, "smb")
                    putString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SERVER, server)
                    putString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SHARE, shareName)
                    putString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PATH, subPath)
                    putString(SharedPreferencesHelper.KEY_SMB_LIBRARY_USERNAME, username.takeIf { it.isNotBlank() })
                    putString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PASSWORD, password.takeIf { it.isNotBlank() })
                    putString(KEY_RAW_PATH, fullPath)
                    apply()
                }

                rememberLogin(server, username, password)
                LibraryIndexScheduler.scheduleLibrarySync(this)
                Toast.makeText(this, getString(R.string.tv_smb_configured_successfully), Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    private fun rememberLogin(server: String, username: String, password: String) {
        smbLoginProfileRepository.rememberConnection(
            server,
            if (username.isNotBlank()) SourceCredentials(username, password) else null,
        )
    }

    private fun seedExistingProfiles() {
        val prefs = SharedPreferencesHelper.getSharedPreferences(this)
        val sourceRepo = com.swordfish.lemuroid.lib.storage.source.SourceRepository(this)
        sourceRepo.getCustomSources()
            .filter { it.type == com.swordfish.lemuroid.lib.storage.source.SourceType.SMB }
            .forEach { source ->
                val authority = runCatching { android.net.Uri.parse(source.path).authority }.getOrNull().orEmpty()
                smbLoginProfileRepository.rememberConnection(authority, source.credentials)
            }

        rememberLoginFromUri(
            prefs.getString(SharedPreferencesHelper.KEY_SAVE_LOCATION_URI, "") ?: "",
            prefs.getString(SharedPreferencesHelper.KEY_SAVE_SMB_USERNAME, "") ?: "",
            prefs.getString(SharedPreferencesHelper.KEY_SAVE_SMB_PASSWORD, "") ?: "",
        )
        rememberLoginFromUri(
            prefs.getString(SharedPreferencesHelper.KEY_DOWNLOAD_SOURCE_ID, "") ?: "",
            prefs.getString(SharedPreferencesHelper.KEY_DOWNLOAD_SMB_USERNAME, "") ?: "",
            prefs.getString(SharedPreferencesHelper.KEY_DOWNLOAD_SMB_PASSWORD, "") ?: "",
        )
    }

    private fun rememberLoginFromUri(uri: String, username: String, password: String) {
        if (!uri.startsWith("smb://")) return
        val authority = runCatching { android.net.Uri.parse(uri).authority }.getOrNull().orEmpty()
        rememberLogin(authority, username, password)
    }

    private fun normalizePath(path: String): String {
        if (path.isBlank()) return path
        return if (path.startsWith("/")) path else "/$path"
    }

    private fun parseSmbUri(uri: String): Pair<String, String> {
        if (!uri.startsWith("smb://")) {
            return "" to ""
        }

        val withoutScheme = uri.removePrefix("smb://")
        val slashIndex = withoutScheme.indexOf('/')
        return if (slashIndex > 0) {
            withoutScheme.substring(0, slashIndex) to withoutScheme.substring(slashIndex)
        } else {
            withoutScheme to ""
        }
    }

    private fun buildServerAddress(): String {
        val host = serverInput.text.toString().trim()
        val port = portInput.text.toString().trim()

        if (host.isBlank() || port.isBlank()) {
            return host
        }

        val parsedPort = port.toIntOrNull()
        if (parsedPort == null || parsedPort !in 1..65535) {
            return host
        }

        return "$host:$parsedPort"
    }

    private fun splitHostAndPort(server: String): Pair<String, String> {
        val raw = server.trim()
        if (raw.isBlank()) return "" to ""

        val separatorIndex = raw.lastIndexOf(':')
        if (separatorIndex <= 0 || separatorIndex == raw.lastIndex) {
            return raw to ""
        }

        val hostPart = raw.substring(0, separatorIndex)
        val portPart = raw.substring(separatorIndex + 1)

        return if (portPart.toIntOrNull() in 1..65535) {
            hostPart to portPart
        } else {
            raw to ""
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val MODE_LIBRARY = "library"
        const val MODE_SAVE = "save"
        const val MODE_DOWNLOAD = "download"
        const val MODE_PROFILE = "profile"
    }
}
