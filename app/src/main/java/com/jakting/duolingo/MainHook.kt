package com.jakting.duolingo

import android.database.Cursor
import android.net.Uri
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.time.ZoneId
import java.util.TimeZone

class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.duolingo") return

        XposedBridge.log("[Duolingo-Regret] Injected into com.duolingo process: ${lpparam.processName}")

        // Lấy Context của Duolingo khi ứng dụng khởi chạy
        XposedHelpers.findAndHookMethod(
    "android.content.ContextWrapper",
    lpparam.classLoader,
    "attachBaseContext",
    android.content.Context::class.java,
    object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val context = param.args[0] as? android.content.Context ?: return
            val targetTz = fetchCustomTimezone(context)
            XposedBridge.log("[Duolingo-Regret] Target timezone obtained: $targetTz")
            applyHooks(lpparam.classLoader, targetTz)
        }
    }
)
    }

    private fun fetchCustomTimezone(context: android.content.Context): String {
        return try {
            val uri = Uri.parse("content://com.jakting.duolingo.provider")
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            var tz = "Pacific/Pago_Pago" // Mặc định UTC-11 nếu chưa thiết lập
            cursor?.use {
                if (it.moveToFirst()) {
                    tz = it.getString(it.getColumnIndexOrThrow("timezone"))
                }
            }
            tz
        } catch (e: Throwable) {
            XposedBridge.log("[Duolingo-Regret] Read provider error: ${e.message}")
            "Pacific/Pago_Pago"
        }
    }

    private fun applyHooks(classLoader: ClassLoader, tzId: String) {
        val spoofedTz = TimeZone.getTimeZone(tzId)

        // 1. Hook java.util.TimeZone.getDefault()
        try {
            XposedHelpers.findAndHookMethod(
                "java.util.TimeZone",
                classLoader,
                "getDefault",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = spoofedTz
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[Duolingo-Regret] Hook java.util.TimeZone failed: ${t.message}")
        }

        // 2. Hook java.time.ZoneId.systemDefault() (Dành cho Android 8.0+ và Duolingo bản mới)
        try {
            val spoofedZoneId = ZoneId.of(tzId)
            XposedHelpers.findAndHookMethod(
                "java.time.ZoneId",
                classLoader,
                "systemDefault",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = spoofedZoneId
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[Duolingo-Regret] Hook java.time.ZoneId failed: ${t.message}")
        }
    }
}
