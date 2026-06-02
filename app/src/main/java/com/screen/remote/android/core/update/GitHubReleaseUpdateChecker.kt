package com.screen.remote.android.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

class GitHubReleaseUpdateChecker(
    private val owner: String,
    private val repo: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun check(
        currentVersion: String,
        channel: UpdateChannel,
    ): Result<GitHubReleaseInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val releases = fetchReleases()
                selectLatestRelease(
                    releases = releases,
                    currentVersion = currentVersion,
                    channel = channel,
                )
            }
        }

    private fun fetchReleases(): List<GitHubReleaseInfo> {
        val connection =
            (URL("https://api.github.com/repos/$owner/$repo/releases").openConnection() as HttpURLConnection)
                .apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Screen-Remote")
                }

        return connection.inputStream.bufferedReader().use { reader ->
            val root = json.parseToJsonElement(reader.readText()).jsonArray
            root.mapNotNull { element ->
                val obj = element.jsonObject
                val tagName = obj["tag_name"]?.jsonPrimitive?.content.orEmpty()
                if (tagName.isBlank()) return@mapNotNull null
                GitHubReleaseInfo(
                    tagName = tagName,
                    name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                    htmlUrl = obj["html_url"]?.jsonPrimitive?.content.orEmpty(),
                    prerelease = obj["prerelease"]?.jsonPrimitive?.booleanOrNull == true,
                    draft = obj["draft"]?.jsonPrimitive?.booleanOrNull == true,
                )
            }
        }
    }
}
