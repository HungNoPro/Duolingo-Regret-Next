package com.hungnopro.duolingo

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

class ConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(arrayOf("timezone"))
        val ctx = context
        var targetTz = "Pacific/Pago_Pago"

        if (ctx != null) {
            val sp = ctx.getSharedPreferences("hugo_duolingo", Context.MODE_PRIVATE)
            targetTz = sp.getString("now_timezone", "Pacific/Pago_Pago") ?: "Pacific/Pago_Pago"
        }

        cursor.addRow(arrayOf(targetTz))
        return cursor
    }

    override fun getType(uri: Uri): String? = "text/plain"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
