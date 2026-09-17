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
 * 仅在 :ocr 进程内使用的 ML Kit 识别实现。
 * ML Kit 的 pipeline so 与识别类一旦加载便常驻，因此相关代码只允许被
 * OcrService 触达，主进程永不加载（保持与原版一致的内存足迹）。
 */
internal object OcrNativeRecognizer {

    private const val TAG = "OcrEngine"

    private val mlKitRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognizeWithMlKit(context: Context, bytes: ByteArray): OcrOutcome {
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
