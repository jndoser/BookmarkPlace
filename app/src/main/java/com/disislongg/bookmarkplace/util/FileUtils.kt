package com.disislongg.bookmarkplace.util

import android.content.Context
import java.io.File

object FileUtils {
    fun deleteFile(context: Context, filename: String) {
        var dir = context.filesDir
        val file = File(dir, filename)
        file.delete()
    }
}