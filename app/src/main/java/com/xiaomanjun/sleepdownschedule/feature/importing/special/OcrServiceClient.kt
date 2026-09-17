package com.xiaomanjun.sleepdownschedule.feature.importing.special

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 主进程侧的 OCR 服务客户端：绑定 :ocr 进程的 [OcrService]，把验证码图片
 * 送过去识别。识别用的原生库与模型内存全部留在 :ocr 进程，空闲后该进程
 * 自杀，主进程内存足迹不受影响。
 */
internal object OcrServiceClient {

    private const val MSG_RECOGNIZE = 1
    private const val TimeoutMillis = 20_000L

    private class ReplyHandler : Handler(Looper.getMainLooper()) {
        var continuation: ((Bundle?) -> Unit)? = null
        override fun handleMessage(msg: Message) {
            val callback = continuation
            continuation = null
            callback?.invoke(msg.data)
        }
    }

    suspend fun recognize(
        context0: Context,
        bytes: ByteArray,
        model: String,
        gpu: Boolean
    ): OcrOutcome = withContext(Dispatchers.IO) {
        val context = context0.applicationContext
        if (!OcrEngineManager.isComponentReady(context, model)) {
            return@withContext OcrOutcome("", "CPU")
        }
        var connection: ServiceConnection? = null
        try {
            val reply: Bundle? = withTimeoutOrNull(TimeoutMillis) {
                suspendCancellableCoroutine { cont ->
                    val resumed = java.util.concurrent.atomic.AtomicBoolean(false)
                    fun resumeOnce(value: Bundle?) {
                        if (resumed.compareAndSet(false, true)) {
                            runCatching { cont.resume(value) }
                        }
                    }
                    val handler = ReplyHandler()
                    handler.continuation = { resumeOnce(it) }
                    val receiver = Messenger(handler)
                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {
                            runCatching {
                                val msg = Message.obtain(null, MSG_RECOGNIZE)
                                msg.data = Bundle().apply {
                                    putByteArray("image", bytes)
                                    putString("model", model)
                                    putBoolean("gpu", gpu)
                                }
                                msg.replyTo = receiver
                                Messenger(service).send(msg)
                            }.onFailure { resumeOnce(null) }
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {}
                    }
                    connection = conn
                    val bound = runCatching {
                        context.bindService(
                            Intent(context, OcrService::class.java),
                            conn,
                            Context.BIND_AUTO_CREATE
                        )
                    }.getOrElse { false }
                    if (!bound) resumeOnce(null)
                }
            }
            OcrOutcome(
                reply?.getString("text") ?: "",
                reply?.getString("device") ?: "CPU"
            )
        } catch (_: TimeoutCancellationException) {
            OcrOutcome("", "CPU")
        } catch (_: Throwable) {
            OcrOutcome("", "CPU")
        } finally {
            connection?.let { runCatching { context.unbindService(it) } }
        }
    }
}
