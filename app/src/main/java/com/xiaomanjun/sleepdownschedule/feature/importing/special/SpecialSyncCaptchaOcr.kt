package com.xiaomanjun.sleepdownschedule.feature.importing.special

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 验证码识别入口：按配置分派。
 * - local + 新/旧模型：ddddocr 官方模型跑 ONNX Runtime；引擎与模型运行时从 GitHub
 *   下载（见 OcrEngineManager），未就绪时返回空文本回退手动输入
 * - local + mlkit：ML Kit 拉丁文本识别；pipeline so 与 assets 模型同样剥离打包，
 *   运行时下载后经路径注入 + addAssetPath 激活
 * - openai：OpenAI 兼容视觉接口（如硅基流动）
 *
 * 三种方式统一约束：验证码为 4 位英文字母与数字，识别前做字符范围初步筛选，失败返回
 * 空文本由调用方回退手动输入（自动重试最多三次）。
 */
object SpecialSyncCaptchaOcr {

    private const val TAG = "OcrEngine"

    private val mlKitRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(context: Context, bytes: ByteArray, config: SpecialSyncConfig): OcrOutcome {
        return when (config.ocrMode) {
            SpecialSyncOcrMode.OPENAI ->
                OcrOutcome(SpecialSyncOpenAiOcr.recognize(bytes, config), "云端接口")
            else -> when (config.ocrLocalModel) {
                SpecialSyncOcrMode.LocalModelMlkit -> recognizeWithMlKit(context, bytes)
                else -> OcrEngineManager.recognize(context, bytes, config.ocrLocalModel, config.ocrGpuEnabled)
            }
        }
    }

    private suspend fun recognizeWithMlKit(context: Context, bytes: ByteArray): OcrOutcome {
        if (!OcrEngineManager.isMlKitReady(context)) return OcrOutcome("", "CPU")
        if (!OcrEngineManager.activateMlKit(context)) {
            Log.w(TAG, "mlkit activate failed")
            return OcrOutcome("", "CPU")
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: run {
            Log.w(TAG, "mlkit: bitmap decode failed")
            return OcrOutcome("", "CPU")
        }
        return suspendCancellableCoroutine { cont ->
            mlKitRecognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { visionText ->
                    // 初步筛选：只保留 ASCII 字母与数字，验证码固定 4 位
                    val alnum = visionText.text.filter {
                        it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9'
                    }.take(4)
                    Log.d(TAG, "mlkit raw=${visionText.text.take(60)} result=$alnum")
                    cont.resume(OcrOutcome(alnum, "CPU"))
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "mlkit recognize failed", error)
                    cont.resume(OcrOutcome("", "CPU"))
                }
        }
    }
}
