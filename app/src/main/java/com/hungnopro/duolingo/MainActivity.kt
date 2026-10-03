package com.hungnopro.duolingo

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    data class TimeZoneItem(val id: String, val displayName: String, val offsetMillis: Int) {
        override fun toString(): String = displayName
    }

    private lateinit var actvTimezone: AutoCompleteTextView
    private lateinit var tvDeviceTime: TextView
    private lateinit var tvHookTime: TextView
    private lateinit var tvResetCountdown: TextView

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss - dd/MM/yyyy")
    private val handler = Handler(Looper.getMainLooper())
    private var activeZoneId: ZoneId = ZoneId.of("Pacific/Pago_Pago")

    private val timeZoneItems = mutableListOf<TimeZoneItem>()
    private var selectedZoneId: String = "Pacific/Pago_Pago"

    private val updateClockRunnable = object : Runnable {
        override fun run() {
            updateClocks()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView.rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvDeviceTime = findViewById(R.id.tv_device_time)
        tvHookTime = findViewById(R.id.tv_hook_time)
        tvResetCountdown = findViewById(R.id.tv_reset_countdown)
        actvTimezone = findViewById(R.id.actv_timezone)

        val btnApplyRestart = findViewById<Button>(R.id.btn_apply_and_restart)
        val btnPresetPago = findViewById<Button>(R.id.btn_preset_pago)
        val btnPresetLocal = findViewById<Button>(R.id.btn_preset_local)

        // Chủ động kích hoạt popup xin quyền Root khi vừa vào app
        requestRootPermissionOnInit()

        buildCompactTimeZoneList()

        val sp = getSharedPreferences("hugo_duolingo", Context.MODE_PRIVATE)
        val savedTz = sp.getString("now_timezone", "Pacific/Pago_Pago") ?: "Pacific/Pago_Pago"
        selectedZoneId = savedTz

        try {
            activeZoneId = ZoneId.of(savedTz)
        } catch (_: Exception) {
            activeZoneId = ZoneId.of("Pacific/Pago_Pago")
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, timeZoneItems)
        actvTimezone.setAdapter(adapter)

        val currentItem = timeZoneItems.firstOrNull { it.id == savedTz }
        actvTimezone.setText(currentItem?.displayName ?: savedTz, false)

        actvTimezone.setOnItemClickListener { parent, _, position, _ ->
            val item = parent.getItemAtPosition(position) as TimeZoneItem
            selectedZoneId = item.id
            actvTimezone.setText(item.displayName, false)
        }

        btnApplyRestart.setOnClickListener {
            val textInput = actvTimezone.text.toString().trim()
            val cleanId = resolveCleanZoneId(textInput)

            selectedZoneId = cleanId
            sp.edit().putString("now_timezone", cleanId).commit()

            try {
                activeZoneId = ZoneId.of(cleanId)
            } catch (_: Exception) {}
            updateClocks()

            lifecycleScope.launch(Dispatchers.IO) {
                val ok = writeConfigAndRestart(cleanId)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        Toast.makeText(this@MainActivity, "Đã lưu ($cleanId) & mở Duolingo!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "LỖI: Chưa được cấp quyền Root (SU)! Hãy cấp quyền trong Magisk/KernelSU.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        btnPresetPago.setOnClickListener {
            selectedZoneId = "Pacific/Pago_Pago"
            val item = timeZoneItems.firstOrNull { it.id == "Pacific/Pago_Pago" }
            actvTimezone.setText(item?.displayName ?: "Pacific/Pago_Pago", false)
            btnApplyRestart.performClick()
        }

        btnPresetLocal.setOnClickListener {
            val localId = TimeZone.getDefault().id
            selectedZoneId = localId
            val item = timeZoneItems.firstOrNull { it.id == localId }
            actvTimezone.setText(item?.displayName ?: localId, false)
            btnApplyRestart.performClick()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateClockRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateClockRunnable)
    }

    private fun requestRootPermissionOnInit() {
        lifecycleScope.launch(Dispatchers.IO) {
            val hasRoot = checkSuAvailable()
            withContext(Dispatchers.Main) {
                if (!hasRoot) {
                    Toast.makeText(
                        this@MainActivity,
                        "Cảnh báo: App cần quyền Root (SU) để ghi múi giờ và khởi động lại Duolingo!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun checkSuAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveCleanZoneId(input: String): String {
        val matched = timeZoneItems.firstOrNull { it.displayName == input || it.id == input }
        if (matched != null) return matched.id

        if (input.contains(" ")) {
            val lastPart = input.substringAfterLast(" ").trim()
            if (isValidZone(lastPart)) return lastPart
        }
        if (isValidZone(input)) return input
        return selectedZoneId
    }

    private fun isValidZone(id: String): Boolean {
        return try {
            ZoneId.of(id)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildCompactTimeZoneList() {
        timeZoneItems.clear()
        val allIds = TimeZone.getAvailableIDs()

        val grouped = allIds
            .filter { it.contains("/") && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
            .groupBy { TimeZone.getTimeZone(it).rawOffset }

        grouped.toSortedMap().forEach { (offsetMillis, ids) ->
            val representativeId = ids.firstOrNull { id ->
                id.contains("Pago_Pago") || id.contains("Honolulu") || id.contains("Anchorage") ||
                id.contains("Los_Angeles") || id.contains("Denver") || id.contains("Chicago") ||
                id.contains("New_York") || id.contains("London") || id.contains("Paris") ||
                id.contains("Cairo") || id.contains("Dubai") || id.contains("Bangkok") ||
                id.contains("Singapore") || id.contains("Tokyo") || id.contains("Sydney") ||
                id.contains("Auckland")
            } ?: ids.first()

            val hours = offsetMillis / (1000 * 60 * 60)
            val minutes = Math.abs((offsetMillis / (1000 * 60)) % 60)
            val sign = if (hours >= 0) "+" else "-"
            val formattedOffset = "[UTC$sign%02d:%02d]".format(Math.abs(hours), minutes)

            timeZoneItems.add(
                TimeZoneItem(
                    id = representativeId,
                    displayName = "$formattedOffset $representativeId",
                    offsetMillis = offsetMillis
                )
            )
        }
    }

    private fun writeConfigAndRestart(tzId: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { os ->
                val targetFile = "/data/data/com.duolingo/hugo_tz.txt"
                val targetFileAlt = "/data/user/0/com.duolingo/hugo_tz.txt"

                // 1. Lấy UID của Duolingo
                os.writeBytes("DUO_UID=\$(stat -c '%u' /data/data/com.duolingo 2>/dev/null || echo '')\n")

                // 2. Ghi chuỗi Timezone trực tiếp vào cả 2 đường dẫn (đảm bảo không trượt)
                os.writeBytes("printf '%s' '$tzId' > $targetFile\n")
                os.writeBytes("printf '%s' '$tzId' > $targetFileAlt\n")

                // 3. Phân quyền toàn quyền và trả quyền sở hữu cho Duolingo
                os.writeBytes("chmod 666 $targetFile $targetFileAlt\n")
                os.writeBytes("if [ -n \"\$DUO_UID\" ]; then chown \$DUO_UID:\$DUO_UID $targetFile $targetFileAlt; fi\n")
                os.writeBytes("restorecon $targetFile $targetFileAlt\n")

                // 4. Force stop và khởi chạy lại Duolingo
                os.writeBytes("am force-stop com.duolingo\n")
                os.writeBytes("sleep 1\n")
                os.writeBytes("monkey -p com.duolingo -c android.intent.category.LAUNCHER 1\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }
    private fun updateClocks() {
        val nowDevice = ZonedDateTime.now()
        val nowHook = ZonedDateTime.now(activeZoneId)

        val nextMidnight = nowHook.toLocalDate().plusDays(1).atStartOfDay(activeZoneId)
        val remaining = Duration.between(nowHook, nextMidnight)

        val hours = remaining.toHours()
        val minutes = remaining.toMinutes() % 60
        val seconds = remaining.seconds % 60

        tvDeviceTime.text = "Giờ máy (${ZoneId.systemDefault()}): ${nowDevice.format(timeFormatter)}"
        tvHookTime.text = "Giờ hook ($activeZoneId): ${nowHook.format(timeFormatter)}"
        tvResetCountdown.text = "Hết ngày sau: %02d giờ %02d phút %02d giây".format(hours, minutes, seconds)
    }
}
