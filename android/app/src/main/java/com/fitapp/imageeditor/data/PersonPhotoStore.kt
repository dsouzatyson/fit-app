package com.fitapp.imageeditor.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "fit_app_prefs"
private const val KEY_PERSON_PHOTO = "person_photo_path"
private const val PERSON_PHOTO_FILENAME = "person_photo.jpg"

/**
 * Persists the selected person photo across app restarts.
 *
 * Gallery content:// URIs are temporary grants that die with the process.
 * We copy the bytes to internal storage and save the file path to SharedPreferences.
 */
@Singleton
class PersonPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val photoFile: File get() = File(context.filesDir, PERSON_PHOTO_FILENAME)

    /** Copy [uri] to internal storage and persist the path. Returns the stable internal URI. */
    fun save(uri: Uri): Uri {
        context.contentResolver.openInputStream(uri)?.use { input ->
            photoFile.outputStream().use { output -> input.copyTo(output) }
        }
        prefs.edit().putString(KEY_PERSON_PHOTO, photoFile.absolutePath).apply()
        return photoFile.toUri()
    }

    /** Returns a persisted URI if a saved photo exists, null otherwise. */
    fun load(): Uri? {
        val path = prefs.getString(KEY_PERSON_PHOTO, null) ?: return null
        val file = File(path)
        return if (file.exists()) file.toUri() else null
    }

    fun clear() {
        prefs.edit().remove(KEY_PERSON_PHOTO).apply()
        photoFile.delete()
    }
}
