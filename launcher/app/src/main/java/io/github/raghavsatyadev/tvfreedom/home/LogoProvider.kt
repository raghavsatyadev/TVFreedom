package io.github.raghavsatyadev.tvfreedom.home

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * The company logo, sent by the TVFreedom phone app over adb:
 * ```
 * content write --uri content://io.github.raghavsatyadev.tvfreedom.home.logo/logo < logo.png
 * content delete --uri content://io.github.raghavsatyadev.tvfreedom.home.logo/logo
 * ```
 * The manifest guards it with android.permission.DUMP, like [ConfigReceiver]. Home shows it the next time it
 * starts ([Branding]).
 */
class LogoProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val flags = if (mode.contains("w")) {
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        } else {
            ParcelFileDescriptor.MODE_READ_ONLY
        }
        return ParcelFileDescriptor.open(file(requireContext()), flags)
    }

    override fun delete(uri: Uri, selection: String?, args: Array<String>?): Int =
        if (file(requireContext()).delete()) 1 else 0

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String = "image/png"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?): Int = 0

    companion object {
        const val FILE = "logo.png"

        fun file(ctx: Context): File = File(ctx.filesDir, FILE)
    }
}
