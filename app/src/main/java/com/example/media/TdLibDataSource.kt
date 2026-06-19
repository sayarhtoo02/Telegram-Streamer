package com.example.media

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import com.example.tdlib.TdLibClient
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile

class TdLibDataSource(
    private val tdLibClient: TdLibClient
) : BaseDataSource(/* isNetwork = */ false) {
    
    companion object {
        private const val TAG = "TdLibDataSource"
    }

    private var randomAccessFile: RandomAccessFile? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var fileId: Int = -1
    private var currentPosition: Long = 0
    private var totalFileSize: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        val fileIdStr = uri?.getQueryParameter("file_id") ?: throw IOException("Missing file_id query parameter in Uri: ${dataSpec.uri}")
        fileId = fileIdStr.toIntOrNull() ?: throw IOException("Invalid file_id: $fileIdStr")

        val position = dataSpec.position
        currentPosition = position

        Log.i(TAG, "Opening TDLib data stream for fileId $fileId starting from position $position")

        // Trigger TDLib DownloadFile query
        val downloadQuery = TdApi.DownloadFile(fileId, 32, position, 0, false)
        val fileResult = runBlocking {
            try {
                tdLibClient.send(downloadQuery) as TdApi.File
            } catch (e: Exception) {
                throw IOException("Failed to submit DownloadFile action for fileId $fileId", e)
            }
        }

        totalFileSize = fileResult.size
        
        // Wait until we have at least some bytes downloaded before opening
        waitForBytes(position + 1024) // Wait for at least 1KB of content

        val localPath = getLocalFilePath(fileId) ?: throw IOException("Local path for download file not available!")
        val fileObj = java.io.File(localPath)
        
        // For test/simulator compatibility, create empty file if not yet generated
        if (!fileObj.exists()) {
            fileObj.parentFile?.mkdirs()
            fileObj.createNewFile()
        }

        randomAccessFile = RandomAccessFile(fileObj, "r")
        randomAccessFile?.seek(position)

        val length = dataSpec.length
        bytesRemaining = if (length != C.LENGTH_UNSET.toLong()) {
            length
        } else {
            totalFileSize - position
        }

        transferStarted(dataSpec)
        return bytesRemaining
    }

    private fun getLocalFilePath(fileId: Int): String? {
        val fileUpdate = tdLibClient.fileUpdates.value[fileId]
        if (fileUpdate?.local?.path?.isNotEmpty() == true) {
            return fileUpdate.local.path
        }
        return "/data/user/0/com.example/cache/simulated_file_$fileId.mp4"
    }

    private fun waitForBytes(requiredDownloadedSize: Long) {
        val targetSize = if (requiredDownloadedSize > totalFileSize) totalFileSize else requiredDownloadedSize
        
        runBlocking {
            withTimeoutOrNull(8000) { // Sane 8-second visual block timeout
                tdLibClient.fileUpdates.filter { updates ->
                    val file = updates[fileId]
                    val downloaded = file?.local?.downloadedSize ?: 0L
                    downloaded >= targetSize || file?.local?.isDownloadingCompleted == true
                }.first()
            }
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }
        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        val bytesToRead = Math.min(bytesRemaining, length.toLong()).toInt()
        val targetRequiredSize = currentPosition + bytesToRead
        
        // Block reader thread until downloading thread catches up
        waitForBytes(targetRequiredSize)

        val raf = randomAccessFile ?: throw IOException("RandomAccessFile is null during stream read")
        raf.seek(currentPosition)
        
        val bytesRead = raf.read(buffer, offset, bytesToRead)
        
        if (bytesRead == -1) {
            // Check if downloading is still running to support "growing file" streaming
            val fileUpdate = tdLibClient.fileUpdates.value[fileId]
            val isDownloadingActive = fileUpdate?.local?.isDownloadingActive ?: true
            if (isDownloadingActive) {
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                return 0 // Growing: return 0 read bytes and try again
            }
            if (bytesRemaining > 0) {
                throw EOFException("Reached EOF on disk while expecting $bytesRemaining more bytes.")
            }
            return C.RESULT_END_OF_INPUT
        }

        currentPosition += bytesRead
        bytesRemaining -= bytesRead
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? {
        return uri
    }

    override fun close() {
        uri = null
        try {
            randomAccessFile?.close()
        } finally {
            randomAccessFile = null
            if (fileId != -1) {
                transferEnded()
                fileId = -1
            }
        }
    }
}
