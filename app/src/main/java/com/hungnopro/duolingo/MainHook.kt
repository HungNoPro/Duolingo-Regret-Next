package com.hungnopro.duolingo

import android.content.Context
import android.net.Uri
import android.os.Build
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

        XposedBridge.log("[Hugo-Duolingo] Hooking com.duolingo on Android SDK ${Build.VERSION.SDK_INT}")

        XposedHelpers.findAndHookMethod(
            "android.content.ContextWrapper",
            lpparam.classLoader,
            "attachBaseContext",
            Context::class.java,
            object : XC_MethodHook() {
                private var isToastShown = false

                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = param.args[0] as? Context ?: return
                    val targetTz = resolveTimezone(context)
                    XposedBridge.log("[Hugo-Duolingo] Múi giờ được áp dụng: $targetTz")

                    applyTimezoneHooks(lpparam.classLoader, targetTz)

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

    private fun resolveTimezone(context: Context): String {
        val authority = "com.hungnopro.duolingo.provider"

        // 1. Thử gọi trực tiếp bằng String authority (Chuẩn Android 10 -> 16)
        try {
            val bundle = context.contentResolver.call(authority, "getTimezone", null, null)
            val tzFromProvider = bundle?.getString("timezone")
            if (!tzFromProvider.isNullOrEmpty() && isValidZone(tzFromProvider)) {
                XposedBridge.log("[Hugo-Duolingo] Provider trả về thành công: $tzFromProvider")
                return tzFromProvider
            }
        } catch (t: Throwable) {
            XposedBridge.log("[Hugo-Duolingo] Lỗi gọi call(authority): ${t.message}")
        }

        // 2. Dự phòng bằng Uri
        try {
            val uri = Uri.parse("content://$authority")
            val bundle = context.contentResolver.call(uri, "getTimezone", null, null)
            val tzFromUri = bundle?.getString("timezone")
            if (!tzFromUri.isNullOrEmpty() && isValidZone(tzFromUri)) {
                return tzFromUri
            }
        } catch (_: Throwable) {}

        // 3. Dự phòng qua createPackageContext
        try {
            val moduleContext = context.createPackageContext(
                "com.hungnopro.duolingo",
                Context.CONTEXT_IGNORE_SECURITY
            )
            val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                moduleContext.createDeviceProtectedStorageContext()
            } else {
                moduleContext
            }
            val sp = storageContext.getSharedPreferences("hugo_duolingo", Context.MODE_PRIVATE)
            val tzFromContext = sp.getString("now_timezone", null)
            if (!tzFromContext.isNullOrEmpty() && isValidZone(tzFromContext)) {
                return tzFromContext
            }
        } catch (_: Throwable) {}

        return "Etc/GMT+12"
    }

    private fun isValidZone(id: String): Boolean {
        return try {
            ZoneId.of(id)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun applyTimezoneHooks(classLoader: ClassLoader, tzId: String) {
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
            XposedBridge.log("[Hugo-Duolingo] Hook java.util.TimeZone lỗi: ${t.message}")
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
            XposedBridge.log("[Hugo-Duolingo] Hook java.time.ZoneId lỗi: ${t.message}")
        }
    }
}
