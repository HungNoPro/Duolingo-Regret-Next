package com.jakting.duolingo

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
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

        // Hook vào ContextWrapper để lấy Context sớm nhất
        XposedHelpers.findAndHookMethod(
            "android.content.ContextWrapper",
            lpparam.classLoader,
            "attachBaseContext",
            Context::class.java,
            object : XC_MethodHook() {
                private var isToastShown = false

                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = param.args[0] as? Context ?: return
                    val targetTz = fetchCustomTimezone(context)
                    
                    // Thực hiện hook thay đổi timezone
                    applyHooks(lpparam.classLoader, targetTz)

                    // Hiển thị Toast thông báo trên Main Thread (chỉ hiện 1 lần khi app mở)
                    if (!isToastShown) {
                        isToastShown = true
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context,
                                "Duolingo Regret: Đã chuyển múi giờ sang $targetTz",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        )
    }

    private fun fetchCustomTimezone(context: Context): String {
        return try {
            val uri = Uri.parse("content://com.jakting.duolingo.provider")
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            var tz = "Pacific/Pago_Pago"
            cursor?.use {
                if (it.moveToFirst()) {
                    tz = it.getString(it.getColumnIndexOrThrow("timezone"))
                }
            }
            tz
        } catch (e: Throwable) {
            XposedBridge.log("[Duolingo-Regret] Error reading provider: ${e.message}")
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

        // 2. Hook java.time.ZoneId.systemDefault()
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
