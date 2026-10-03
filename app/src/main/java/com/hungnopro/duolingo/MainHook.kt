package com.hungnopro.duolingo

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.duolingo") return

        XposedBridge.log("[Hugo-Duolingo] Hooked into Duolingo process")

        XposedHelpers.findAndHookMethod(
            "android.content.ContextWrapper",
            lpparam.classLoader,
            "attachBaseContext",
            Context::class.java,
            object : XC_MethodHook() {
                private var isToastShown = false

                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = param.args[0] as? Context ?: return
                    val (targetTz, debugInfo) = fetchTimezoneWithDiagnostics(context)

                    applyTimezoneHooks(lpparam.classLoader, targetTz)

                    if (!isToastShown) {
                        isToastShown = true
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context,
                                "[Hugo] $targetTz ($debugInfo)",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        )
    }

    private fun fetchTimezoneWithDiagnostics(context: Context): Pair<String, String> {
        // Kênh 1: Dynamic Ordered Broadcast (Giao tiếp bộ nhớ IPC)
        try {
            val intent = Intent("com.hungnopro.duolingo.ACTION_GET_TZ").apply {
                setPackage("com.hungnopro.duolingo")
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }

            val latch = CountDownLatch(1)
            val resultRef = AtomicReference<String?>(null)

            context.sendOrderedBroadcast(
                intent,
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, resultIntent: Intent?) {
                        val bundle = getResultExtras(true)
                        val tz = bundle?.getString("timezone") ?: resultData
                        resultRef.set(tz)
                        latch.countDown()
                    }
                },
                null,
                Activity.RESULT_OK,
                null,
                null
            )

            // Đợi tối đa 350ms để nhận phản hồi từ module
            latch.await(350, TimeUnit.MILLISECONDS)
            val tz = resultRef.get()
            if (!tz.isNullOrEmpty() && isValidZone(tz)) {
                return Pair(tz, "IPC-Broadcast OK")
            }
        } catch (e: Throwable) {
            XposedBridge.log("[Hugo-Duolingo] Broadcast error: ${e.message}")
        }

        // Kênh 2: ContentProvider call (dự phòng)
        try {
            val uri = Uri.parse("content://com.hungnopro.duolingo.provider")
            val bundle = context.contentResolver.call(uri, "getTimezone", null, null)
            val tz = bundle?.getString("timezone")
            if (!tz.isNullOrEmpty() && isValidZone(tz)) {
                return Pair(tz, "Provider OK")
            }
        } catch (e: Throwable) {
            return Pair("Etc/GMT+12", "Lỗi: ${e.javaClass.simpleName}")
        }

        return Pair("Etc/GMT+12", "Chưa nhận phản hồi từ module")
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
