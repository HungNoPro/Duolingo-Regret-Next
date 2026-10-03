package com.hungnopro.duolingo

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

class TimezoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.hungnopro.duolingo.ACTION_GET_TZ") {
            val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }
            val sp = storageContext.getSharedPreferences("hugo_duolingo", Context.MODE_PRIVATE)
            val tz = sp.getString("now_timezone", "Etc/GMT+12") ?: "Etc/GMT+12"

            val bundle = Bundle().apply {
                putString("timezone", tz)
            }
            // Trả trực tiếp dữ liệu về tiến trình Duolingo đang đợi
            setResult(Activity.RESULT_OK, tz, bundle)
        }
    }
}
