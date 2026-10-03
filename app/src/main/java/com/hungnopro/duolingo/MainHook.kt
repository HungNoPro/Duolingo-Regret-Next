package com.hungnopro.duolingo

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

        XposedBridge.log("[Hugo-Duolingo] Injected into com.duolingo: ${lpparam.processName}")

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

                    applyHooks(lpparam.classLoader, targetTz)

                    if (!isToastShown) {
                        isToastShown = true
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context,
                                "[Hugo] Múi giờ Duolingo: $targetTz",
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
            val uri = Uri.parse("content://com.hungnopro.duolingo.provider")
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            var tz = "Pacific/Pago_Pago"
            cursor?.use {
                if (it.moveToFirst()) {
                    tz = it.getString(it.getColumnIndexOrThrow("timezone"))
                }
            }
            tz
        } catch (e: Throwable) {
            XposedBridge.log("[Hugo-Duolingo] Lỗi đọc provider: ${e.message}")
            "Pacific/Pago_Pago"
        }
    }

    private fun applyHooks(classLoader: ClassLoader, tzId: String) {
        val spoofedTz = TimeZone.getTimeZone(tzId)

        // 1. Hook java.util.TimeZone
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
            XposedBridge.log("[Hugo-Duolingo] Hook java.util.TimeZone thất bại: ${t.message}")
        }

        // 2. Hook java.time.ZoneId
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
            XposedBridge.log("[Hugo-Duolingo] Hook java.time.ZoneId thất bại: ${t.message}")
        }
    }
}
