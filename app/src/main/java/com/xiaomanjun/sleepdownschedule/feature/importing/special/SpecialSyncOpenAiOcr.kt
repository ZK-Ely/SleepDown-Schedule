package com.xiaomanjun.sleepdownschedule.feature.importing.special

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * OpenAI 兼容 chat/completions 视觉模型验证码识别（参考硅基流动 API 文档）。
 * 请求：POST {baseUrl}/chat/completions，Authorization: Bearer {apiKey}，
 * 消息内容为 image_url(base64 data URI) + 提示词，要求模型只输出图中字母数字。
 * 仅在用户选择 OpenAI 兼容识别方式时使用；网络与解析失败由调用方按空结果兜底。
 */
object SpecialSyncOpenAiOcr {

    private const val PROMPT =
        "识别图片中的图形验证码。验证码只包含字母和数字。" +
            "只输出验证码字符本身，不要输出任何其他文字、标点或解释。"

    /** 返回识别出的字母数字（最多 6 位）；任何失败返回空串 */
    suspend fun recognize(bytes: ByteArray, config: SpecialSyncConfig): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = config.ocrApiKey.trim()
                if (apiKey.isEmpty()) return@runCatching ""
                val model = config.ocrModelName.trim()
                if (model.isEmpty()) return@runCatching ""
                val base = config.ocrApiBaseUrl.trim().trimEnd('/')
                if (base.isEmpty()) return@runCatching ""

                val imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val body = JSONObject().apply {
                    put("model", model)
                    put("temperature", 0)
                    put("max_tokens", 16)
                    put("stream", false)
                    put(
                        "messages",
                        org.json.JSONArray().put(
                            JSONObject().apply {
                                put("role", "user")
                                put(
                                    "content",
                                    org.json.JSONArray()
                                        .put(
                                            JSONObject().apply {
                                                put("type", "image_url")
                                                put(
                                                    "image_url",
                                                    JSONObject().put(
                                                        "url",
                                                        "data:image/png;base64,$imageBase64"
                                                    )
                                                )
                                            }
                                        )
                                        .put(
                                            JSONObject().apply {
                                                put("type", "text")
                                                put("text", PROMPT)
                                            }
                                        )
                                )
                            }
                        )
                    )
                }.toString()

                val resp = postJson("$base/chat/completions", body, apiKey)
                val json = JSONObject(resp)
                val content = json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                content.filter { it.isLetterOrDigit() }.take(6)
            }.getOrDefault("")
        }

    private fun postJson(url: String, body: String, apiKey: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        conn.requestMethod = "POST"
        conn.doOutput = true
        val payload = body.toByteArray(Charsets.UTF_8)
        conn.setFixedLengthStreamingMode(payload.size)
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.outputStream.use { it.write(payload) }
        val stream = if (conn.responseCode in 400..599) conn.errorStream ?: conn.inputStream else conn.inputStream
        val text = if ("gzip".equals(conn.contentEncoding, true)) {
            GZIPInputStream(stream).use { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }
        } else {
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        }
        if (conn.responseCode !in 200..299) {
            error("HTTP ${conn.responseCode}: ${text.take(200)}")
        }
        return text
    }
}
