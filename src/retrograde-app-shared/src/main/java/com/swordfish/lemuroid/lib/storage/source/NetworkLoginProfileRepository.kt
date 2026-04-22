package com.swordfish.lemuroid.lib.storage.source

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class NetworkProtocol {
    SMB,
    SFTP,
    WEBDAV,
    WEBDAV_HTTP,
}

data class NetworkLoginProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: NetworkProtocol = NetworkProtocol.SMB,
    val server: String,
    val username: String = "",
    val password: String = "",
) {
    companion object {
        fun listFromJson(json: String): List<NetworkLoginProfile> = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.getJSONObject(index)
                val server = obj.optString("server", "").trim()
                val name = obj.optString("name", "").trim()
                if (server.isBlank() || name.isBlank()) {
                    null
                } else {
                    NetworkLoginProfile(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = name,
                        protocol = runCatching {
                            NetworkProtocol.valueOf(obj.optString("protocol", NetworkProtocol.SMB.name))
                        }.getOrDefault(NetworkProtocol.SMB),
                        server = server,
                        username = obj.optString("username", ""),
                        password = obj.optString("password", ""),
                    )
                }
            }
        }.getOrDefault(emptyList())

        fun listToJson(profiles: List<NetworkLoginProfile>): String {
            val array = JSONArray()
            profiles.forEach { profile ->
                array.put(
                    JSONObject().apply {
                        put("id", profile.id)
                        put("name", profile.name)
                        put("protocol", profile.protocol.name)
                        put("server", profile.server)
                        put("username", profile.username)
                        put("password", profile.password)
                    },
                )
            }
            return array.toString()
        }
    }
}

class NetworkLoginProfileRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getProfiles(): List<NetworkLoginProfile> {
        val json = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return NetworkLoginProfile.listFromJson(json)
    }

    fun profilesFlow(): Flow<List<NetworkLoginProfile>> =
        bus.map { getProfiles() }.distinctUntilChanged()

    fun addOrUpdateProfile(profile: NetworkLoginProfile) {
        val normalized = profile.copy(
            name = profile.name.trim(),
            server = profile.server.trim(),
            username = profile.username.trim(),
        )
        if (normalized.name.isBlank() || normalized.server.isBlank()) return

        val profiles = getProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.id == normalized.id }
        if (index >= 0) {
            profiles[index] = normalized
        } else {
            profiles.add(normalized)
        }
        save(profiles)
    }

    fun removeProfile(id: String) {
        val profiles = getProfiles().toMutableList()
        profiles.removeAll { it.id == id }
        save(profiles)
    }

    fun rememberConnection(
        server: String,
        credentials: SourceCredentials?,
        protocol: NetworkProtocol = NetworkProtocol.SMB,
    ) {
        val normalizedServer = server.trim()
        val normalizedUsername = credentials?.username?.trim().orEmpty()
        if (normalizedServer.isBlank()) return

        val profiles = getProfiles().toMutableList()
        val existingIndex = profiles.indexOfFirst {
            it.protocol == protocol &&
                it.server.equals(normalizedServer, ignoreCase = true) &&
                it.username == normalizedUsername
        }

        val profile = if (existingIndex >= 0) {
            profiles[existingIndex].copy(
                protocol = protocol,
                server = normalizedServer,
                password = credentials?.password.orEmpty(),
            )
        } else {
            NetworkLoginProfile(
                name = normalizedServer.substringBefore(':').ifBlank { normalizedServer },
                protocol = protocol,
                server = normalizedServer,
                username = normalizedUsername,
                password = credentials?.password.orEmpty(),
            )
        }

        if (existingIndex >= 0) {
            profiles[existingIndex] = profile
        } else {
            profiles.add(profile)
        }
        save(profiles)
    }

    private fun save(profiles: List<NetworkLoginProfile>) {
        prefs.edit().putString(KEY_PROFILES, NetworkLoginProfile.listToJson(profiles)).apply()
        bus.tryEmit(Unit)
    }

    companion object {
        private const val PREFS_NAME = "network_login_profiles"
        private const val KEY_PROFILES = "profiles"
        private val bus = MutableSharedFlow<Unit>(replay = 1).also { it.tryEmit(Unit) }
    }
}