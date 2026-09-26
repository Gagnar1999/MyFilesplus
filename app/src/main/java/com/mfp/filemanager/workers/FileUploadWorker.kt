package com.mfp.filemanager.workers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.mfp.filemanager.R
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.feature.drive.GoogleLoginHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID

class FileUploadWorker(
    appContext: Context, workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "FileUploadWorker"

        const val FILE_NAME = "FILE_NAME"
        const val FILE_PATH = "FILE_PATH"
        const val MIME_TYPE = "MIME_TYPE"
        const val SIZE = "SIZE"

        const val KEY_IS_UPLOADING = "isUploading"
        const val KEY_PROGRESS = "progress"

        private const val NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "Sync_INFO"

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun uploadFileInBackground(context: Context, fileModel: FileModel): UUID {
            val data = Data.Builder()
                .putString(FILE_NAME, fileModel.name)
                .putString(FILE_PATH, fileModel.path)
                .putString(MIME_TYPE, fileModel.mimeType)
                .putLong(SIZE, fileModel.size)
                .build()

            val uploadWorkRequest = OneTimeWorkRequestBuilder<FileUploadWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(fileModel.name, ExistingWorkPolicy.REPLACE, uploadWorkRequest)

            return uploadWorkRequest.id
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val fileName = inputData.getString(FILE_NAME) ?: "File"
        return createForegroundInfo(0, fileName)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val fileName = inputData.getString(FILE_NAME)
        val filePath = inputData.getString(FILE_PATH)
        val mimeType = inputData.getString(MIME_TYPE) ?: "*/*"
        val size = inputData.getLong(SIZE, 0)

        if (fileName.isNullOrEmpty() || filePath.isNullOrEmpty()) {
            Log.e(TAG, "Missing file arguments: fileName=$fileName, filePath=$filePath")
            return@withContext Result.failure()
        }

        val localFile = File(filePath)
        if (!localFile.exists() || !localFile.isFile) {
            Log.e(TAG, "File does not exist at path: $filePath")
            return@withContext Result.failure()
        }

        val lastSignedAccount = GoogleLoginHelper.getLastSignedInAccount(applicationContext)
        if (lastSignedAccount == null) {
            Log.e(TAG, "No Google Account logged in")
            return@withContext Result.failure()
        }

        // Set initial foreground status before starting transfer
        try {
            setForeground(createForegroundInfo(0, fileName))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to run in foreground mode", e)
        }

        return@withContext try {
            val credential = GoogleAccountCredential.usingOAuth2(
                applicationContext, listOf(DriveScopes.DRIVE, DriveScopes.DRIVE_FILE)
            )
            credential.selectedAccount = lastSignedAccount.account

            val drive = Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                JacksonFactory.getDefaultInstance(),
                credential
            ).setApplicationName(applicationContext.getString(R.string.app_name)).build()

            val gFile = com.google.api.services.drive.model.File().apply {
                name = fileName
                this.mimeType = mimeType
            }

            val fileStream = BufferedInputStream(FileInputStream(localFile))
            val mediaContent = InputStreamContent(mimeType, fileStream).apply {
                length = if (size > 0) size else localFile.length()
            }

            val request = drive.files().create(gFile, mediaContent)
            request.mediaHttpUploader.isDirectUploadEnabled = false

            var lastProgressPercent = -1

            request.mediaHttpUploader.progressListener =
                MediaHttpUploaderProgressListener { uploader ->
                    when (uploader.uploadState) {
                        MediaHttpUploader.UploadState.INITIATION_STARTED -> {
                            val data = Data.Builder()
                                .putBoolean(KEY_IS_UPLOADING, true)
                                .putDouble(KEY_PROGRESS, 0.0)
                                .build()
                            setProgressAsync(data)
                        }

                        MediaHttpUploader.UploadState.MEDIA_IN_PROGRESS -> {
                            val progressDouble = uploader.progress
                            val progressPercent = (progressDouble * 100).toInt().coerceIn(0, 100)
                            // Throttle progress and notification updates to avoid flooding UI thread
                            if (progressPercent != lastProgressPercent) {
                                lastProgressPercent = progressPercent
                                val data = Data.Builder()
                                    .putBoolean(KEY_IS_UPLOADING, true)
                                    .putDouble(KEY_PROGRESS, progressDouble)
                                    .build()
                                setProgressAsync(data)
                                setForegroundAsync(createForegroundInfo(progressPercent, fileName))
                            }
                        }

                        MediaHttpUploader.UploadState.MEDIA_COMPLETE -> {
                            val data = Data.Builder()
                                .putBoolean(KEY_IS_UPLOADING, false)
                                .putDouble(KEY_PROGRESS, 1.0)
                                .build()
                            setProgressAsync(data)
                        }

                        else -> {}
                    }
                }

            val response = request.execute()
            Log.d(TAG, "File Upload Response: ${response.id}")
            Result.success()
        } catch (e: IOException) {
            Log.e(TAG, "Network or I/O error during upload: ${e.message}", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload file to Google Drive: ${e.message}", e)
            Result.failure()
        }
    }

    private fun createForegroundInfo(progress: Int, fileName: String): ForegroundInfo {
        val maxProgress = 100
        val title = applicationContext.getString(R.string.app_name)
        val cancel = "Stop Uploading"

        val intent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService<NotificationManager>()
            if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, title, NotificationManager.IMPORTANCE_LOW)
                manager?.createNotificationChannel(channel)
            }
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Uploading ${fileName.take(50)}")
            .setProgress(maxProgress, progress, false)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setStyle(NotificationCompat.BigPictureStyle())
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
