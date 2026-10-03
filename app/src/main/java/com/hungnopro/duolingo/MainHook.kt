package com.hungnopro.duolingo

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
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
                    val targetTz = fetchTargetTimezone()
                    XposedBridge.log("[Hugo-Duolingo] Timezone nạp cho Duolingo: $targetTz")

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

    private fun fetchTargetTimezone(): String {
        return try {
            // Nạp SharedPreferences từ module com.hungnopro.duolingo qua LSPosed bridge
            val xsp = XSharedPreferences("com.hungnopro.duolingo", "hugo_duolingo")
            xsp.reload()
            val tz = xsp.getString("now_timezone", "Etc/GMT+12")
            if (!tz.isNullOrEmpty() && isValidZone(tz)) {
                tz
            } else {
                "Etc/GMT+12"
            }
        } catch (e: Throwable) {
            XposedBridge.log("[Hugo-Duolingo] Lỗi nạp XSharedPreferences: ${e.message}")
            "Etc/GMT+12"
        }
    }

    private fun isValidZone(id: String): Boolean {
        return try {
            ZoneId.of(id)
            true
        } catch (_: Exception) {
            false
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
            XposedBridge.log("[Hugo-Duolingo] Hook java.util.TimeZone error: ${t.message}")
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
            XposedBridge.log("[Hugo-Duolingo] Hook java.time.ZoneId error: ${t.message}")
        }
    }
}
