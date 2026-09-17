package com.xiaomanjun.sleepdownschedule.feature.importing.special

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import kotlinx.coroutines.runBlocking

/**
 * 独立 :ocr 进程的验证码识别服务。
 *
 * 为什么放子进程：libonnxruntime / ML Kit pipeline 这类原生库 System.load 后
 * 无法卸载（.so mmap 常驻），ONNX 会话关闭后 arena 归还 allocator 仍会保留
 * 大块内存——这些足迹会永远留在主进程里。把识别整体搬进 :ocr 进程，
 * 空闲后直接杀进程，全部内存归还系统，主进程保持与原版一致。
 *
 * 协议：客户端发 MSG_RECOGNIZE（data: image/model/gpu，replyTo 回信 Messenger），
 * 服务端回 data: text/device。
 */
class OcrService : Service() {

    private var worker: Messenger? = null
    private var workThread: HandlerThread? = null
    private var idleKiller: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread("OcrService").apply { start() }
        workThread = thread
        worker = Messenger(Handler(thread.looper) { msg ->
            handleRequest(msg)
            true
        })
    }

    private fun handleRequest(msg: Message) {
        val replyTo = msg.replyTo ?: return
        val bytes = msg.data?.getByteArray("image") ?: ByteArray(0)
        val model = msg.data?.getString("model") ?: SpecialSyncOcrMode.LOCAL
        val gpu = msg.data?.getBoolean("gpu") ?: false
        val outcome = runCatching {
            runBlocking {
                when (model) {
                    SpecialSyncOcrMode.LocalModelMlkit ->
                        OcrNativeRecognizer.recognizeWithMlKit(applicationContext, bytes)
                    else -> OcrEngineManager.recognize(applicationContext, bytes, model, gpu)
                }
            }
        }.getOrElse { OcrOutcome("", "CPU") }
        val reply = Message.obtain()
        reply.what = msg.what
        reply.arg1 = msg.arg1
        reply.data = Bundle().apply {
            putString("text", outcome.text)
            putString("device", outcome.device)
        }
        runCatching { replyTo.send(reply) }
    }

    override fun onBind(intent: Intent?): IBinder {
        cancelIdleKill()
        return requireNotNull(worker?.binder)
    }

    override fun onRebind(intent: Intent?) {
        cancelIdleKill()
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // 最后一个客户端解绑后闲置一段时间即自杀，把原生内存全部归还系统
        cancelIdleKill()
        val mainHandler = Handler(Looper.getMainLooper())
        val killer = Runnable {
            idleKiller = null
            stopSelf()
            Process.killProcess(Process.myPid())
        }
        idleKiller = killer
        mainHandler.postDelayed(killer, IdleKillDelayMillis)
        return true
    }

    private fun cancelIdleKill() {
        idleKiller?.let { killer ->
            Handler(Looper.getMainLooper()).removeCallbacks(killer)
            idleKiller = null
        }
    }

    override fun onDestroy() {
        cancelIdleKill()
        workThread?.quitSafely()
        workThread = null
        worker = null
        super.onDestroy()
    }

    companion object {
        private const val IdleKillDelayMillis = 10_000L
    }
}
