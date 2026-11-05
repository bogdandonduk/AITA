package kz.aita

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

actual fun createStockScreenRecorder(): StockScreenRecorder =
    if (System.getProperty("os.name").startsWith("Windows", true) &&
        System.getProperty("os.arch").lowercase() in setOf("amd64", "x86_64")) WindowsStockScreenRecorder()
    else UnavailableStockScreenRecorder()

/** Only helper programs/models are written to disk. Frames stay in bounded process pipes and memory. */
internal class WindowsStockScreenRecorder : StockScreenRecorder {
    override val supported = true
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(StockCaptureState())
    override val state: StateFlow<StockCaptureState> = mutableState.asStateFlow()
    private val mutableFrames = MutableSharedFlow<StockScreenFrame>(extraBufferCapacity = 1)
    override val frames: SharedFlow<StockScreenFrame> = mutableFrames.asSharedFlow()
    private val epoch = AtomicInteger()
    private val pauseGeneration = AtomicInteger()
    @Volatile private var pauseRequested = false
    private val helperDirectory by lazy { extractHelpers() }
    private val processes = java.util.concurrent.ConcurrentHashMap.newKeySet<Process>()
    private val ocr = StockOcrProcess()
    @Volatile private var recordingProcess: Process? = null
    @Volatile private var closed = false
    @Volatile private var lastHeartbeat = 0L
    private var reader: Job? = null
    private var watchdog: Job? = null
    private val allowedWindows = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private var language = "en"

    override suspend fun windows(): List<StockCaptureWindow> = withContext(Dispatchers.IO) {
        if (closed) return@withContext emptyList()
        val process = try { launch("List") } catch (_: Exception) {
            fail("helper_unavailable"); return@withContext emptyList()
        }
        val output = Executors.newSingleThreadExecutor { task -> Thread(task, "aita-capture-list").apply { isDaemon = true } }
        try {
            val future = output.submit<String?> { process.inputStream.buffered().use { StockCaptureProtocol.readLine(it, 150_000) } }
            val line = future.get(12, TimeUnit.SECONDS) ?: error("capture_list_unavailable")
            val data = StockCaptureProtocol.parse(line)
            if (data["type"]?.jsonPrimitive?.content != "windows") error("capture_list_unavailable")
            val windowPids = mutableMapOf<String, Int>()
            val windows = (data["windows"] as? JsonArray).orEmpty().take(200).mapNotNull {
                val item = it as? JsonObject ?: return@mapNotNull null
                val id = item["id"]?.jsonPrimitive?.content.orEmpty()
                val title = item["title"]?.jsonPrimitive?.content.orEmpty().take(256)
                val pid = item["pid"]?.jsonPrimitive?.intOrNull ?: 0
                if (!id.matches(Regex("[0-9]{1,20}")) || title.isBlank() || pid <= 0) null
                else { windowPids[id] = pid; StockCaptureWindow(id, title) }
            }
            allowedWindows.clear(); allowedWindows.putAll(windowPids)
            windows.sortedBy { it.title.lowercase() }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { fail("source_unavailable"); emptyList() }
        finally { terminate(process); output.shutdownNow() }
    }

    override suspend fun start(windowId: String, languageTag: String) = withContext(Dispatchers.IO) {
        if (closed) return@withContext
        stop()
        if (!allowedWindows.containsKey(windowId)) { fail("source_unavailable"); return@withContext }
        pauseRequested = false
        language = languageTag.substringBefore('-').lowercase().take(3).takeIf { it.matches(Regex("[a-z]{2,3}")) } ?: "en"
        val token = epoch.incrementAndGet()
        mutableState.value = StockCaptureState(StockCaptureStatus.PAUSED, "ready")
        val process = try { launch("Record", windowId) } catch (_: Exception) {
            fail("helper_unavailable"); return@withContext
        }
        if (closed || token != epoch.get()) { terminate(process); return@withContext }
        recordingProcess = process
        lastHeartbeat = System.nanoTime()
        reader = scope.launch {
            try {
                process.inputStream.buffered(65536).use { input ->
                    var count = 0
                    while (isActive && token == epoch.get()) {
                        val line = StockCaptureProtocol.readLine(input) ?: break
                        val value = StockCaptureProtocol.parse(line)
                        lastHeartbeat = System.nanoTime()
                        when (value["type"]?.jsonPrimitive?.content) {
                            "state" -> {
                                val status = runCatching { StockCaptureStatus.valueOf(value["status"]!!.jsonPrimitive.content) }.getOrNull()
                                if (status != null) {
                                    if (status == StockCaptureStatus.PAUSED) { pauseRequested = false; pauseGeneration.incrementAndGet() }
                                    if (status == StockCaptureStatus.RECORDING && pauseRequested) continue
                                    val code = value["code"]?.jsonPrimitive?.contentOrNull?.takeIf { it in StockCaptureProtocol.messageCodes }
                                    mutableState.value = StockCaptureState(status, code, count)
                                    if (status in setOf(StockCaptureStatus.ERROR, StockCaptureStatus.STOPPED)) break
                                }
                            }
                            "frame" -> {
                                if (count >= 1800) { fail("capture_limit"); break } // 30 min minimum at 1 fps; review manageable batches.
                                val frameEpoch = epoch.get()
                                val framePause = pauseGeneration.get()
                                val w = value["width"]?.jsonPrimitive?.intOrNull ?: 0
                                val h = value["height"]?.jsonPrimitive?.intOrNull ?: 0
                                require(w > 0 && h > 0 && w.toLong() * h <= StockCaptureProtocol.MAX_PIXELS)
                                val source = StockScreenSource.valueOf(value["source"]!!.jsonPrimitive.content)
                                val words = if (source == StockScreenSource.OCR) {
                                    val png = value["png"]?.jsonPrimitive?.content ?: error("capture_image_missing")
                                    require(png.length <= 9_400_000)
                                    ocr.recognize(png, w, h, language)
                                } else StockCaptureProtocol.words(value["words"], w, h)
                                // Pause/Stop drops work already in flight before it reaches the review UI.
                                if (frameEpoch == epoch.get() && framePause == pauseGeneration.get() && !pauseRequested && token == epoch.get() && state.value.status == StockCaptureStatus.RECORDING) {
                                    count++
                                    if (words.isNotEmpty()) mutableFrames.emit(StockScreenFrame(
                                        id = "$token-${value["id"]!!.jsonPrimitive.content}",
                                        capturedAtMillis = value["at"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis(),
                                        sourceTitle = value["title"]?.jsonPrimitive?.content.orEmpty().take(256),
                                        words = words, source = source, width = w, height = h,
                                    ))
                                    mutableState.value = state.value.copy(frameCount = count,
                                        messageCode = if (words.isEmpty()) "no_readable_text" else null)
                                }
                                command("ack", process)
                            }
                        }
                    }
                    if (token == epoch.get() && state.value.status !in setOf(StockCaptureStatus.STOPPED, StockCaptureStatus.ERROR)) fail("capture_stopped")
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: StockOcrTimeout) { if (token == epoch.get()) fail("ocr_timeout") }
            catch (_: StockOcrUnavailable) { if (token == epoch.get()) fail("ocr_unavailable") }
            catch (_: Exception) { if (token == epoch.get()) fail("source_unavailable") }
            finally {
                terminate(process)
                if (recordingProcess === process) { recordingProcess = null; ocr.close() }
            }
        }
        watchdog = scope.launch {
            while (isActive && token == epoch.get() && process.isAlive) {
                delay(1000)
                // OCR is bounded separately; includes cold helper compilation + OCR startup.
                if (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - lastHeartbeat) > 25) {
                    fail("source_unresponsive"); terminate(process); ocr.close(); break
                }
            }
        }
    }

    override fun pause() {
        if (state.value.status == StockCaptureStatus.RECORDING) {
            pauseRequested = true; pauseGeneration.incrementAndGet()
            mutableState.value = state.value.copy(status = StockCaptureStatus.PAUSED, messageCode = "paused")
            command("pause")
        }
    }
    override fun resume() { if (!closed) { pauseRequested = false; pauseGeneration.incrementAndGet(); command("resume") } }
    override fun stop() {
        epoch.incrementAndGet()
        command("stop")
        reader?.cancel(); reader = null; watchdog?.cancel(); watchdog = null
        recordingProcess?.let(::terminate); recordingProcess = null
        ocr.close()
        mutableState.value = state.value.copy(status = StockCaptureStatus.STOPPED, messageCode = null)
    }
    override fun close() {
        if (closed) return
        closed = true; stop(); scope.cancel()
        processes.toList().forEach(::terminate)
        // No screen data was stored. The tiny executable helper copies can be removed now.
        if (helpersCreated) runCatching { helperDirectory.toFile().deleteRecursively() }
    }
    private fun fail(code: String) { mutableState.value = state.value.copy(status = StockCaptureStatus.ERROR, messageCode = code) }
    private fun command(command: String, process: Process? = recordingProcess) {
        if (process == null) return
        runCatching { synchronized(process) { process.outputStream.write((command + "\n").toByteArray()); process.outputStream.flush() } }
    }
    private fun launch(mode: String, window: String = "0"): Process {
        val executable = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32/WindowsPowerShell/v1.0/powershell.exe")
        require(executable.isFile)
        val args = listOf(executable.absolutePath, "-NoLogo", "-NoProfile", "-NonInteractive", "-STA", "-WindowStyle", "Hidden",
            "-ExecutionPolicy", "Bypass", "-File", helperDirectory.resolve("stock-capture-helper.ps1").toString(),
            "-Mode", mode, "-WindowId", window, "-Language", language, "-OwnerPid", ProcessHandle.current().pid().toString(),
            "-ExpectedPid", (allowedWindows[window] ?: 0).toString())
        check(!closed)
        return ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start().also {
            processes.add(it)
            if (closed) { terminate(it); error("capture_closed") }
        }
    }
    private fun terminate(process: Process) {
        runCatching { process.outputStream.close() }; runCatching { process.destroy() }
        runCatching { if (process.isAlive) process.destroyForcibly() }
        processes.remove(process)
    }
    @Volatile private var helpersCreated = false
    private fun extractHelpers(): Path {
        val dir = Files.createTempDirectory("aita-stock-capture-")
        for (name in listOf("stock-capture-helper.ps1", "stock-capture-helper.cs")) {
            javaClass.getResourceAsStream("/stock-capture/$name")!!.use { input -> Files.copy(input, dir.resolve(name)) }
        }
        helpersCreated = true
        return dir
    }
}

private class StockOcrTimeout : Exception()
private class StockOcrUnavailable : Exception()

/** Native OCR never loads into AITA's main JVM. A timeout/native crash only terminates this worker. */
private class StockOcrProcess {
    @Volatile private var process: Process? = null
    private var language: String? = null
    private var output: java.io.InputStream? = null
    private var executor: java.util.concurrent.ExecutorService? = null
    private val generation = AtomicInteger()
    fun recognize(png: String, width: Int, height: Int, tag: String): List<StockScreenWord> {
        val token = generation.get()
        val lang = StockCaptureProtocol.ocrLanguage(tag)
        if (process?.isAlive != true || language != lang) {
            disposeWorker()
            val data = try { extractModels() } catch (_: Exception) { throw StockOcrUnavailable() }
            val javaHome = File(System.getProperty("java.home"))
            val args = listOf(data.toString(), lang).map { java.util.Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) }
            val installed = System.getProperty("jpackage.app-path")?.let(::File)?.takeIf { it.isAbsolute && it.isFile && it.name.equals("AITA.exe", true) }
            val java = File(javaHome, "bin/javaw.exe").takeIf { it.isFile } ?: File(javaHome, "bin/java.exe")
            val classpath = System.getProperty("java.class.path").split(File.pathSeparator).joinToString(File.pathSeparator) { File(it).absolutePath }
            val command = if (installed != null) listOf(installed.absolutePath, "--aita-local-ocr") + args
                else listOf(java.absolutePath, "-Xmx320m", "-Djava.awt.headless=true", "-cp", classpath, "kz.aita.StockOcrWorker") + args
            val builder = ProcessBuilder(command)
            builder.environment()["JAVA_TOOL_OPTIONS"] = "-Xmx320m -Djava.awt.headless=true -XX:ActiveProcessorCount=2"
            builder.environment()["PATH"] = File(javaHome, "bin").absolutePath + File.pathSeparator + builder.environment()["PATH"].orEmpty()
            builder.environment()["OMP_THREAD_LIMIT"] = "1"
            synchronized(this) {
                if (generation.get() != token) throw StockOcrUnavailable()
                process = builder.redirectError(ProcessBuilder.Redirect.DISCARD).start()
                output = process!!.inputStream.buffered(65536)
                executor = Executors.newSingleThreadExecutor { task -> Thread(task, "aita-local-ocr").apply { isDaemon = true } }
                language = lang
            }
        }
        val future = synchronized(this) {
            if (generation.get() != token) throw StockOcrUnavailable()
            val child = process ?: throw StockOcrUnavailable()
            val read = output ?: throw StockOcrUnavailable()
            (executor ?: throw StockOcrUnavailable()).submit<String?> {
                child.outputStream.write((png + "\n").toByteArray(Charsets.US_ASCII)); child.outputStream.flush()
                StockCaptureProtocol.readLine(read, 1_600_000)
            }
        }
        try {
            val line = future.get(18, TimeUnit.SECONDS) ?: throw StockOcrUnavailable()
            val result = StockCaptureProtocol.parse(line)
            if (result["type"]?.jsonPrimitive?.content != "ocr") throw StockOcrUnavailable()
            return StockCaptureProtocol.words(result["words"], width, height)
        } catch (_: TimeoutException) { close(); throw StockOcrTimeout() }
        catch (_: Exception) { close(); throw StockOcrUnavailable() }
    }
    @Synchronized fun close() {
        generation.incrementAndGet(); disposeWorker()
    }
    @Synchronized private fun disposeWorker() {
        process?.let { runCatching { it.destroyForcibly() } }; process = null
        executor?.shutdownNow(); executor = null; output = null; language = null
    }
    private fun extractModels(): Path {
        val root = File(System.getProperty("user.home"), ".aita/stock-ocr/65727574").toPath()
        Files.createDirectories(root)
        val manifest = StockCaptureProtocol.parse(javaClass.getResourceAsStream("/stock-capture/tessdata/manifest.json")!!.bufferedReader().use { it.readText() })
        for (entry in manifest["models"]!!.jsonArray) {
            val item = entry.jsonObject
            val name = item["file"]!!.jsonPrimitive.content
            require(name.matches(Regex("[a-z_]+\\.traineddata")))
            val expected = item["sha256"]!!.jsonPrimitive.content
            val file = root.resolve(name)
            fun digest(path: Path): String = Files.newInputStream(path).use { input ->
                val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
                while (true) { val size = input.read(buffer); if (size < 0) break; hash.update(buffer, 0, size) }
                hash.digest().joinToString("") { "%02x".format(it) }
            }
            if (!Files.isRegularFile(file) || Files.size(file) != item["bytes"]!!.jsonPrimitive.long || digest(file) != expected) {
                val tmp = Files.createTempFile(root, "model-", ".tmp")
                try {
                    javaClass.getResourceAsStream("/stock-capture/tessdata/$name")!!.use { input -> Files.copy(input, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
                    require(digest(tmp) == expected)
                    Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                } finally { Files.deleteIfExists(tmp) }
            }
        }
        return root
    }
}
