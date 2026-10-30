package com.example.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class MediaSlot(val key: String, val displayTitle: String) {
    APP_LOGO("logo", "Application Logo"),
    MAIN_BANNER("main_banner", "Main Top Website Banner"),
    PROMO_BANNER_1("promo_banner_1", "Promotional Banner 1"),
    PROMO_BANNER_2("promo_banner_2", "Promotional Banner 2"),
    PROMO_BANNER_3("promo_banner_3", "Promotional Banner 3"),
}

/**
 * Copies administrator-selected media from Android Photo Picker into persistent internal storage
 * so uploaded logos and banners remain permanently available across app restarts and refreshes.
 */
class MediaStorageHelper(context: Context) {
    private val appContext = context.applicationContext
    private val mediaDir: File
        get() = File(appContext.filesDir, "winner_branding_media").apply {
            if (!exists()) mkdirs()
        }

    suspend fun persistPickedImage(slot: MediaSlot, sourceUri: Uri, previousSavedUri: String?): Result<String> =
        persistImageWithPrefix(slot.key, sourceUri, previousSavedUri)

    suspend fun persistUserAvatarImage(userId: String, sourceUri: Uri, previousSavedUri: String?): Result<String> {
        val safeUserKey = userId.replace(Regex("[^A-Za-z0-9_-]"), "").take(36).ifEmpty { "user" }
        return persistImageWithPrefix("user_avatar_$safeUserKey", sourceUri, previousSavedUri)
    }

    private suspend fun persistImageWithPrefix(
        prefix: String,
        sourceUri: Uri,
        previousSavedUri: String?,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val mimeType = appContext.contentResolver.getType(sourceUri) ?: "image/jpeg"
                val extension = when {
                    mimeType.contains("png", ignoreCase = true) -> "png"
                    mimeType.contains("webp", ignoreCase = true) -> "webp"
                    else -> "jpg"
                }
                val fileName = "${prefix}_${UUID.randomUUID()}.$extension"
                val destFile = File(mediaDir, fileName)

                val inputStream = appContext.contentResolver.openInputStream(sourceUri)
                    ?: return@withContext Result.failure(IllegalArgumentException("Unable to read selected image file."))

                inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                if (destFile.length() <= 0L) {
                    destFile.delete()
                    return@withContext Result.failure(IllegalArgumentException("Selected image file is empty."))
                }

                deletePreviouslyStoredFile(previousSavedUri)
                Result.success(Uri.fromFile(destFile).toString())
            } catch (e: Exception) {
                Result.failure(IllegalStateException("Failed to save image: ${e.localizedMessage ?: "I/O error"}"))
            }
        }

    suspend fun deleteStoredImage(savedUri: String?) = withContext(Dispatchers.IO) {
        deletePreviouslyStoredFile(savedUri)
    }

    private fun deletePreviouslyStoredFile(savedUri: String?) {
        if (savedUri.isNullOrBlank() || !savedUri.startsWith("file:")) return
        try {
            val path = Uri.parse(savedUri).path ?: return
            val file = File(path)
            if (file.exists() && file.canonicalPath.startsWith(mediaDir.canonicalPath)) {
                file.delete()
            }
        } catch (_: Exception) {
        }
    }
}
