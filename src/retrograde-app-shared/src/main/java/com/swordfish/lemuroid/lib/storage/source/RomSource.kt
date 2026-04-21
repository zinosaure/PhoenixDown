package com.swordfish.lemuroid.lib.storage.source

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Represents a ROM source (Archive.org, Local folder, SMB share, etc.)
 *
 * This is the canonical type shared between the storage layer (retrograde-app-shared)
 * and the UI layer (lemuroid-app). UIs in lemuroid-app extend SourceRepository and
 * use these types directly.
 */
data class RomSource(
    val id: String,
    val type: SourceType,
    val name: String,
    val path: String,
    val credentials: SourceCredentials? = null,
) {
    companion object {
        private fun newId(prefix: String): String = "${prefix}_${UUID.randomUUID()}"

        fun archiveOrg() = RomSource(
            id = "archive_org",
            type = SourceType.ARCHIVE_ORG,
            name = "Archive.org",
            path = "https://archive.org",
        )

        fun local(name: String, uri: String) = RomSource(
            id = newId("local"),
            type = SourceType.LOCAL,
            name = name,
            path = uri,
        )

        fun smb(
            name: String,
            server: String,
            path: String,
            credentials: SourceCredentials? = null,
        ): RomSource {
            val normalizedPath = "/" + path.trimStart('/')
            return RomSource(
                id = newId("smb"),
                type = SourceType.SMB,
                name = name,
                path = "smb://$server$normalizedPath",
                credentials = credentials,
            )
        }

        fun webdav(
            name: String,
            url: String,
            credentials: SourceCredentials? = null,
        ): RomSource {
            val normalizedUrl = if (url.startsWith("http")) url else "http://$url"
            return RomSource(
                id = newId("webdav"),
                type = SourceType.WEBDAV,
                name = name,
                path = normalizedUrl,
                credentials = credentials,
            )
        }

        fun sftp(
            name: String,
            server: String,
            path: String,
            credentials: SourceCredentials? = null,
        ): RomSource {
            val normalizedPath = "/" + path.trimStart('/')
            return RomSource(
                id = newId("sftp"),
                type = SourceType.SFTP,
                name = name,
                path = "sftp://$server$normalizedPath",
                credentials = credentials,
            )
        }

        // -----------------------------------------------------------------------
        // JSON serialization (org.json — compatible with existing Gson format)
        // -----------------------------------------------------------------------

        internal fun fromJson(obj: JSONObject): RomSource? {
            return try {
                val type = SourceType.valueOf(obj.getString("type"))
                val credObj = obj.optJSONObject("credentials")
                val credentials = credObj?.let {
                    SourceCredentials(
                        username = it.optString("username", ""),
                        password = it.optString("password", ""),
                    )
                }
                RomSource(
                    id = obj.getString("id"),
                    type = type,
                    name = obj.getString("name"),
                    path = obj.getString("path"),
                    credentials = credentials,
                )
            } catch (_: Exception) {
                null
            }
        }

        internal fun toJson(source: RomSource): JSONObject {
            return JSONObject().apply {
                put("id", source.id)
                put("type", source.type.name)
                put("name", source.name)
                put("path", source.path)
                if (source.credentials != null) {
                    put(
                        "credentials",
                        JSONObject().apply {
                            put("username", source.credentials.username)
                            put("password", source.credentials.password)
                        },
                    )
                } else {
                    put("credentials", JSONObject.NULL)
                }
            }
        }

        internal fun listFromJson(json: String): List<RomSource> {
            return try {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { i ->
                    array.optJSONObject(i)?.let { fromJson(it) }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        internal fun listToJson(sources: List<RomSource>): String {
            val array = JSONArray()
            sources.forEach { array.put(toJson(it)) }
            return array.toString()
        }
    }
}

/** Type of ROM source. */
enum class SourceType {
    ARCHIVE_ORG,
    LOCAL,
    SMB,
    WEBDAV,
    SFTP,
}

/** Authentication credentials used for network sources (SMB, WebDAV, SFTP). */
data class SourceCredentials(
    val username: String,
    val password: String,
)
