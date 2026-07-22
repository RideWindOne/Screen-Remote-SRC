package com.screen.remote.android.feature.session.ui

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.screen.remote.android.core.common.util.compat.readAtMostBytesCompat
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.infrastructure.adb.connection.AdbBridge
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import com.screen.remote.android.infrastructure.adb.shell.AdbShellManager
import dadb.AdbShellPacket
import dadb.AdbShellStream
import dadb.ID_CLOSE_STDIN
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object SessionManagementAdbConnection {
    fun current(): AdbConnection? = AdbBridge.getConnection()
}

internal sealed interface ManagementShellPacket {
    data class Output(val text: String) : ManagementShellPacket

    data class Exit(val code: Int) : ManagementShellPacket
}

internal class ManagementShellStream(
    private val stream: AdbShellStream,
) {
    fun read(): ManagementShellPacket =
        when (val packet = stream.read()) {
            is AdbShellPacket.StdOut -> ManagementShellPacket.Output(String(packet.payload))
            is AdbShellPacket.StdError -> ManagementShellPacket.Output(String(packet.payload))
            is AdbShellPacket.Exit -> ManagementShellPacket.Exit(packet.payload.firstOrNull()?.toInt() ?: 0)
        }

    fun write(text: String) {
        stream.write(text)
    }

    fun closeInput() {
        stream.write(ID_CLOSE_STDIN)
    }

    fun close() {
        stream.close()
    }
}

internal suspend fun openManagementShellStream(): ManagementShellStream? =
    SessionManagementAdbConnection
        .current()
        ?.openShellStream("")
        ?.let(::ManagementShellStream)

internal suspend fun executeManagementShell(command: String): Result<String> {
    val connection =
        SessionManagementAdbConnection.current()
            ?: return Result.failure(IllegalStateException(ManagementTexts.Files.NO_ADB_CONNECTION_AVAILABLE.get()))

    return AdbShellManager.execute(
        connection = connection,
        command = command,
        retryOnFailure = false,
    )
}

@SuppressLint("SdCardPath")
internal fun navigateFileBrowserUp(path: String): String =
    when (val normalizedPath = normalizeRemotePath(path)) {
        "/" -> "/sdcard"
        "/sdcard" -> "/"
        else -> parentRemotePath(normalizedPath)
    }

internal fun parentRemotePath(path: String): String {
    val normalizedPath = normalizeRemotePath(path)
    return normalizedPath.substringBeforeLast("/", "/").ifBlank { "/" }
}

internal fun joinRemotePath(
    parent: String,
    child: String,
): String =
    when (val normalizedParent = normalizeRemotePath(parent)) {
        "/" -> "/${child.trimStart('/')}"
        else -> "$normalizedParent/${child.trimStart('/')}"
    }

internal data class RemotePathBreadcrumbItem(
    val label: String,
    val path: String,
)

internal fun buildRemotePathBreadcrumb(path: String): List<RemotePathBreadcrumbItem> {
    val normalizedPath = normalizeRemotePath(path)
    if (normalizedPath == "/") {
        return listOf(RemotePathBreadcrumbItem(label = "/", path = "/"))
    }

    val parts = normalizedPath.trim('/').split("/").filter { it.isNotEmpty() }
    val items = mutableListOf(RemotePathBreadcrumbItem(label = "/", path = "/"))
    var currentPath = ""
    parts.forEach { part ->
        currentPath = if (currentPath.isBlank()) "/$part" else "$currentPath/$part"
        items += RemotePathBreadcrumbItem(label = part, path = currentPath)
    }
    return items
}

internal fun normalizeRemotePath(path: String): String {
    val trimmedPath = path.trim()
    if (trimmedPath.isBlank() || trimmedPath == "/") {
        return "/"
    }

    val rootedPath =
        if (trimmedPath.startsWith("/")) {
            trimmedPath
        } else {
            "/$trimmedPath"
        }

    return rootedPath
        .replace(Regex("/{2,}"), "/")
        .trimEnd('/')
        .ifBlank { "/" }
}

internal fun quoteShellArg(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

internal suspend fun runShellAction(
    command: String,
    successMessage: String,
): Result<String> =
    executeManagementShell(command)
        .map { output ->
            output.trim().ifBlank { successMessage }
        }

internal suspend fun copyRemoteEntries(
    entries: List<RemoteFileEntry>,
    targetDirectory: String,
): Result<String> {
    val command = buildRemoteCopyCommand(entries, targetDirectory)
        .getOrElse { error -> return Result.failure(error) }
    return runShellAction(
        command = command,
        successMessage = ManagementTexts.Files.COPY_COMPLETE.get(),
    )
}

internal suspend fun moveRemoteEntries(
    entries: List<RemoteFileEntry>,
    targetDirectory: String,
): Result<String> {
    val command = buildRemoteMoveCommand(entries, targetDirectory)
        .getOrElse { error -> return Result.failure(error) }
    return runShellAction(
        command = command,
        successMessage = ManagementTexts.Files.MOVE_COMPLETE.get(),
    )
}

internal fun buildRemoteCopyCommand(
    entries: List<RemoteFileEntry>,
    targetDirectory: String,
): Result<String> = buildRemoteTransferCommand(entries, targetDirectory, command = "cp -R")

internal fun buildRemoteMoveCommand(
    entries: List<RemoteFileEntry>,
    targetDirectory: String,
): Result<String> = buildRemoteTransferCommand(entries, targetDirectory, command = "mv")

private fun buildRemoteTransferCommand(
    entries: List<RemoteFileEntry>,
    targetDirectory: String,
    command: String,
): Result<String> =
    runCatching {
        val moving = command == "mv"
        require(entries.isNotEmpty()) {
            if (moving) {
                ManagementTexts.Files.THERE_NO_FILES_MOVE.get()
            } else {
                ManagementTexts.Files.THERE_NO_FILES_COPY.get()
            }
        }
        val normalizedTargetDirectory = normalizeRemotePath(targetDirectory)
        entries.forEach { entry ->
            val normalizedSource = normalizeRemotePath(entry.fullPath)
            require(
                !entry.isDirectory ||
                    (normalizedTargetDirectory != normalizedSource && !normalizedTargetDirectory.startsWith("$normalizedSource/")),
            ) {
                if (moving) {
                    ManagementTexts.Files.FOLDER_CAN_T_BE_MOVED_INTO_ITSELF.get()
                } else {
                    ManagementTexts.Files.FOLDER_CAN_T_BE_COPIED_INTO_ITSELF.get()
                }
            }
        }

        val destinationChecks =
            entries.joinToString(separator = "; ") { entry ->
                val destination = joinRemotePath(normalizedTargetDirectory, entry.name)
                "[ ! -e ${quoteShellArg(destination)} ] || { echo ${quoteShellArg(ManagementTexts.Files.DESTINATION_ALREADY_EXISTS.format(entry.name))} >&2; exit 1; }"
            }
        val transferCommands =
            entries.joinToString(separator = "; ") { entry ->
                "$command ${quoteShellArg(entry.fullPath)} ${quoteShellArg(normalizedTargetDirectory)}"
            }
        "$destinationChecks; $transferCommands"
    }

internal suspend fun downloadRemoteEntriesToDocument(
    context: Context,
    entries: List<RemoteFileEntry>,
    destinationUri: Uri,
): Result<String> {
    val connection =
        SessionManagementAdbConnection.current()
            ?: return Result.failure(IllegalStateException(ManagementTexts.Files.NO_ADB_CONNECTION_AVAILABLE.get()))

    return withContext(Dispatchers.IO) {
        runCatching {
            require(entries.isNotEmpty()) {
                ManagementTexts.Files.THERE_NO_FILES_DOWNLOAD.get()
            }
            val resolver = context.contentResolver
            val output =
                resolver.openOutputStream(destinationUri, "w")
                    ?: error(ManagementTexts.Files.COULDN_T_OPEN_DESTINATION_FILE.get())
            if (entries.size == 1 && !entries.single().isDirectory) {
                output.use { destination ->
                    val entry = entries.single()
                    val localFile = getPreparedLocalFile(context, entry)
                    connection.pullFile(entry.fullPath, localFile.absolutePath).getOrThrow()
                    localFile.inputStream().use { input -> input.copyTo(destination) }
                }
            } else {
                ZipOutputStream(output).use { zip ->
                    suspend fun addEntry(
                        entry: RemoteFileEntry,
                        relativePath: String,
                    ) {
                        val zipPath = if (relativePath.isBlank()) entry.name else "$relativePath/${entry.name}"
                        if (entry.isDirectory) {
                            zip.putNextEntry(ZipEntry("$zipPath/"))
                            zip.closeEntry()
                            val snapshot = loadFileBrowserSnapshot(entry.fullPath)
                            snapshot.errorMessage?.let(::error)
                            snapshot.entries.forEach { child -> addEntry(child, zipPath) }
                        } else {
                            val localFile = getPreparedLocalFile(context, entry)
                            connection.pullFile(entry.fullPath, localFile.absolutePath).getOrThrow()
                            zip.putNextEntry(ZipEntry(zipPath))
                            localFile.inputStream().use { input -> input.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                    entries.forEach { entry -> addEntry(entry, relativePath = "") }
                }
            }
            ManagementTexts.Files.SAVED_ITEM_S.format(entries.size)
        }
    }
}

internal fun buildArchiveDownloadName(entries: List<RemoteFileEntry>): String {
    val nameParts =
        entries
            .take(3)
            .mapNotNull { entry ->
                val sourceName =
                    if (entry.isDirectory) {
                        entry.name
                    } else {
                        entry.name.substringBeforeLast('.', entry.name)
                    }
                sourceName
                    .trim()
                    .replace(Regex("[\\s\\\\/:*?\"<>|]+"), "_")
                    .trim('_')
                    .take(64)
                    .takeIf(String::isNotBlank)
            }
    val baseName = nameParts.joinToString("_").ifBlank { "ScreenRemote_files" }
    val moreSuffix = if (entries.size > 3) "_more" else ""
    return "$baseName$moreSuffix.zip"
}

internal suspend fun uploadLocalFilesToRemoteDirectory(
    context: Context,
    sourceUris: List<Uri>,
    targetDirectory: String,
): Result<String> {
    val connection =
        SessionManagementAdbConnection.current()
            ?: return Result.failure(IllegalStateException(ManagementTexts.Files.NO_ADB_CONNECTION_AVAILABLE.get()))

    return withContext(Dispatchers.IO) {
        runCatching {
            require(sourceUris.isNotEmpty()) {
                ManagementTexts.Files.NO_FILES_WERE_SELECTED_UPLOAD.get()
            }
            val sources =
                sourceUris.map { uri ->
                    val name = resolveContentDisplayName(context, uri)
                    require(name.isNotBlank() && name != "." && name != ".." && '/' !in name) {
                        ManagementTexts.Files.COULDN_T_DETERMINE_UPLOAD_FILE_NAME.get()
                    }
                    uri to name
                }
            require(sources.map { it.second }.distinct().size == sources.size) {
                ManagementTexts.Files.SELECTED_FILES_CONTAIN_DUPLICATE_NAMES.get()
            }
            val normalizedTarget = normalizeRemotePath(targetDirectory)
            val destinationChecks =
                sources.joinToString(separator = "; ") { (_, name) ->
                    val destination = joinRemotePath(normalizedTarget, name)
                    "[ ! -e ${quoteShellArg(destination)} ] || { echo ${quoteShellArg(ManagementTexts.Files.DESTINATION_ALREADY_EXISTS.format(name))} >&2; exit 1; }"
                }
            executeManagementShell(destinationChecks).getOrThrow()

            val uploadDir = File(context.cacheDir, "session-management/uploads").apply { mkdirs() }
            val uploadedPaths = mutableListOf<String>()
            runCatching {
                sources.forEach { (uri, name) ->
                    val localFile = File(uploadDir, "${sha256(uri.toString().toByteArray()).take(12)}_$name")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        localFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: error(ManagementTexts.Files.COULDN_T_READ_FILE.format(name))
                    val remotePath = joinRemotePath(normalizedTarget, name)
                    connection.pushFile(localFile.absolutePath, remotePath).getOrThrow()
                    uploadedPaths += remotePath
                }
            }.onFailure {
                if (uploadedPaths.isNotEmpty()) {
                    connection.executeShell(
                        "rm -f ${uploadedPaths.joinToString(" ") { path -> quoteShellArg(path) }}",
                        retryOnFailure = false,
                    )
                }
            }.getOrThrow()
            ManagementTexts.Files.UPLOADED_FILE_S.format(sources.size)
        }
    }
}

private fun resolveContentDisplayName(
    context: Context,
    uri: Uri,
): String =
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index).orEmpty() else ""
        }.orEmpty()

internal suspend fun captureDeviceScreenshot(context: Context): Result<File> {
    val connection =
        SessionManagementAdbConnection.current()
            ?: return Result.failure(IllegalStateException(ManagementTexts.Files.NO_ADB_CONNECTION_AVAILABLE.get()))

    return withContext(Dispatchers.IO) {
        runCatching {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val localDir = File(context.cacheDir, "session-management/screenshots").apply { mkdirs() }
            val localFile = File(localDir, "device_$timestamp.png")
            val remotePath = "/data/local/tmp/scrcpy_mobile_screenshot_$timestamp.png"

            connection.executeShell("rm -f $remotePath", retryOnFailure = false)
            connection.executeShell("screencap -p $remotePath", retryOnFailure = false).getOrThrow()
            connection.pullFile(remotePath, localFile.absolutePath).getOrThrow()
            connection.executeShell("rm -f $remotePath", retryOnFailure = false)

            if (localFile.length() <= 0L) {
                error(ManagementTexts.Files.SCREENSHOT_FILE_EMPTY.get())
            }
            localFile
        }
    }
}

internal fun openImagePreview(
    context: Context,
    file: File,
) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/png")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, ManagementTexts.Files.COULDN_T_OPEN_SCREENSHOT_PREVIEW.get(), Toast.LENGTH_SHORT).show()
    }
}

internal suspend fun saveImageToGallery(
    context: Context,
    file: File,
): Result<String> =
    withContext(Dispatchers.IO) {
        runCatching {
            val fileName = file.nameWithoutExtension.ifBlank { "screenshot_${System.currentTimeMillis()}" } + ".png"
            val resolver = context.contentResolver
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScrcpyMobile")
                    }
                }

            val uri =
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error(ManagementTexts.Files.COULDN_T_CREATE_GALLERY_FILE.get())

            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error(ManagementTexts.Files.COULDN_T_WRITE_GALLERY_FILE.get())

            ManagementTexts.Files.SCREENSHOT_SAVED_GALLERY.get()
        }
    }

internal suspend fun prepareRemoteFileForLocalOpen(
    context: Context,
    entry: RemoteFileEntry,
): Result<File> {
    val connection =
        SessionManagementAdbConnection.current()
            ?: return Result.failure(IllegalStateException(ManagementTexts.Files.NO_ADB_CONNECTION_AVAILABLE.get()))

    return withContext(Dispatchers.IO) {
        runCatching {
            val localFile = getPreparedLocalFile(context, entry)
            connection.pullFile(entry.fullPath, localFile.absolutePath).getOrThrow()
            localFile
        }
    }
}

internal suspend fun loadRemoteTextEditorState(
    context: Context,
    entry: RemoteFileEntry,
): Result<RemoteTextEditorState> {
    val localFile =
        prepareRemoteFileForLocalOpen(context, entry)
            .getOrElse { error -> return Result.failure(error) }

    return withContext(Dispatchers.IO) {
        runCatching {
            if (!isEditableTextFile(entry.name) && !isLikelyTextContent(localFile)) {
                error(ManagementTexts.Files.FILE_DOESN_T_LOOK_LIKE_TEXT_USE_PREVIEW.get())
            }
            if (localFile.length() > 128 * 1024L) {
                error(ManagementTexts.Files.KEEP_EDITING_RESPONSIVE_BUILT_IN_EDITOR_SUPPORTS_TEXT.get())
            }
            val content = localFile.readText(Charsets.UTF_8)
            RemoteTextEditorState(
                entry = entry,
                localFile = localFile,
                content = content,
            )
        }
    }
}

internal suspend fun saveRemoteTextFile(
    state: RemoteTextEditorState,
    content: String,
): Result<String> {
    val connection =
        SessionManagementAdbConnection.current()
            ?: return Result.failure(IllegalStateException(ManagementTexts.Files.NO_ADB_CONNECTION_AVAILABLE.get()))

    return withContext(Dispatchers.IO) {
        runCatching {
            state.localFile.writeText(content, Charsets.UTF_8)
            connection.pushFile(state.localFile.absolutePath, state.entry.fullPath).getOrThrow()
            ManagementTexts.Files.FILE_SAVED_PUSHED_BACK_DEVICE.get()
        }
    }
}

internal fun openLocalFileExternal(
    context: Context,
    file: File,
): Result<String> =
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = resolveMimeType(file.name)
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(Intent.createChooser(intent, ManagementTexts.Files.OPEN_FILE.get()))
        ManagementTexts.Files.OPENED_LOCAL_TEMP_FILE_EXTERNAL_APP.get()
    }

internal suspend fun pushPreparedLocalFileToDevice(
    context: Context,
    entry: RemoteFileEntry,
): Result<String> {
    val connection =
        SessionManagementAdbConnection.current()
            ?: return Result.failure(IllegalStateException(ManagementTexts.Files.NO_ADB_CONNECTION_AVAILABLE.get()))

    return withContext(Dispatchers.IO) {
        runCatching {
            val localFile = getPreparedLocalFile(context, entry)
            require(localFile.exists()) { ManagementTexts.Files.NO_LOCAL_COPY_AVAILABLE_PUSH_BACK.get() }
            connection.pushFile(localFile.absolutePath, entry.fullPath).getOrThrow()
            ManagementTexts.Files.LOCAL_COPY_PUSHED_BACK_DEVICE.get()
        }
    }
}

internal fun getPreparedLocalFile(
    context: Context,
    entry: RemoteFileEntry,
): File {
    val tempDir = File(context.cacheDir, "session-management/files").apply { mkdirs() }
    return File(
        tempDir,
        "${sha256(entry.fullPath.toByteArray()).take(12)}_${entry.name}",
    )
}

internal fun readBinaryPreview(
    file: File,
    maxBytes: Int = 512,
): String {
    val bytes =
        runCatching { file.inputStream().use { input -> readAtMostBytesCompat(input, maxBytes) } }.getOrNull()
            ?: return ManagementTexts.Files.COULDN_T_READ_BINARY_PREVIEW.get()
    if (bytes.isEmpty()) return ManagementTexts.Files.EMPTY_FILE.get()

    return bytes
        .toList()
        .chunked(16)
        .mapIndexed { index, chunk ->
            val address = (index * 16).toString(16).padStart(4, '0')
            val hex = chunk.joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }
            "$address  $hex"
        }.joinToString(separator = "\n")
}

internal suspend fun loadRemoteFileDetailSnapshot(entry: RemoteFileEntry): RemoteFileDetailSnapshot {
    val connection =
        SessionManagementAdbConnection.current()
            ?: return RemoteFileDetailSnapshot.loading(entry).copy(
                isLoading = false,
                errorMessage = ManagementTexts.Files.NO_ADB_CONNECTION_AVAILABLE_SO_FILE_DETAILS_CAN.get(),
            )

    suspend fun shell(command: String): String =
        connection
            .executeShell(command, retryOnFailure = false)
            .getOrNull()
            ?.trim()
            .orEmpty()

    return runCatching {
        val statOutput =
            shell(
                "stat -c '%A|%U|%G|%s|%y' ${quoteShellArg(entry.fullPath)} 2>/dev/null",
            )
        val sizeBytes =
            shell("stat -c '%s' ${quoteShellArg(entry.fullPath)} 2>/dev/null")
                .lineSequence()
                .map(String::trim)
                .lastOrNull { value -> value.toLongOrNull() != null }
                ?.toLongOrNull()

        val statLine =
            statOutput
                .lineSequence()
                .lastOrNull { line -> line.count { it == '|' } == 4 }

        if (statLine != null) {
            val parts = statLine.split("|", limit = 5)
            val resolvedSizeBytes = sizeBytes ?: parts.getOrNull(3)?.trim()?.toLongOrNull()
            val modified =
                parts
                    .getOrNull(4)
                    ?.substringBefore(".")
                    ?.trim()
                    .orEmpty()

            RemoteFileDetailSnapshot(
                isLoading = false,
                name = entry.name,
                fullPath = entry.fullPath,
                typeLabel = remoteFileTypeLabel(entry),
                permissions = parts.getOrNull(0)?.trim().orEmpty(),
                owner = parts.getOrNull(1)?.trim().orEmpty(),
                group = parts.getOrNull(2)?.trim().orEmpty(),
                sizeLabel = resolvedSizeBytes?.let(::formatFileSize) ?: "--",
                modifiedTime = modified,
            )
        } else {
            val lsOutput = shell("ls -ld ${quoteShellArg(entry.fullPath)}")
            val parsed = parseLsDetailLine(lsOutput, entry)
            parsed?.copy(
                sizeLabel = sizeBytes?.let(::formatFileSize) ?: parsed.sizeLabel,
            ) ?: error(ManagementTexts.Files.COULDN_T_PARSE_FILE_DETAILS.get())
        }
    }.getOrElse { error ->
        RemoteFileDetailSnapshot.loading(entry).copy(
            isLoading = false,
            errorMessage = error.message ?: ManagementTexts.Files.COULDN_T_LOAD_FILE_DETAILS.get(),
        )
    }
}

internal suspend fun loadFileBrowserSnapshot(path: String): FileBrowserSnapshot {
    val connection =
        SessionManagementAdbConnection.current()
            ?: return FileBrowserSnapshot.loading(path).copy(
                isLoading = false,
                errorMessage = ManagementTexts.Files.NO_ADB_CONNECTION_AVAILABLE_SO_FOLDER_CAN_T.get(),
            )

    suspend fun shell(command: String): String =
        connection
            .executeShell(command, retryOnFailure = false)
            .getOrNull()
            ?.trim()
            .orEmpty()

    return runCatching {
        val normalizedPath = normalizeRemotePath(path)
        val entries =
            loadFileBrowserEntriesFast(
                shell = { command -> shell(command) },
                path = normalizedPath,
            ).ifEmpty {
                loadFileBrowserEntriesFallback(
                    connection = connection,
                    path = normalizedPath,
                )
            }

        FileBrowserSnapshot(
            isLoading = false,
            currentPath = normalizedPath,
            entries = entries,
        )
    }.getOrElse { error ->
        FileBrowserSnapshot.loading(path).copy(
            isLoading = false,
            errorMessage = error.message ?: ManagementTexts.Files.COULDN_T_LOAD_FOLDER.get(),
        )
    }
}

private suspend fun loadFileBrowserEntriesFast(
    shell: suspend (String) -> String,
    path: String,
): List<RemoteFileEntry> {
    val directoryOperand = remoteDirectoryOperand(path)
    val command =
        "find ${quoteShellArg(directoryOperand)} -mindepth 1 -maxdepth 1 -printf '%y\\t%TY-%Tm-%Td %TH:%TM\\t%s\\t%f\\n' 2>/dev/null"
    val output = shell(command)
    return withContext(Dispatchers.Default) {
        val lines =
            output
                .lineSequence()
                .filter(String::isNotBlank)
                .toList()
        val entries =
            lines
                .mapNotNull { line -> parseFindFileBrowserEntry(path = path, line = line) }

        if (entries.size == lines.size) {
            sortRemoteFileEntries(entries)
        } else {
            emptyList()
        }
    }
}

private suspend fun loadFileBrowserEntriesFallback(
    connection: AdbConnection,
    path: String,
): List<RemoteFileEntry> {
    val directoryOperand = remoteDirectoryOperand(path)
    val output =
        connection
            .executeShell("ls -lAnp ${quoteShellArg(directoryOperand)}", retryOnFailure = false)
            .getOrThrow()

    return withContext(Dispatchers.Default) {
        output
            .lineSequence()
            .mapNotNull { line -> parseLsFileBrowserEntry(path = path, line = line) }
            .toList()
            .let(::sortRemoteFileEntries)
    }
}

private fun remoteDirectoryOperand(path: String): String {
    val normalizedPath = normalizeRemotePath(path)
    return if (normalizedPath == "/") normalizedPath else "$normalizedPath/"
}

internal fun parseLsFileBrowserEntry(
    path: String,
    line: String,
): RemoteFileEntry? {
    val parts = line.trim().split(Regex("\\s+"), limit = 8)
    if (parts.size < 8 || parts.first() == "total") return null

    val permissions = parts[0]
    val isDirectory = permissions.startsWith("d")
    val sizeBytes = parts[4].toLongOrNull() ?: return null
    val rawName = parts[7].substringBefore(" -> ")
    val name = rawName.removeSuffix("/")
    if (name.isBlank() || name == "." || name == "..") return null

    return RemoteFileEntry(
        name = name,
        fullPath = joinRemotePath(path, name),
        isDirectory = isDirectory,
        sizeBytes = sizeBytes.takeUnless { isDirectory },
        detail = formatFileModifiedTime("${parts[5]} ${parts[6]}"),
    )
}

internal fun parseFindFileBrowserEntry(
    path: String,
    line: String,
): RemoteFileEntry? {
    val firstSeparator = line.indexOf('\t')
    if (firstSeparator <= 0) return null
    val secondSeparator = line.indexOf('\t', startIndex = firstSeparator + 1)
    if (secondSeparator <= firstSeparator) return null
    val thirdSeparator = line.indexOf('\t', startIndex = secondSeparator + 1)
    if (thirdSeparator <= secondSeparator) return null

    val type = line.substring(0, firstSeparator)
    val modified = line.substring(firstSeparator + 1, secondSeparator)
    val sizeBytes = line.substring(secondSeparator + 1, thirdSeparator).trim().toLongOrNull()
    val name = line.substring(thirdSeparator + 1)
    if (name.isBlank() || name == "." || name == "..") return null
    val isDirectory = type == "d"
    if (sizeBytes == null) return null

    return RemoteFileEntry(
        name = name,
        fullPath = joinRemotePath(path, name),
        isDirectory = isDirectory,
        sizeBytes = sizeBytes.takeUnless { isDirectory },
        detail = modified.trim().takeIf { it.isNotBlank() }?.let(::formatFileModifiedTime) ?: "--",
    )
}

private fun sortRemoteFileEntries(entries: List<RemoteFileEntry>): List<RemoteFileEntry> =
    entries.sortedWith(compareByDescending<RemoteFileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })

internal suspend fun exportPackageApk(
    context: Context,
    packageName: String,
    destinationUri: Uri,
): Result<String> {
    val connection =
        SessionManagementAdbConnection.current()
            ?: return Result.failure(IllegalStateException(ManagementTexts.Files.NO_ADB_CONNECTION_AVAILABLE.get()))

    return withContext(Dispatchers.IO) {
        runCatching {
            val remotePath =
                connection
                    .executeShell("pm path $packageName | head -n 1", retryOnFailure = false)
                    .getOrThrow()
                    .removePrefix("package:")
                    .trim()
                    .ifBlank { error(ManagementTexts.Files.COULDN_T_FIND_APK_PATH.get()) }

            val exportDir = File(context.cacheDir, "session-management/apks").apply { mkdirs() }
            val localFile = File(exportDir, "${packageName.substringAfterLast('.')}.apk")
            connection.pullFile(remotePath, localFile.absolutePath).getOrThrow()
            runCatching {
                context.contentResolver.openOutputStream(destinationUri, "w")?.use { output ->
                    localFile.inputStream().use { input -> input.copyTo(output) }
                } ?: error(ManagementTexts.Files.COULDN_T_WRITE_APK.get())
            }.onFailure {
                runCatching { context.contentResolver.delete(destinationUri, null, null) }
            }.getOrThrow()
            ManagementTexts.Files.APK_SAVED.get()
        }
    }
}

private fun parseLsDetailLine(
    line: String,
    entry: RemoteFileEntry,
): RemoteFileDetailSnapshot? {
    val tokens = line.trim().split(Regex("\\s+"))
    if (tokens.size < 7) return null

    val permissions = tokens.getOrNull(0).orEmpty()
    val owner = tokens.getOrNull(2).orEmpty()
    val group = tokens.getOrNull(3).orEmpty()
    val sizeBytes = tokens.getOrNull(4)?.toLongOrNull()
    val modified =
        when {
            tokens.size >= 7 -> "${tokens[5]} ${tokens[6]}"
            else -> "--"
        }

    return RemoteFileDetailSnapshot(
        isLoading = false,
        name = entry.name,
        fullPath = entry.fullPath,
        typeLabel = remoteFileTypeLabel(entry),
        permissions = permissions,
        owner = owner,
        group = group,
        sizeLabel = sizeBytes?.let(::formatFileSize) ?: "--",
        modifiedTime = modified,
    )
}

internal fun formatFileModifiedTime(value: String): String =
    when {
        value.length >= 16 -> value.take(16)
        else -> value
    }

private fun resolveMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
    if (extension.isBlank()) return "*/*"

    return when (extension) {
        "txt", "log", "md", "json", "xml", "html", "htm", "css", "js", "kt", "java", "py", "sh", "yaml", "yml",
        "ini", "conf", "properties",
        -> "text/plain"

        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }
}

private fun isEditableTextFile(fileName: String): Boolean {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return extension in
        setOf(
            "txt",
            "log",
            "md",
            "markdown",
            "json",
            "json5",
            "xml",
            "html",
            "htm",
            "css",
            "js",
            "ts",
            "tsx",
            "jsx",
            "kt",
            "kts",
            "java",
            "groovy",
            "gradle",
            "py",
            "sh",
            "bash",
            "zsh",
            "yaml",
            "yml",
            "ini",
            "conf",
            "config",
            "properties",
            "prop",
            "toml",
            "csv",
            "tsv",
            "sql",
            "c",
            "h",
            "cpp",
            "hpp",
            "rs",
            "go",
            "php",
            "rb",
            "swift",
            "dart",
            "lua",
            "smali",
        )
}

internal enum class RemoteFileKind {
    Text,
    Image,
    Video,
    Audio,
    Binary,
}

internal fun remoteFileTypeLabel(entry: RemoteFileEntry): String {
    if (entry.isDirectory) return ManagementTexts.Files.FOLDER.get()
    return when (classifyRemoteFileKind(entry.name)) {
        RemoteFileKind.Text -> ManagementTexts.Files.TEXT_FILE.get()
        RemoteFileKind.Image -> ManagementTexts.Files.IMAGE_FILE.get()
        RemoteFileKind.Video -> ManagementTexts.Files.VIDEO_FILE.get()
        RemoteFileKind.Audio -> ManagementTexts.Files.AUDIO_FILE.get()
        RemoteFileKind.Binary -> ManagementTexts.Files.FILE.get()
    }
}

internal fun classifyRemoteFileKind(fileName: String): RemoteFileKind {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return when (extension) {
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic" -> RemoteFileKind.Image
        "mp4", "mkv", "webm", "mov", "3gp", "avi" -> RemoteFileKind.Video
        "mp3", "wav", "ogg", "m4a", "flac", "aac" -> RemoteFileKind.Audio
        else -> if (isEditableTextFile(fileName)) RemoteFileKind.Text else RemoteFileKind.Binary
    }
}

private fun isLikelyTextContent(file: File): Boolean {
    val bytes =
        runCatching { file.inputStream().use { input -> readAtMostBytesCompat(input, 2048) } }.getOrNull()
            ?: return false
    if (bytes.isEmpty()) return true

    val controlCount =
        bytes.count { byte ->
            val value = byte.toInt() and 0xFF
            value == 0 || (value < 0x09) || (value in 0x0E..0x1F)
        }
    return controlCount <= (bytes.size / 20).coerceAtLeast(1)
}
