package com.xiaomanjun.sleepdownschedule.feature.importing.special

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import dalvik.system.BaseDexClassLoader

/** 单次验证码识别结果：文本（4 位英文字母，失败为空串）+ 所用加速设备描述 */
data class OcrOutcome(val text: String, val device: String)

/**
 * 本地验证码识别引擎：ddddocr 官方模型跑在 ONNX Runtime 上。
 *
 * 宿主 APK 只打包 ONNX Runtime 的 Java 绑定（约 110KB）；原生库（libonnxruntime /
 * libonnxruntime4j_jni）与模型文件（新/旧两套 onnx + 字符集 json）全部托管在 GitHub
 * Releases，首次使用时按需下载到应用私有目录，运行时用 System.load 加载
 * （受限 SELinux 环境回退 memfd 方案）。
 *
 * 资产清单（发布时上传到 Releases 根目录，文件名固定）：
 * - onnxruntime-<abi>.so            主推理库（从官方 AAR jni/<abi>/ 解出）
 * - onnxruntime4j_jni-<abi>.so      Java 绑定 JNI 桥
 * - common.onnx / common.json       ddddocr 新模型 + 字符集
 * - common_old.onnx / common_old.json  ddddocr 旧模型 + 字符集
 * <abi> ∈ arm64-v8a / armeabi-v7a / x86_64 / x86
 */
internal object OcrEngineManager {

    private const val TAG = "OcrEngine"

    /** 资产托管仓库（GitHub raw 直链）；文件放在仓库 ocr/ 目录，文件名固定 */
    private const val ReleaseBaseUrl =
        "https://github.com/ZK-Ely/SleepDown-Schedule/raw/main/ocr"

    /** 引擎资产版本：托管内容更新时递增，客户端会自动重新下载引擎 */
    private const val EngineVersion = "1.0.0"

    private const val OrtVersion = "1.22.0"
    private const val MarkerFile = "engine_version.txt"

    private const val ModelNewOnnx = "common.onnx"
    private const val ModelNewCharset = "common.json"
    private const val ModelOldOnnx = "common_old.onnx"
    private const val ModelOldCharset = "common_old.json"

    private val json = Json { ignoreUnknownKeys = true }

    private val loadState = AtomicBoolean(false)

    /** memfd 加载时保活的 fd，防止 load 完成前被回收 */
    private val heldFds = mutableListOf<Int>()

    private val sessionMutex = Mutex()
    private var session: OrtSession? = null
    private var sessionKey: String? = null
    private var charset: List<String> = emptyList()

    /** 验证码字符约束：仅 4 位英文字母，CTC 解码时只在 {blank} ∪ 字母列中选最大值 */
    private var letterIndices: IntArray = IntArray(0)

    /** 最近一次推理使用的加速设备描述（测试按钮展示用） */
    @Volatile
    internal var lastDevice: String = "CPU"
        private set

    private val mlKitLoadState = AtomicBoolean(false)
    private var injectedAssetsPath: String? = null

    // ---------- 资产路径与状态 ----------

    private fun engineDir(context: Context): File = File(context.filesDir, "ocr_engine")

    private fun primaryAbi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    private fun ortSo(context: Context): File = File(engineDir(context), "libonnxruntime.so")

    private fun ortJniSo(context: Context): File = File(engineDir(context), "libonnxruntime4j_jni.so")

    private fun mlKitSo(context: Context): File = File(engineDir(context), "libmlkit_google_ocr_pipeline.so")

    private fun mlKitAssetsZip(context: Context): File = File(engineDir(context), "mlkit-assets.zip")

    private fun onnxFile(context: Context, model: String): File =
        File(engineDir(context), if (model == SpecialSyncOcrMode.LocalModelOld) ModelOldOnnx else ModelNewOnnx)

    private fun charsetFile(context: Context, model: String): File =
        File(engineDir(context), if (model == SpecialSyncOcrMode.LocalModelOld) ModelOldCharset else ModelNewCharset)

    /** 引擎原生库是否已下载且版本匹配 */
    internal fun isEngineReady(context: Context): Boolean {
        if (engineDir(context).resolve(MarkerFile).takeIf { it.isFile } == null) return false
        val marker = engineDir(context).resolve(MarkerFile).readText().trim()
        return marker == EngineVersion &&
            ortSo(context).let { it.isFile && it.length() > 0L } &&
            ortJniSo(context).let { it.isFile && it.length() > 0L }
    }

    /** 指定模型（onnx + 字符集）是否已下载；内置 ML Kit 模式无需任何下载，恒为就绪 */
    internal fun isModelReady(context: Context, model: String): Boolean {
        if (model == SpecialSyncOcrMode.LocalModelMlkit) return true
        return onnxFile(context, model).let { it.isFile && it.length() > 0L } &&
            charsetFile(context, model).let { it.isFile && it.length() > 0L }
    }

    /** ML Kit 组件（pipeline so + assets 模型包）是否已下载 */
    internal fun isMlKitReady(context: Context): Boolean =
        mlKitSo(context).let { it.isFile && it.length() > 0L } &&
            mlKitAssetsZip(context).let { it.isFile && it.length() > 0L }

    /** 统一就绪判定：所选模型需要的组件全部在位 */
    internal fun isComponentReady(context: Context, model: String): Boolean = when (model) {
        SpecialSyncOcrMode.LocalModelMlkit -> isMlKitReady(context)
        else -> isEngineReady(context) && isModelReady(context, model)
    }

    /**
     * 下载全部缺失资产：引擎 so（按设备 ABI）+ 所选模型 onnx + 字符集。
     * 已存在的文件跳过；重复调用安全（幂等）。
     */
    internal suspend fun ensureReady(context: Context, model: String): Unit = withContext(Dispatchers.IO) {
        val dir = engineDir(context)
        dir.mkdirs()
        if (model == SpecialSyncOcrMode.LocalModelMlkit) {
            val abi = primaryAbi()
            if (!mlKitSo(context).isFile || mlKitSo(context).length() == 0L) {
                download("$ReleaseBaseUrl/mlkit-$abi.so", mlKitSo(context))
            }
            if (!mlKitAssetsZip(context).isFile || mlKitAssetsZip(context).length() == 0L) {
                download("$ReleaseBaseUrl/mlkit-assets.zip", mlKitAssetsZip(context))
            }
            return@withContext
        }
        if (!isEngineReady(context)) {
            val abi = primaryAbi()
            download("$ReleaseBaseUrl/onnxruntime-$abi.so", ortSo(context))
            download("$ReleaseBaseUrl/onnxruntime4j_jni-$abi.so", ortJniSo(context))
            dir.resolve(MarkerFile).writeText(EngineVersion)
            loadState.set(false)
        }
        val (onnxUrl, charsetUrl) = if (model == SpecialSyncOcrMode.LocalModelOld) {
            "$ReleaseBaseUrl/$ModelOldOnnx" to "$ReleaseBaseUrl/$ModelOldCharset"
        } else {
            "$ReleaseBaseUrl/$ModelNewOnnx" to "$ReleaseBaseUrl/$ModelNewCharset"
        }
        if (!isModelReady(context, model)) {
            download(onnxUrl, onnxFile(context, model))
            download(charsetUrl, charsetFile(context, model))
        }
    }

    /** 首次下载的体量描述，用于设置页提示；无下载需求时返回空串 */
    internal fun pendingSizeHint(context: Context, model: String): String {
        val parts = mutableListOf<String>()
        when (model) {
            SpecialSyncOcrMode.LocalModelMlkit -> {
                if (!isMlKitReady(context)) parts.add("引擎约 10MB、模型约 1MB")
            }
            else -> {
                if (!isEngineReady(context)) parts.add("引擎约 18MB")
                if (!isModelReady(context, model)) {
                    parts.add(if (model == SpecialSyncOcrMode.LocalModelOld) "旧模型约 13MB" else "新模型约 52MB")
                }
            }
        }
        return parts.joinToString("、")
    }

    /** 已下载组件占用空间（字节）；内置模型不占额外空间 */
    internal fun installedSizeBytes(context: Context): Long =
        engineDir(context).walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /** 卸载组件：删除引擎目录与已下载模型，回到未安装状态 */
    internal suspend fun uninstall(context: Context): Unit = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            try {
                session?.close()
            } catch (_: Throwable) {
            }
            session = null
            sessionKey = null
            charset = emptyList()
            loadState.set(false)
            mlKitLoadState.set(false)
            injectedAssetsPath = null
            engineDir(context).deleteRecursively()
        }
    }

    /**
     * 激活 ML Kit 运行环境：注入 ClassLoader 原生检索路径 → System.load 加载
     * pipeline so → 反射 addAssetPath 把下载的模型包挂到应用 AssetManager。
     * 必须在首次使用 TextRecognition API 之前调用。未下载或注入失败返回 false。
     */
    internal fun activateMlKit(context: Context): Boolean {
        synchronized(this) {
            val so = mlKitSo(context)
            val zip = mlKitAssetsZip(context)
            if (!so.isFile || so.length() == 0L || !zip.isFile || zip.length() == 0L) return false
            try {
                injectNativeLibraryPath(context)
            } catch (inject: Throwable) {
                Log.w(TAG, "mlkit inject native path failed", inject)
            }
            if (!mlKitLoadState.get()) {
                try {
                    System.load(so.absolutePath)
                    mlKitLoadState.set(true)
                } catch (first: Throwable) {
                    Log.w(TAG, "mlkit direct load failed, trying memfd", first)
                    try {
                        loadViaMemfd(so)
                        mlKitLoadState.set(true)
                    } catch (second: Throwable) {
                        Log.e(TAG, "mlkit memfd load failed", second)
                        return false
                    }
                }
            }
            if (injectedAssetsPath != zip.absolutePath) {
                try {
                    val assets = context.applicationContext.assets
                    val addMethod = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
                        .apply { isAccessible = true }
                    val cookie = addMethod.invoke(assets, zip.absolutePath) as? Int ?: 0
                    if (cookie == 0) {
                        Log.e(TAG, "addAssetPath returned 0 for ${zip.absolutePath}")
                        return false
                    }
                    injectedAssetsPath = zip.absolutePath
                    Log.i(TAG, "mlkit assets injected: ${zip.absolutePath}")
                } catch (e: Throwable) {
                    Log.e(TAG, "addAssetPath failed", e)
                    return false
                }
            }
            return true
        }
    }

    private fun download(url: String, target: File) {
        Log.i(TAG, "download $url -> ${target.name}")
        val tmp = File(target.parentFile, target.name + ".tmp")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output, 1 shl 16) }
            }
            if (tmp.length() <= 0L) throw IllegalStateException("下载内容为空")
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } finally {
            connection.disconnect()
            if (tmp.exists() && !tmp.renameTo(target)) tmp.delete()
        }
    }

    // ---------- 原生库加载 ----------

    /**
     * 加载 ONNX Runtime 原生库。必须在首次触达 OrtEnvironment 之前调用，
     * 因为 OrtEnvironment 静态块会 System.loadLibrary，而我们的库不在默认检索路径里。
     * 步骤：注入私有目录到 ClassLoader 检索路径 → 按依赖序 System.load 预加载
     * → 之后的 System.loadLibrary 按名命中已加载库。
     */
    private fun ensureNativeLoaded(context: Context): Boolean {
        if (loadState.get()) return true
        synchronized(this) {
            if (loadState.get()) return true
            val so = ortSo(context)
            val jniSo = ortJniSo(context)
            if (!so.isFile || !jniSo.isFile) return false
            try {
                injectNativeLibraryPath(context)
            } catch (inject: Throwable) {
                Log.w(TAG, "inject native path failed", inject)
            }
            try {
                System.load(so.absolutePath)
                System.load(jniSo.absolutePath)
                loadState.set(true)
                return true
            } catch (first: Throwable) {
                Log.w(TAG, "direct System.load failed, falling back to memfd", first)
            }
            try {
                loadViaMemfd(so)
                loadViaMemfd(jniSo)
                loadState.set(true)
                return true
            } catch (second: Throwable) {
                Log.e(TAG, "memfd load failed", second)
                return false
            }
        }
    }

    /**
     * 把引擎目录插入 BaseDexClassLoader 的 nativeLibraryDirectories 首位并重建
     * nativeLibraryPathElements，使 System.loadLibrary 能按名找到下载的 so。
     * （参考 bugly/Tinker 的动态 so 方案，SillyBoy 同款思路）
     */
    private fun injectNativeLibraryPath(context: Context) {
        val classLoader = OcrEngineManager::class.java.classLoader as? BaseDexClassLoader ?: return
        val pathList = BaseDexClassLoader::class.java.getDeclaredField("pathList")
            .apply { isAccessible = true }
            .get(classLoader)
        val pathListClass = pathList.javaClass
        val nativeDirsField = pathListClass.getDeclaredField("nativeLibraryDirectories")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val nativeDirs = nativeDirsField.get(pathList) as MutableList<File>
        val dir = engineDir(context)
        if (nativeDirs.none { it.absolutePath == dir.absolutePath }) {
            nativeDirs.add(0, dir)
        }
        val combined = mutableListOf<File>()
        combined.addAll(nativeDirs)
        runCatching {
            val systemField = pathListClass.getDeclaredField("systemNativeLibraryDirectories")
                .apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            combined.addAll(systemField.get(pathList) as List<File>)
        }
        // 优先尝试 makePathElements（各版本签名不同），失败则直接构造 NativeLibraryElement[]。
        // 打印实际签名，便于在未见过的系统版本上诊断。
        val candidates = pathListClass.declaredMethods.filter { it.name == "makePathElements" }
        candidates.forEach { method ->
            Log.i(TAG, "makePathElements sig: ${method.parameterTypes.joinToString() { it.name }}")
        }
        var elements: Any? = null
        for (method in candidates) {
            val types = method.parameterTypes
            if (types.isEmpty() || types[0] != List::class.java) continue
            elements = runCatching {
                when {
                    types.size == 1 -> method.invoke(null, combined)
                    types.size == 2 && types[1] == File::class.java -> method.invoke(null, combined, null)
                    types.size == 3 && types[1] == List::class.java ->
                        method.invoke(null, combined, mutableListOf<java.io.IOException>(), classLoader)
                    else -> null
                }
            }.getOrNull()
            if (elements != null) break
        }
        if (elements == null) {
            val elementClass = Class.forName("dalvik.system.DexPathList\$NativeLibraryElement")
            val constructor = elementClass.getDeclaredConstructor(File::class.java)
                .apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val array = java.lang.reflect.Array.newInstance(elementClass, combined.size)
            combined.forEachIndexed { index, file ->
                java.lang.reflect.Array.set(array, index, constructor.newInstance(file))
            }
            elements = array
            Log.i(TAG, "makePathElements unavailable, built NativeLibraryElement[] directly")
        }
        pathListClass.getDeclaredField("nativeLibraryPathElements")
            .apply { isAccessible = true }
            .set(pathList, elements)
        Log.i(TAG, "native library path injected: ${dir.absolutePath}")
    }

    /** Android 10+ 的 W^X 政策可能拒绝直接加载；memfd 匿名节点不在 app_data_file 域内 */
    private fun loadViaMemfd(file: File) {
        check(Build.VERSION.SDK_INT >= 29) { "memfd requires API 29" }
        val bytes = file.readBytes()
        val descriptor = Os.memfd_create(file.name, 0)
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) {
            Os.write(descriptor, buffer)
        }
        val pfd = ParcelFileDescriptor.dup(descriptor)
        val fd = pfd.detachFd()
        synchronized(heldFds) { heldFds.add(fd) }
        System.load("/proc/self/fd/$fd")
    }

    // ---------- 会话与推理 ----------

    /**
     * 本地识别一张验证码图片。引擎或模型未就绪时返回空串，由调用方回退手动输入。
     * 识别逻辑与 ddddocr 官方一致：灰度化 → 高 64 等比缩放 → (x/255-0.5)/0.5 归一化
     * → CTC 贪心解码（去 blank、去相邻重复）。
     * @param gpu 开启后尝试 NNAPI（GPU/APU）加速，失败自动回退 CPU
     */
    internal suspend fun recognize(
        context: Context,
        imageBytes: ByteArray,
        model: String,
        gpu: Boolean = false
    ): OcrOutcome {
        if (model == SpecialSyncOcrMode.LocalModelMlkit) return OcrOutcome("", lastDevice)
        if (!isEngineReady(context) || !isModelReady(context, model)) return OcrOutcome("", lastDevice)
        if (!ensureNativeLoaded(context)) return OcrOutcome("", lastDevice)
        return withContext(Dispatchers.IO) {
            sessionMutex.withLock {
                try {
                    val environment = OrtEnvironment.getEnvironment()
                    val activeSession = ensureSession(environment, context, model, gpu)
                        ?: return@withLock OcrOutcome("", lastDevice)
                    OcrOutcome(runInference(activeSession, imageBytes), lastDevice)
                } catch (error: Throwable) {
                    Log.w(TAG, "local recognize failed", error)
                    OcrOutcome("", lastDevice)
                }
            }
        }
    }

    private fun ensureSession(
        environment: OrtEnvironment,
        context: Context,
        model: String,
        gpu: Boolean
    ): OrtSession? {
        val key = "$model-$gpu"
        if (session != null && sessionKey == key) return session
        session?.close()
        session = null
        val definition = parseCharset(charsetFile(context, model)) ?: return null
        val path = onnxFile(context, model).absolutePath
        var usedNnapi = false
        session = if (gpu) {
            val options = OrtSession.SessionOptions()
            val created = try {
                // 禁用 NNAPI 的 CPU 参考后端：设备没有 GPU/NPU 驱动时这里会直接失败，
                // 回退 CPU——保证显示的加速设备是真实存在的硬件而非静默 CPU 兜底
                options.addNnapi(
                    java.util.EnumSet.of(ai.onnxruntime.providers.NNAPIFlags.CPU_DISABLED)
                )
                environment.createSession(path, options)
                    .also { usedNnapi = true; Log.i(TAG, "session created with NNAPI (non-CPU accelerator)") }
            } catch (nnapiError: Throwable) {
                Log.w(TAG, "NNAPI init failed, falling back to CPU", nnapiError)
                try {
                    environment.createSession(path)
                } catch (cpuError: Throwable) {
                    options.close()
                    throw cpuError
                }
            }
            runCatching { options.close() }
            created
        } else {
            environment.createSession(path)
        }
        sessionKey = key
        charset = definition.charset
        // 字符集预筛选：验证码含英文字母与数字，允许列 = blank(0) + ASCII 字母/数字列
        letterIndices = definition.charset
            .withIndex()
            .filter { (index, text) -> index == 0 || (text.length == 1 && text[0].isAsciiAlnum()) }
            .map { it.index }
            .toIntArray()
        lastDevice = if (usedNnapi) "GPU/NPU (NNAPI)" else "CPU"
        return session
    }

    private fun Char.isAsciiAlnum(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    @Serializable
    private data class CharsetDef(
        val word: Boolean = false,
        val image: List<Long> = listOf(-1L, 64L),
        val channel: Long = 1L,
        val charset: List<String> = emptyList()
    )

    private fun parseCharset(file: File): CharsetDef? = runCatching {
        json.decodeFromString(CharsetDef.serializer(), file.readText())
    }.getOrNull()

    private fun runInference(activeSession: OrtSession, imageBytes: ByteArray): String {
        val (buffer, shape) = preprocess(imageBytes) ?: return ""
        OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), buffer, shape).use { tensor ->
            val inputName = activeSession.inputNames.firstOrNull() ?: return ""
            activeSession.run(mapOf(inputName to tensor)).use { result ->
                val output = result.get(0) as OnnxTensor
                // CTC 输出扁平后是 [seq][classes]，逐行 argmax（兼容 [seq,1,C] / [1,seq,C] 两种布局）
                val classCount = output.info.shape.last().toInt()
                val buffer = output.floatBuffer
                val rowCount = buffer.remaining() / classCount
                // 初步筛选：只在 {blank} ∪ 英文字母列里取最大值，字符间隔帧仍可正确输出 blank
                val allowed = letterIndices.takeIf { it.isNotEmpty() }
                    ?: IntArray(classCount) { it }
                val builder = StringBuilder()
                var last = 0
                for (rowIndex in 0 until rowCount) {
                    val base = rowIndex * classCount
                    var best = allowed[0]
                    var bestValue = Float.NEGATIVE_INFINITY
                    for (candidateIndex in allowed) {
                        val value = buffer.get(base + candidateIndex)
                        if (value > bestValue) {
                            bestValue = value
                            best = candidateIndex
                        }
                    }
                    if (best != 0 && best != last) {
                        val text = charset.getOrNull(best)
                        if (!text.isNullOrEmpty()) builder.append(text)
                    }
                    last = best
                }
                // 验证码固定为 4 位英文字母与数字
                return builder.toString().take(4)
            }
        }
    }

    /** 解码 + 灰度（PIL Rec.601 系数）+ 归一化，输出 (1,1,H,W) float 张量 */
    private fun preprocess(imageBytes: ByteArray): Pair<FloatBuffer, LongArray>? {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
        val targetHeight = 64
        val scaledWidth = (bitmap.width.toLong() * targetHeight / bitmap.height)
            .toInt().coerceIn(1, 4096)
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, targetHeight, true)
        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        val buffer = ByteBuffer.allocateDirect(width * height * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val gray = (r * 299 + g * 587 + b * 114) / 1000
            buffer.put(((gray / 255f) - 0.5f) / 0.5f)
        }
        buffer.rewind()
        return Pair(buffer, longArrayOf(1, 1, height.toLong(), width.toLong()))
    }
}
