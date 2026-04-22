package com.swordfish.lemuroid.app.tv.settings

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.catalog.NetworkClient
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.smb.SmbClient
import com.swordfish.lemuroid.lib.storage.source.NetworkProtocol
import com.swordfish.lemuroid.lib.storage.source.SmbLoginProfile
import com.swordfish.lemuroid.lib.storage.source.SmbLoginProfileRepository
import com.swordfish.lemuroid.lib.storage.source.SourceCredentials as NetworkCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TV-optimized Activity for configuring SMB library source.
 * Uses standard Android EditText layout for reliable input handling.
 */
class TVNetworkConfigActivity : FragmentActivity() {
    private lateinit var titleText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var pathLabel: TextView
    
    private lateinit var savedLoginButton: Button
    private lateinit var protocolLabel: TextView
    private lateinit var protocolSpinner: Spinner
    private lateinit var serverInput: EditText
    private lateinit var portInput: EditText
    private lateinit var pathInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var statusText: TextView
    private lateinit var testButton: Button

    private val networkLoginProfileRepository by lazy { SmbLoginProfileRepository(this) }
    private val networkClient by lazy { NetworkClient(com.swordfish.lemuroid.app.mobile.feature.catalog.SmbClient()) }
    private var selectedProtocol: NetworkProtocol = NetworkProtocol.SMB

    private val mode: String by lazy {
        intent.getStringExtra(EXTRA_MODE) ?: MODE_LIBRARY
    }

    private val profileId: String? by lazy {
        intent.getStringExtra(EXTRA_PROFILE_ID)
    }

    private val sourceId: String? by lazy {
        intent.getStringExtra(EXTRA_SOURCE_ID)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_network_config)
        
        // Find views
        titleText = findViewById(R.id.network_title_text)
        descriptionText = findViewById(R.id.network_description_text)
        pathLabel = findViewById(R.id.network_path_label)
        savedLoginButton = findViewById(R.id.network_saved_login_button)
        protocolLabel = findViewById(R.id.network_protocol_label)
        protocolSpinner = findViewById(R.id.network_protocol_spinner)
        serverInput = findViewById(R.id.network_server_input)
        portInput = findViewById(R.id.network_port_input)
        pathInput = findViewById(R.id.network_path_input)
        usernameInput = findViewById(R.id.network_username_input)
        passwordInput = findViewById(R.id.network_password_input)
        statusText = findViewById(R.id.network_status_text)
        testButton = findViewById(R.id.network_test_button)

        configureTextsForMode()
        configureVisibilityForMode()
    configureProtocolSpinner()
        
        // Load existing values
        loadExistingConfig()
        
        // Set up buttons
        savedLoginButton.setOnClickListener { showSavedLoginPicker() }
        testButton.setOnClickListener { testConnection() }
        findViewById<Button>(R.id.network_save_button).setOnClickListener { saveAndFinish() }
        findViewById<Button>(R.id.network_cancel_button).setOnClickListener { finish() }
        
        // Request focus on first input
        serverInput.requestFocus()
    }
    
    private val KEY_RAW_PATH = "network_library_raw_path_ui_v2"

    private fun configureTextsForMode() {
        when (mode) {
            MODE_SAVE -> {
                titleText.setText(R.string.tv_network_save_title)
                descriptionText.setText(R.string.tv_network_save_description)
            }
            MODE_DOWNLOAD -> {
                titleText.setText(R.string.tv_network_download_title)
                descriptionText.setText(R.string.tv_network_download_description)
            }
            MODE_PROFILE -> {
                titleText.setText(R.string.tv_network_profile_title)
                descriptionText.setText(R.string.tv_network_profile_description)
            }
            MODE_EDIT_SOURCE -> {
                titleText.setText(com.swordfish.lemuroid.lib.R.string.smb_library_title)
                descriptionText.setText(com.swordfish.lemuroid.lib.R.string.smb_library_description)
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
        protocolLabel.visibility = if (isProfileMode) View.VISIBLE else View.GONE
        protocolSpinner.visibility = if (isProfileMode) View.VISIBLE else View.GONE
        testButton.visibility = View.VISIBLE
    }

    private fun configureProtocolSpinner() {
        val labels = listOf(
            getString(R.string.network_protocol_smb),
            getString(R.string.network_protocol_sftp),
            getString(R.string.network_protocol_webdav),
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        protocolSpinner.adapter = adapter
        protocolSpinner.setSelection(0)
        protocolSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedProtocol = when (position) {
                    1 -> NetworkProtocol.SFTP
                    2 -> NetworkProtocol.WEBDAV
                    else -> NetworkProtocol.SMB
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun loadExistingConfig() {
        val prefs = SharedPreferencesHelper.getSharedPreferences(this)

        when (mode) {
            MODE_SAVE -> {
                val current = prefs.getString(SharedPreferencesHelper.KEY_SAVE_LOCATION_URI, "") ?: ""
                val parsed = parseNetworkUri(current)
                selectedProtocol = parsed.protocol
                val (host, port) = splitHostAndPort(parsed.server)
                serverInput.setText(host)
                portInput.setText(port)
                pathInput.setText(normalizePath(parsed.path))
                usernameInput.setText(prefs.getString(SharedPreferencesHelper.KEY_SAVE_SMB_USERNAME, ""))
                passwordInput.setText(prefs.getString(SharedPreferencesHelper.KEY_SAVE_SMB_PASSWORD, ""))
            }
            MODE_DOWNLOAD -> {
                val current = prefs.getString(SharedPreferencesHelper.KEY_DOWNLOAD_SOURCE_ID, "") ?: ""
                val parsed = parseNetworkUri(current)
                selectedProtocol = parsed.protocol
                val (host, port) = splitHostAndPort(parsed.server)
                serverInput.setText(host)
                portInput.setText(port)
                pathInput.setText(normalizePath(parsed.path))
                usernameInput.setText(prefs.getString(SharedPreferencesHelper.KEY_DOWNLOAD_SMB_USERNAME, ""))
                passwordInput.setText(prefs.getString(SharedPreferencesHelper.KEY_DOWNLOAD_SMB_PASSWORD, ""))
            }
            MODE_PROFILE -> {
                val profile = networkLoginProfileRepository.getProfiles().firstOrNull { it.id == profileId }
                val (host, port) = splitHostAndPort(profile?.server.orEmpty())
                serverInput.setText(host)
                portInput.setText(port)
                usernameInput.setText(profile?.username.orEmpty())
                passwordInput.setText(profile?.password.orEmpty())
                selectedProtocol = profile?.protocol ?: NetworkProtocol.SMB
                protocolSpinner.setSelection(
                    when (selectedProtocol) {
                        NetworkProtocol.SMB -> 0
                        NetworkProtocol.SFTP -> 1
                        NetworkProtocol.WEBDAV,
                        NetworkProtocol.WEBDAV_HTTP -> 2
                    },
                )
            }
            MODE_EDIT_SOURCE -> {
                val source = com.swordfish.lemuroid.lib.storage.source.SourceRepository(this)
                    .getCustomSources().firstOrNull { it.id == sourceId }
                if (source != null) {
                    val parsed = parseNetworkUri(source.path)
                    selectedProtocol = parsed.protocol
                    val (host, port) = splitHostAndPort(parsed.server)
                    serverInput.setText(host)
                    portInput.setText(port)
                    pathInput.setText(normalizePath(parsed.path))
                    usernameInput.setText(source.credentials?.username.orEmpty())
                    passwordInput.setText(source.credentials?.password.orEmpty())
                }
            }
            else -> {
                val savedServer = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SERVER, "") ?: ""
                val (host, port) = splitHostAndPort(savedServer)
                serverInput.setText(host)
                portInput.setText(port)

                val rawPath = prefs.getString(KEY_RAW_PATH, null)
                if (rawPath != null) {
                    pathInput.setText(normalizePath(rawPath))
                } else {
                    val share = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SHARE, "")
                    val path = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PATH, "") ?: ""
                    val fullPath = if (!share.isNullOrBlank()) "/$share$path" else path
                    pathInput.setText(normalizePath(fullPath))
                }

                usernameInput.setText(prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_USERNAME, ""))
                passwordInput.setText(prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PASSWORD, ""))
            }
        }
    }

    private fun showSavedLoginPicker() {
        seedExistingProfiles()
        val profiles = networkLoginProfileRepository.getProfiles()
        if (profiles.isEmpty()) {
            Toast.makeText(this, getString(R.string.tv_network_saved_login_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val labels = profiles.map { profile ->
            val protocolName = getString(
                when (profile.protocol) {
                    NetworkProtocol.SMB -> R.string.network_protocol_smb
                    NetworkProtocol.SFTP -> R.string.network_protocol_sftp
                    NetworkProtocol.WEBDAV -> R.string.network_protocol_webdav
                    NetworkProtocol.WEBDAV_HTTP -> R.string.network_protocol_webdav_http
                },
            )
            if (profile.username.isNotBlank()) {
                "$protocolName • ${profile.name} (${profile.username}@${profile.server})"
            } else {
                "$protocolName • ${profile.name} (${profile.server})"
            }
        }.toTypedArray()

        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.sources_network_saved_profile_title)
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
        selectedProtocol = profile.protocol
        protocolSpinner.setSelection(
            when (profile.protocol) {
                NetworkProtocol.SMB -> 0
                NetworkProtocol.SFTP -> 1
                NetworkProtocol.WEBDAV,
                NetworkProtocol.WEBDAV_HTTP -> 2
            },
        )
        statusText.visibility = View.GONE
    }

    private fun testConnection() {
        val server = buildServerAddress()
        val path = normalizePath(pathInput.text.toString().trim())
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString()
        
        if (server.isBlank() || (mode != MODE_PROFILE && path.isBlank())) {
            statusText.text = getString(R.string.tv_network_enter_server_and_path)
            statusText.setTextColor(getColor(android.R.color.holo_red_light))
            statusText.visibility = View.VISIBLE
            return
        }
        
        statusText.text = getString(R.string.sources_smb_testing)
        statusText.setTextColor(getColor(android.R.color.white))
        statusText.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.IO).launch {
            val credentials = if (username.isNotBlank()) {
                NetworkCredentials(username, password)
            } else null

            val effectivePath = if (mode == MODE_PROFILE) "/" else path
            val protocol = selectedProtocol

            val result = networkClient.testConnection(protocol, server, effectivePath, credentials)
            
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    statusText.text = getString(R.string.tv_network_connection_success)
                    statusText.setTextColor(getColor(android.R.color.holo_green_light))
                } else {
                    val errorMsg = toFriendlyNetworkError(result.exceptionOrNull()?.message, protocol)
                    statusText.text = getString(R.string.tv_network_connection_failed, errorMsg)
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
                statusText.text = getString(R.string.tv_network_enter_server_and_path)
                statusText.setTextColor(getColor(android.R.color.holo_red_light))
                statusText.visibility = View.VISIBLE
                return
            }

            val existing = networkLoginProfileRepository.getProfiles().firstOrNull { it.id == profileId }
            val profileName = existing?.name ?: server.substringBefore(':').ifBlank { server }
            networkLoginProfileRepository.addOrUpdateProfile(
                SmbLoginProfile(
                    id = existing?.id ?: SmbLoginProfile(name = profileName, server = server).id,
                    name = profileName,
                    protocol = selectedProtocol,
                    server = server,
                    username = username,
                    password = password,
                ),
            )
            Toast.makeText(this, getString(R.string.tv_network_profile_configured_successfully), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        android.util.Log.e("ANTIGRAVITY", "UI Save: User Input Path: '$fullPath'")
        
        if (server.isBlank() || fullPath.isBlank()) {
            statusText.text = getString(R.string.tv_network_enter_server_and_path)
            statusText.setTextColor(getColor(android.R.color.holo_red_light))
            statusText.visibility = View.VISIBLE
            return
        }
        
        val networkUri = buildNetworkLocationUri(selectedProtocol, server, fullPath)

        // Parse SMB share only for legacy SMB library mode.
        val pathSegments = fullPath.removePrefix("/").split("/")
        val shareName = pathSegments.firstOrNull() ?: ""
        val subPath = if (pathSegments.size > 1) {
            "/" + pathSegments.drop(1).joinToString("/")
        } else {
            ""
        }
        
        if (mode == MODE_EDIT_SOURCE) {
            val repo = com.swordfish.lemuroid.lib.storage.source.SourceRepository(this)
            val existing = repo.getCustomSources().firstOrNull { it.id == sourceId }
            if (existing != null) {
                val credentials = if (username.isNotBlank()) NetworkCredentials(username, password) else null
                repo.updateSource(existing.copy(path = networkUri, credentials = credentials))
                rememberLogin(server, username, password)
                com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler.scheduleLibrarySync(this)
                Toast.makeText(this, getString(R.string.tv_network_configured_successfully), Toast.LENGTH_SHORT).show()
            }
            finish()
            return
        }

        val prefs = SharedPreferencesHelper.getSharedPreferences(this)
        when (mode) {
            MODE_SAVE -> {
                prefs.edit().apply {
                    putString(SharedPreferencesHelper.KEY_SAVE_LOCATION_URI, networkUri)
                    putString(SharedPreferencesHelper.KEY_SAVE_SMB_USERNAME, username)
                    putString(SharedPreferencesHelper.KEY_SAVE_SMB_PASSWORD, password)
                    apply()
                }
                rememberLogin(server, username, password)
                Toast.makeText(this, getString(R.string.tv_network_save_configured_successfully), Toast.LENGTH_SHORT).show()
            }
            MODE_DOWNLOAD -> {
                prefs.edit().apply {
                    putString(SharedPreferencesHelper.KEY_DOWNLOAD_SOURCE_ID, networkUri)
                    putString(SharedPreferencesHelper.KEY_DOWNLOAD_SMB_USERNAME, username)
                    putString(SharedPreferencesHelper.KEY_DOWNLOAD_SMB_PASSWORD, password)
                    apply()
                }
                rememberLogin(server, username, password)
                Toast.makeText(this, getString(R.string.tv_network_download_configured_successfully), Toast.LENGTH_SHORT).show()
            }
            else -> {
                if (shareName.isBlank()) {
                    statusText.text = getString(R.string.tv_network_path_must_start_with_share)
                    statusText.setTextColor(getColor(android.R.color.holo_red_light))
                    statusText.visibility = View.VISIBLE
                    return
                }

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

                if (mode == MODE_PROFILE) {
                    rememberLogin(server, username, password)
                }
                LibraryIndexScheduler.scheduleLibrarySync(this)
                Toast.makeText(this, getString(R.string.tv_network_configured_successfully), Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    private fun rememberLogin(server: String, username: String, password: String) {
        networkLoginProfileRepository.rememberConnection(
            server,
            if (username.isNotBlank()) NetworkCredentials(username, password) else null,
            protocol = selectedProtocol,
        )
    }

    private fun seedExistingProfiles() {
        // Profiles are managed explicitly by the network manager.
    }

    private fun normalizePath(path: String): String {
        if (path.isBlank()) return path
        return if (path.startsWith("/")) path else "/$path"
    }

    private fun toFriendlyNetworkError(raw: String?, protocol: NetworkProtocol): String {
        val message = raw?.trim().orEmpty()
        if (message.isBlank()) return getString(R.string.sources_network_test_unknown_error)

        val lower = message.lowercase()
        return when {
            lower.contains("requires username/password") || lower.contains("auth fail") || lower.contains("authentication") ->
                "Identifiants invalides ou manquants pour ${protocol.name}."
            lower.contains("missing smb share name") ->
                "Aucun partage SMB n'est sélectionné. Vérifiez le chemin (ex: /games/roms)."
            lower.contains("status_bad_network_name") || lower.contains("bad_network_name") ->
                "Partage SMB introuvable. Vérifiez le nom du partage au début du chemin."
            lower.contains("timeout") ->
                "Connexion expirée. Vérifiez l'adresse serveur, le port et le réseau."
            else -> message
        }
    }

    private fun parseNetworkUri(uri: String): ParsedNetworkUri {
        if (!isNetworkUri(uri)) {
            return ParsedNetworkUri(NetworkProtocol.SMB, "", "")
        }

        val parsed = runCatching { Uri.parse(uri) }.getOrNull()
            ?: return ParsedNetworkUri(NetworkProtocol.SMB, "", "")

        val protocol = when (parsed.scheme?.lowercase()) {
            "sftp" -> NetworkProtocol.SFTP
            "davs" -> NetworkProtocol.WEBDAV
            "dav" -> NetworkProtocol.WEBDAV_HTTP
            else -> NetworkProtocol.SMB
        }
        val authority = parsed.authority.orEmpty()
        val path = parsed.path.orEmpty()
        return ParsedNetworkUri(protocol, authority, path)
    }

    private fun isNetworkUri(uri: String): Boolean {
        val lower = uri.lowercase()
        return lower.startsWith("smb://") || lower.startsWith("sftp://") ||
            lower.startsWith("dav://") || lower.startsWith("davs://")
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

    private fun buildNetworkLocationUri(protocol: NetworkProtocol, server: String, path: String): String {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val scheme = when (protocol) {
            NetworkProtocol.SMB -> "smb"
            NetworkProtocol.SFTP -> "sftp"
            NetworkProtocol.WEBDAV -> "davs"
            NetworkProtocol.WEBDAV_HTTP -> "dav"
        }
        return "$scheme://$server$normalizedPath"
    }

    private data class ParsedNetworkUri(
        val protocol: NetworkProtocol,
        val server: String,
        val path: String,
    )

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_SOURCE_ID = "source_id"
        const val MODE_LIBRARY = "library"
        const val MODE_SAVE = "save"
        const val MODE_DOWNLOAD = "download"
        const val MODE_PROFILE = "profile"
        const val MODE_EDIT_SOURCE = "edit_source"
    }
}
