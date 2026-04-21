package com.swordfish.lemuroid.lib.storage.source

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class SourceType { LOCAL, SMB, ARCHIVE_ORG }

data class SourceCredentials(val username: String, val password: String)

/**
 * Represents a single user-configured ROM source: a local folder, SMB share, or Archive.org.
 */
data class RomSource(
    val id: String = UUID.randomUUID().toString(),
    val type: SourceType,
    val name: String,
    val path: String,
    val credentials: SourceCredentials? = null,
    /** If non-null, forces all .zip files from this source into this system (e.g. "gba"). */
    val platformHint: String? = null,
) {
    companion object {
        fun local(name: String, path: String, platformHint: String? = null) =
            RomSource(type = SourceType.LOCAL, name = name, path = path, platformHint = platformHint)

        fun smb(name: String, server: String, path: String, credentials: SourceCredentials? = null, platformHint: String? = null): RomSource {
            val normalizedPath = if (path.startsWith("/")) path else "/$path"
            return RomSource(type = SourceType.SMB, name = name, path = "smb://$server$normalizedPath", credentials = credentials, platformHint = platformHint)
        }

        fun archiveOrg() = RomSource(
            id = "builtin-archive-org",
            type = SourceType.ARCHIVE_ORG,
            name = "Archive.org",
            path = "https://archive.org",
        )

        fun listFromJson(json: String): List<RomSource> = runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val typeStr = obj.optString("type", "") 
                val type = runCatching { SourceType.valueOf(typeStr) }.getOrNull() ?: return@mapNotNull null
                val credentials = if (obj.has("username") && obj.optString("username").isNotBlank()) {
                    SourceCredentials(obj.getString("username"), obj.optString("password", ""))
                } else null
                RomSource(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    type = type,
                    name = obj.optString("name", ""),
                    path = obj.optString("path", ""),
                    credentials = credentials,
                    platformHint = obj.optString("platformHint", "").takeIf { it.isNotBlank() },
                )
            }
        }.getOrDefault(emptyList())

        fun listToJson(sources: List<RomSource>): String {
            val arr = JSONArray()
            sources.forEach { src ->
                val obj = JSONObject().apply {
                    put("id", src.id)
                    put("type", src.type.name)
                    put("name", src.name)
                    put("path", src.path)
                    src.credentials?.let {
                        put("username", it.username)
                        put("password", it.password)
                    }
                    src.platformHint?.let { put("platformHint", it) }
                }
                arr.put(obj)
            }
            return arr.toString()
        }
    }
}
