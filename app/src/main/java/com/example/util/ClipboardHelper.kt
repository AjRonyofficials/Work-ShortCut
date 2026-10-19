package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object ClipboardHelper {

    fun copyToClipboard(
        context: Context,
        text: String,
        label: String = "Work Shortcut",
        toastMessage: String? = null
    ) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard?.setPrimaryClip(clip)

            if (toastMessage != null) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Copy failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getFromClipboard(context: Context): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val item = clipboard?.primaryClip?.getItemAt(0)
            item?.text?.toString()?.trim()
        } catch (_: Exception) {
            null
        }
    }

    fun pasteFromClipboard(context: Context): String? = getFromClipboard(context)
}
