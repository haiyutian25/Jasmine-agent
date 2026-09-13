package com.lhzkml.jasmine.core.data.datasource

import com.lhzkml.jasmine.core.data.manager.dispatcher.DispatcherManager
import com.lhzkml.jasmine.core.network.FontDownloadApi
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Remote data source for binary font payloads; the single bridge to
 * [FontDownloadApi]. All blocking stream IO is confined to
 * [DispatcherManager.io].
 */
class FontRemoteDataSource @Inject constructor(
    private val fontDownloadApi: FontDownloadApi,
    private val dispatcherManager: DispatcherManager,
) {
    /**
     * Streams [url] into [target], reporting progress in the 0f..0.99f range,
     * and verifies the transferred bytes against [expectedSha256] (SHA-256 hex).
     *
     * Returns false on any failure (network error, non-2xx status, digest
     * mismatch); [target] may then be left partially written — cleanup is the
     * caller's responsibility. Cancellation is rethrown so callers stay
     * cooperative with structured concurrency.
     */
    suspend fun downloadAndVerify(
        url: String,
        target: File,
        expectedSha256: String,
        sizeHintBytes: Long,
        onProgress: (Float) -> Unit,
    ): Boolean = withContext(dispatcherManager.io) {
        try {
            val body = fontDownloadApi.download(url)
            val total = body.contentLength().takeIf { it > 0 } ?: sizeHintBytes
            val digest = MessageDigest.getInstance("SHA-256")
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        onProgress((downloaded.toFloat() / total).coerceIn(0f, 0.99f))
                    }
                    output.flush()
                }
            }
            digest.digest().toHexString().equals(expectedSha256, ignoreCase = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
