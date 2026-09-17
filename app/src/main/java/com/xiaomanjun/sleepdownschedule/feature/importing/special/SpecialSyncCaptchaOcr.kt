package com.xiaomanjun.sleepdownschedule.feature.importing.special

import android.content.Context

/**
 * 验证码识别入口：按配置分派。
 * - openai：OpenAI 兼容视觉接口（如硅基流动），纯网络请求，直接在主进程执行
 * - 本地（onnx 新/旧模型、ML Kit）：全部经 [OcrServiceClient] 发往 :ocr 进程
 *   识别——原生库加载后无法卸载，模型 arena 释放后 allocator 仍保留内存，
 *   只有放进独立进程、用完杀进程，才能让主进程保持与原版一致的内存足迹
 *
 * 统一约束：验证码为 4 位英文字母与数字，失败返回空文本由调用方回退手动输入
 * （自动重试最多三次）。
 */
object SpecialSyncCaptchaOcr {

    suspend fun recognize(context: Context, bytes: ByteArray, config: SpecialSyncConfig): OcrOutcome {
        return when (config.ocrMode) {
            SpecialSyncOcrMode.OPENAI ->
                OcrOutcome(SpecialSyncOpenAiOcr.recognize(bytes, config), "云端接口")
            else -> OcrServiceClient.recognize(
                context,
                bytes,
                config.ocrLocalModel,
                config.ocrGpuEnabled
            )
        }
    }
}
