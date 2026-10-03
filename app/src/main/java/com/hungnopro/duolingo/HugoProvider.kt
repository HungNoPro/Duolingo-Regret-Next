package com.hungnopro.duolingo

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle

class HugoProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val bundle = Bundle()
        if (method == "getTimezone") {
            val ctx = context ?: return bundle
            // Hỗ trợ Direct Boot trên Android 7.0+
            val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ctx.createDeviceProtectedStorageContext()
            } else {
                ctx
            }
            val sp = storageContext.getSharedPreferences("hugo_duolingo", Context.MODE_PRIVATE)
            val tz = sp.getString("now_timezone", "Etc/GMT+12") ?: "Etc/GMT+12"
            bundle.putString("timezone", tz)
        }
        return bundle
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = "text/plain"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
