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
import java.io.FileInputStream
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
                    val (targetTz, debugMsg) = readTargetTimezoneWithDebug(context)

                    applyHooks(lpparam.classLoader, targetTz)

                    if (!isToastShown) {
                        isToastShown = true
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context,
                                "[Hugo] $targetTz ($debugMsg)",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        )
    }

    private fun readTargetTimezoneWithDebug(context: Context): Pair<String, String> {
        val candidatePaths = listOf(
            File("/sdcard/hugo_tz.txt"),
            File("/storage/emulated/0/hugo_tz.txt"),
            File(context.filesDir, "hugo_tz.txt"),
            File(context.dataDir, "hugo_tz.txt")
        )

        for (file in candidatePaths) {
            try {
                if (file.exists() && file.canRead()) {
                    val content = file.readText().trim()
                    if (content.isNotEmpty() && isValidZone(content)) {
                        return Pair(content, "OK từ ${file.absolutePath}")
                    }
                }
            } catch (e: Throwable) {
                return Pair("Pacific/Pago_Pago", "Lỗi đọc ${file.name}: ${e.message}")
            }
        }

        return Pair("Pacific/Pago_Pago", "Không tìm thấy hugo_tz.txt ở /sdcard")
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
