package com.hungnopro.duolingo

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.factory.toClass
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import java.time.ZoneId
import java.util.TimeZone

@InjectYukiHookWithXposed
class MainHook : IYukiHookXposedInit {

    override fun onInit() = configs {
        isDebug = true
    }

    override fun onHook() = encase {
        loadApp("com.duolingo") {
            // Đọc cấu hình từ file hugo_duolingo qua kênh YukiHookPrefs
            val targetTz = prefs("hugo_duolingo").getString("now_timezone", "Pacific/Pago_Pago")
            YLog.info("[Hugo-Duolingo] Đã nhận Target Timezone: $targetTz")

            var isToastShown = false

            // 1. Hook Application/Activity để hiển thị Toast thông báo khi app mở
            "android.app.Application".toClass().method {
                name = "onCreate"
                emptyParam()
            }.hook {
                after {
                    val app = instance as? android.app.Application ?: return@after
                    if (!isToastShown) {
                        isToastShown = true
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                app.applicationContext,
                                "[Hugo] Múi giờ Duolingo: $targetTz",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

            // 2. Hook java.util.TimeZone.getDefault()
            "java.util.TimeZone".toClass().method {
                name = "getDefault"
                emptyParam()
            }.hook {
                after {
                    result = TimeZone.getTimeZone(targetTz)
                }
            }

            // 3. Hook java.time.ZoneId.systemDefault()
            "java.time.ZoneId".toClass().method {
                name = "systemDefault"
                emptyParam()
            }.hook {
                after {
                    result = ZoneId.of(targetTz)
                }
            }
        }
    }
}
