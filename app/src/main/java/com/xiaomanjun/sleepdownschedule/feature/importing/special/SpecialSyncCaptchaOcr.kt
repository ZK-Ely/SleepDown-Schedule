package com.xiaomanjun.sleepdownschedule.feature.importing.special

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 验证码识别入口：按配置分派。
 * - local：ML Kit 本地拉丁文本识别（打包模型，不依赖 Play 服务）
 * - openai：OpenAI 兼容视觉接口（如硅基流动）
 * 识别失败统一返回空串，由调用方回退到手动输入验证码。
 */
object SpecialSyncCaptchaOcr {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(bytes: ByteArray, config: SpecialSyncConfig): String {
        return when (config.ocrMode) {
            SpecialSyncOcrMode.OPENAI -> SpecialSyncOpenAiOcr.recognize(bytes, config)
            else -> recognizeLocally(bytes)
        }
    }

    private suspend fun recognizeLocally(bytes: ByteArray): String {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return ""
        return suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { visionText ->
                    val raw = visionText.text.filter { it.isLetterOrDigit() }
                    cont.resume(raw.take(6))
                }
                .addOnFailureListener { cont.resume("") }
        }
    }
}
