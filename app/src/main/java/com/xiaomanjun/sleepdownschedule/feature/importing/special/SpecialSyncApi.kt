package com.xiaomanjun.sleepdownschedule.feature.importing.special

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/** 教务系统单条课程记录（kbList 元素）：一次授课占用 = 某周某天某节段 */
data class SpecialSyncCourseItem(
    val kcmc: String,
    val teaxms: String,
    val jxcdmc: String,
    val skrq: String,
    val qssj: String,
    val jssj: String,
    val zc: Int,
    val xq: Int,
    val sknrjj: String,
    val szxqmc: String,
    val sectionStart: Int,
    val sectionEnd: Int,
) {
    companion object {
        fun listFrom(json: String): List<SpecialSyncCourseItem> {
            val root = JSONObject(json)
            val arr = root.optJSONObject("data")?.optJSONArray("kbList")
                ?: root.optJSONArray("kbList")
                ?: org.json.JSONArray()
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                fun text(key: String): String = o.optString(key, "").takeIf { it != "null" } ?: ""
                val ps = text("ps").trim().toIntOrNull() ?: 1
                val pe = text("pe").trim().toIntOrNull() ?: ps
                var start = ps.coerceIn(1, 20)
                var end = pe.coerceIn(start, 20)
                if (end == start) {
                    // jcdm2 兜底："1,2,3" 这样的连续节次编码
                    val parts = text("jcdm2").split(",").mapNotNull { it.trim().toIntOrNull() }
                    if (parts.size > 1 && parts.last() in start..20) end = parts.last()
                }
                SpecialSyncCourseItem(
                    kcmc = text("kcmc"),
                    teaxms = text("teaxms"),
                    jxcdmc = text("jxcdmc"),
                    skrq = text("skrq"),
                    qssj = text("qssj"),
                    jssj = text("jssj"),
                    zc = o.optInt("zc", 0),
                    xq = o.optInt("xq", 0),
                    sknrjj = text("sknrjj"),
                    szxqmc = text("szxqmc"),
                    sectionStart = start,
                    sectionEnd = end
                )
            }
        }
    }
}

/**
 * 广中医教务系统（/dev-api 前缀）同步客户端。
 * 登录要点：RSA(PKCS#1 v1.5) 密码 + 图形验证码（verifyCode 文本 + yzmCode 会话时间戳）+ token 请求头。
 * 网络层使用 HttpURLConnection，与项目 AI 导入的零依赖风格一致。
 */
class SpecialSyncApi(private val baseUrl: String = DefaultBaseUrl) {

    companion object {
        const val DefaultBaseUrl = "https://wjw.gzucm.edu.cn"
        private const val PUBLIC_KEY =
            "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKoR8mX0rGKLqzcWmOzbfj64K8ZIgOdH" +
                "nzkXSOVOZbFu/TJhZ7rFAN+eaGkl3C4buccQd/EjEsj9ir7ijT7h96MCAwEAAQ=="
        private const val UA = "Mozilla/5.0 (Linux; Android 14) SleepDownSpecialSync/1.0"
        private const val TokenPrefs = "special_sync_token"
    }

    var token: String? = null
        private set

    /** 验证码会话时间戳，与当前验证码图片绑定，登录时需原样回传 */
    var yzmCode: Long = 0
        private set

    private lateinit var tokenPrefs: android.content.SharedPreferences

    fun attach(context: android.content.Context) {
        tokenPrefs = context.applicationContext.getSharedPreferences(TokenPrefs, android.content.Context.MODE_PRIVATE)
        token = tokenPrefs.getString("token", null)
    }

    fun clearToken() {
        token = null
        if (this::tokenPrefs.isInitialized) tokenPrefs.edit().remove("token").apply()
    }

    suspend fun getCaptcha(): ByteArray = withContext(Dispatchers.IO) {
        yzmCode = System.currentTimeMillis()
        val bytes = httpGet("$baseUrl/dev-api/appapi/captcha/getCaptchaCode?yzmcode=$yzmCode")
        require(bytes.isNotEmpty()) { "验证码获取失败" }
        bytes
    }

    data class LoginOutcome(val ok: Boolean, val message: String)

    suspend fun login(username: String, password: String, verifyCode: String): LoginOutcome =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("username", username)
                put("password", encryptPassword(password))
                put("code", "")
                put("appid", JSONObject.NULL)
                put("yzmCode", yzmCode.toString())
                put("verifyCode", verifyCode)
            }.toString()
            val resp = postJson("/dev-api/appapi/applogin", body, token = null)
            val json = JSONObject(resp)
            if (json.optInt("code", 0) == 200 && json.optJSONObject("user") != null) {
                token = json.getJSONObject("user").optStringOrNull("token")
                if (this@SpecialSyncApi::tokenPrefs.isInitialized) {
                    tokenPrefs.edit().putString("token", token).apply()
                }
                LoginOutcome(true, "登录成功")
            } else {
                LoginOutcome(false, json.optStringOrNull("msg") ?: "登录失败")
            }
        }

    /** 用保存的 token 恢复会话；失败时清空 token 返回 false */
    suspend fun tryResume(): Boolean = withContext(Dispatchers.IO) {
        val saved = token ?: return@withContext false
        try {
            val resp = String(httpGet("$baseUrl/dev-api/appapi/getInfo", token = saved), Charsets.UTF_8)
            val json = JSONObject(resp)
            if (json.optInt("code", 0) == 200 && json.optJSONObject("user") != null) return@withContext true
        } catch (_: Exception) {
        }
        clearToken()
        false
    }

    suspend fun loadKb(): List<SpecialSyncCourseItem> = withContext(Dispatchers.IO) {
        val t = token ?: error("未登录")
        val resp = postJson("/dev-api/appapi/Studentkb/data", "{}", t)
        val json = JSONObject(resp)
        if (json.optInt("code", 0) == 200) {
            SpecialSyncCourseItem.listFrom(resp)
        } else {
            error(json.optStringOrNull("msg") ?: "课表加载失败")
        }
    }

    private fun encryptPassword(plain: String): String {
        val keyBytes = Base64.decode(PUBLIC_KEY, Base64.DEFAULT)
        val pub = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, pub)
        return Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun httpGet(url: String, token: String? = null): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", UA)
        if (token != null) conn.setRequestProperty("token", token)
        // 未手动设置 Accept-Encoding 时 HttpURLConnection 会自动协商 gzip 并透明解压
        return conn.inputStream.use { it.readBytes() }
    }

    private fun postJson(path: String, body: String, token: String?): String {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.requestMethod = "POST"
        conn.doOutput = true
        val payload = body.toByteArray(Charsets.UTF_8)
        conn.setFixedLengthStreamingMode(payload.size)
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("User-Agent", UA)
        if (token != null) conn.setRequestProperty("token", token)
        conn.outputStream.use { it.write(payload) }
        return BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        val s = optString(key, "")
        return if (s.isEmpty() || s == "null") null else s
    }
}
