package com.hungnopro.duolingo

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.time.ZoneId
import java.util.TimeZone

class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.duolingo") return

        XposedBridge.log("[Hugo-Duolingo] Injected into com.duolingo process: ${lpparam.processName}")

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
                    XposedBridge.log("[Hugo-Duolingo] Múi giờ áp dụng cho Duolingo: $targetTz")

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
        // 1. Đọc từ SystemProperty hệ thống (Cực kỳ ổn định và nhanh)
        try {
            val getMethod = Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java, String::class.java)
            val propTz = getMethod.invoke(null, "persist.hugo.duolingo.tz", "") as String
            if (propTz.isNotEmpty() && isValidZone(propTz)) {
                return propTz
            }
        } catch (e: Throwable) {
            XposedBridge.log("[Hugo-Duolingo] Lỗi đọc SystemProperties: ${e.message}")
        }

        // 2. Dự phòng đọc từ file /data/local/tmp/hugo_tz.txt
        try {
            val file = File("/data/local/tmp/hugo_tz.txt")
            if (file.exists()) {
                val fileTz = file.readText().trim()
                if (fileTz.isNotEmpty() && isValidZone(fileTz)) {
                    return fileTz
                }
            }
        } catch (_: Throwable) {}

        return "Pacific/Pago_Pago"
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

        // Hook java.util.TimeZone.getDefault()
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

        // Hook java.time.ZoneId.systemDefault()
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
