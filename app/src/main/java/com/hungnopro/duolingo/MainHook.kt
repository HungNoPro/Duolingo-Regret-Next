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
                    val targetTz = fetchTargetTimezone(context)
                    XposedBridge.log("[Hugo-Duolingo] Áp dụng Timezone: $targetTz")

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

    private fun fetchTargetTimezone(context: Context): String {
        // 1. Đọc từ thư mục private của chính Duolingo (/data/data/com.duolingo/hugo_tz.txt)
        try {
            val file = File(context.dataDir, "hugo_tz.txt")
            if (file.exists()) {
                val content = file.readText().trim()
                if (content.isNotEmpty() && isValidZone(content)) {
                    XposedBridge.log("[Hugo-Duolingo] Đọc hugo_tz.txt thành công: $content")
                    return content
                }
            } else {
                XposedBridge.log("[Hugo-Duolingo] Chưa tìm thấy file: ${file.absolutePath}")
            }
        } catch (e: Throwable) {
            XposedBridge.log("[Hugo-Duolingo] Lỗi đọc file dataDir: ${e.message}")
        }

        // 2. Dự phòng đường dẫn trực tiếp
        try {
            val directFile = File("/data/data/com.duolingo/hugo_tz.txt")
            if (directFile.exists()) {
                val content = directFile.readText().trim()
                if (content.isNotEmpty() && isValidZone(content)) {
                    return content
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
