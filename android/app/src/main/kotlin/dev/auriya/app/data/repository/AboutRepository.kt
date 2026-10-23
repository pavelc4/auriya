package dev.auriya.app.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val OWNER_LOGIN = "Pavelc4"
private const val REPO_NAME = "Auriya"
private const val APP_TAGLINE = "Technical documentation for Auriya and personal optimization experiments."

data class RepoInfo(
    val name: String,
    val description: String,
    val version: String,
)

data class OwnerInfo(
    val login: String,
    val name: String?,
    val bio: String?,
    val htmlUrl: String,
    val avatarUrl: String?,
)

data class Contributor(
    val login: String,
    val avatarUrl: String,
    val htmlUrl: String,
    val contributions: Int,
    val role: String = "Community Contributor",
)

class AboutRepository(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "auriya_about_cache"
        private const val KEY_OWNER_NAME = "owner_name"
        private const val KEY_OWNER_BIO = "owner_bio"
        private const val KEY_OWNER_AVATAR = "owner_avatar"
        private const val KEY_CONTRIBUTORS_JSON = "contributors_json"
        private const val KEY_LAST_FETCH = "last_fetch_time"
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    fun getCachedOwner(): OwnerInfo {
        val name = prefs.getString(KEY_OWNER_NAME, "Pavel") ?: "Pavel"
        val bio =
            prefs.getString(KEY_OWNER_BIO, "Rust daemon, eBPF telemetry, and Android Kotlin UI")
                ?: "Rust daemon, eBPF telemetry, and Android Kotlin UI"
        val avatar =
            prefs.getString(KEY_OWNER_AVATAR, "https://github.com/$OWNER_LOGIN.png")
                ?: "https://github.com/$OWNER_LOGIN.png"
        return OwnerInfo(
            login = OWNER_LOGIN,
            name = name,
            bio = bio,
            htmlUrl = "https://github.com/$OWNER_LOGIN",
            avatarUrl = avatar,
        )
    }

    fun saveOwner(owner: OwnerInfo) {
        prefs
            .edit()
            .putString(KEY_OWNER_NAME, owner.name)
            .putString(KEY_OWNER_BIO, owner.bio)
            .putString(KEY_OWNER_AVATAR, owner.avatarUrl)
            .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
            .apply()
    }

    fun getCachedContributors(): List<Contributor> {
        val json = prefs.getString(KEY_CONTRIBUTORS_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                Contributor(
                    login = obj.getString("login"),
                    avatarUrl = obj.getString("avatar_url"),
                    htmlUrl = obj.getString("html_url"),
                    contributions = obj.getInt("contributions"),
                    role = "Community Contributor",
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveContributors(list: List<Contributor>) {
        if (list.isEmpty()) return
        try {
            val array = JSONArray()
            for (c in list) {
                val obj =
                    JSONObject().apply {
                        put("login", c.login)
                        put("avatar_url", c.avatarUrl)
                        put("html_url", c.htmlUrl)
                        put("contributions", c.contributions)
                    }
                array.put(obj)
            }
            prefs.edit().putString(KEY_CONTRIBUTORS_JSON, array.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun shouldRefresh(): Boolean {
        val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0L)
        return (System.currentTimeMillis() - lastFetch) > CACHE_TTL_MS
    }

    suspend fun fetchRepoInfo(): RepoInfo =
        withContext(Dispatchers.IO) {
            val body =
                fetchJson("https://api.github.com/repos/$OWNER_LOGIN/$REPO_NAME")
                    ?: return@withContext RepoInfo(REPO_NAME, APP_TAGLINE, "2.0.0")
            try {
                val obj = JSONObject(body)
                RepoInfo(
                    name = obj.getString("name"),
                    description = APP_TAGLINE,
                    version = "2.0.0",
                )
            } catch (_: Exception) {
                RepoInfo(REPO_NAME, APP_TAGLINE, "2.0.0")
            }
        }

    suspend fun fetchOwnerInfo(): OwnerInfo =
        withContext(Dispatchers.IO) {
            val fallback =
                OwnerInfo(
                    login = OWNER_LOGIN,
                    name = "Pavel",
                    bio = "Rust daemon, eBPF telemetry, and Android Kotlin UI",
                    htmlUrl = "https://github.com/$OWNER_LOGIN",
                    avatarUrl = "https://github.com/$OWNER_LOGIN.png",
                )
            val body = fetchJson("https://api.github.com/users/$OWNER_LOGIN") ?: return@withContext fallback
            try {
                val obj = JSONObject(body)
                OwnerInfo(
                    login = obj.getString("login"),
                    name = obj.optString("name").takeIf { it.isNotBlank() } ?: "Pavel",
                    bio = obj.optString("bio").takeIf { it.isNotBlank() } ?: fallback.bio,
                    htmlUrl = obj.optString("html_url", "https://github.com/$OWNER_LOGIN"),
                    avatarUrl = obj.optString("avatar_url").takeIf { it.isNotBlank() } ?: fallback.avatarUrl,
                )
            } catch (_: Exception) {
                fallback
            }
        }

    suspend fun fetchContributors(): List<Contributor> =
        withContext(Dispatchers.IO) {
            val body =
                fetchJson("https://api.github.com/repos/$OWNER_LOGIN/$REPO_NAME/contributors")
                    ?: return@withContext emptyList()
            try {
                val jsonArray = JSONArray(body)
                List(jsonArray.length()) { i ->
                    val obj = jsonArray.getJSONObject(i)
                    Contributor(
                        login = obj.getString("login"),
                        avatarUrl = obj.getString("avatar_url"),
                        htmlUrl = obj.getString("html_url"),
                        contributions = obj.getInt("contributions"),
                        role = "Community Contributor",
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    private suspend fun fetchJson(url: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val connection =
                    (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "Auriya-App")
                        setRequestProperty("Accept", "application/vnd.github+json")
                        connectTimeout = 5000
                        readTimeout = 5000
                    }
                if (connection.responseCode == 200) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
}
