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

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, resultIntent: Intent?) {
                    val bundle = getResultExtras(true)
                    val tz = bundle?.getString("timezone") ?: resultData
                    resultRef.set(tz)
                    latch.countDown()
                }
            }

            // Xử lý cờ RECEIVER_EXPORTED bắt buộc trên Android 14+ (API 34, 35, 36)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Trên Android 14+, gọi qua registerReceiver với cờ RECEIVER_EXPORTED nếu cần
                // Hoặc dùng sendOrderedBroadcast với permission null và receiver được gắn cờ:
                context.sendOrderedBroadcast(
                    intent,
                    null, // receiverPermission
                    receiver,
                    null, // scheduler handler
                    Activity.RESULT_OK,
                    null, // initialData
                    null  // initialExtras
                )
            } else {
                context.sendOrderedBroadcast(
                    intent,
                    null,
                    receiver,
                    null,
                    Activity.RESULT_OK,
                    null,
                    null
                )
            }

            // Đợi tối đa 400ms để nhận phản hồi từ module
            val received = latch.await(400, TimeUnit.MILLISECONDS)
            val tz = resultRef.get()
            if (received && !tz.isNullOrEmpty() && isValidZone(tz)) {
                return Pair(tz, "IPC-Broadcast OK")
            }
        } catch (e: Throwable) {
            XposedBridge.log("[Hugo-Duolingo] Broadcast error: ${e.message}")
            return Pair("Etc/GMT+12", "Broadcast: ${e.javaClass.simpleName}")
        }

        // Kênh 2: Thử ContentProvider call với String authority trực tiếp
        try {
            val bundle = context.contentResolver.call(
                "com.hungnopro.duolingo.provider",
                "getTimezone",
                null,
                null
            )
            val tz = bundle?.getString("timezone")
            if (!tz.isNullOrEmpty() && isValidZone(tz)) {
                return Pair(tz, "Provider OK")
            }
        } catch (e: Throwable) {
            return Pair("Etc/GMT+12", "Lỗi: ${e.javaClass.simpleName}")
        }

        return Pair("Etc/GMT+12", "Timeout không phản hồi")
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
