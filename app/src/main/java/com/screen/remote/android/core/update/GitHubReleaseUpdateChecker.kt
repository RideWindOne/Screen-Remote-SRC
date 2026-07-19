package com.screen.remote.android.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val RELEASES_API_URL = "https://api.github.com/repos/XRSec/Screen-Remote/releases"

class GitHubReleaseUpdateChecker(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun check(
        currentVersion: String,
        channel: UpdateChannel,
    ): Result<GitHubReleaseInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                selectLatestRelease(
                    releases = fetchReleases(),
                    currentVersion = currentVersion,
                    channel = channel,
                )
            }
        }

    private fun fetchReleases(): List<GitHubReleaseInfo> {
        // 临时验证更新 UI 时，可注释真实请求并直接返回以下 mock：
        //return listOf(
        //    GitHubReleaseInfo(
        //        tagName = "99.0.0",
        //        name = "Screen Remote 99.0.0 (Mock)",
        //        htmlUrl = "https://github.com/XRSec/Screen-Remote/releases",
        //        prerelease = false,
        //        draft = false,
        //    ),
        //)

        val connection =
            (URL(RELEASES_API_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "Screen-Remote")
            }

        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                connection.errorStream?.close()
                throw IOException("GitHub Releases API returned HTTP $responseCode")
            }

            connection.inputStream.bufferedReader().use { reader ->
                json.parseToJsonElement(reader.readText()).jsonArray.mapNotNull { element ->
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
        } finally {
            connection.disconnect()
        }
    }
}
