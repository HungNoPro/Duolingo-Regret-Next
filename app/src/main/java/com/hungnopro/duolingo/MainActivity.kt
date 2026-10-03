package com.hungnopro.duolingo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private var activeZoneId: ZoneId = ZoneId.of("Etc/GMT+12")

    private val timeZoneItems = mutableListOf<TimeZoneItem>()
    private var selectedZoneId: String = "Etc/GMT+12"

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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvDeviceTime = findViewById(R.id.tv_device_time)
        tvHookTime = findViewById(R.id.tv_hook_time)
        tvResetCountdown = findViewById(R.id.tv_reset_countdown)
        actvTimezone = findViewById(R.id.actv_timezone)

        val btnApply = findViewById<Button>(R.id.btn_apply)
        val btnPresetUtc12 = findViewById<Button>(R.id.btn_preset_utc12)
        val btnPresetLocal = findViewById<Button>(R.id.btn_preset_local)
        val btnOpenDuoSettings = findViewById<Button>(R.id.btn_open_duo_settings)

        // Tạo danh sách gọn gàng (bao gồm cả Etc/GMT+12 cho UTC-12)
        buildCompactTimeZoneList()

        val sp = getSharedPreferences("hugo_duolingo", Context.MODE_PRIVATE)
        val savedTz = sp.getString("now_timezone", "Etc/GMT+12") ?: "Etc/GMT+12"
        selectedZoneId = savedTz

        try {
            activeZoneId = ZoneId.of(savedTz)
        } catch (_: Exception) {
            activeZoneId = ZoneId.of("Etc/GMT+12")
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

        // Bấm nút Lưu & Áp dụng
        btnApply.setOnClickListener {
            val textInput = actvTimezone.text.toString().trim()
            val cleanId = resolveCleanZoneId(textInput)

            selectedZoneId = cleanId
            // Lưu trực tiếp vào SharedPreferences của app
            sp.edit().putString("now_timezone", cleanId).commit()

            try {
                activeZoneId = ZoneId.of(cleanId)
            } catch (_: Exception) {}
            updateClocks()

            Toast.makeText(
                this,
                "Đã lưu múi giờ $cleanId! Hãy vuốt đóng hoặc khởi động lại Duolingo thủ công.",
                Toast.LENGTH_LONG
            ).show()
        }

        // Phím tắt UTC-12 (Cứu Streak tối đa)
        btnPresetUtc12.setOnClickListener {
            selectedZoneId = "Etc/GMT+12"
            val item = timeZoneItems.firstOrNull { it.id == "Etc/GMT+12" }
            actvTimezone.setText(item?.displayName ?: "[UTC-12:00] Baker Island (UTC-12)", false)
            btnApply.performClick()
        }

        // Phím tắt giờ máy
        btnPresetLocal.setOnClickListener {
            val localId = TimeZone.getDefault().id
            selectedZoneId = localId
            val item = timeZoneItems.firstOrNull { it.id == localId }
            actvTimezone.setText(item?.displayName ?: localId, false)
            btnApply.performClick()
        }

        // Mở màn hình Cài đặt ứng dụng Duolingo để người dùng bấm Buộc dừng (Force stop) tiện lợi
        btnOpenDuoSettings.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", "com.duolingo", null)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Không tìm thấy ứng dụng Duolingo!", Toast.LENGTH_SHORT).show()
            }
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

        // Lọc lấy danh sách gọn, giữ lại Etc/GMT+12 đại diện cho UTC-12
        val filtered = allIds.filter {
            (it.contains("/") && !it.startsWith("SystemV/") && !it.startsWith("Etc/")) || it == "Etc/GMT+12"
        }

        val grouped = filtered.groupBy { TimeZone.getTimeZone(it).rawOffset }

        grouped.toSortedMap().forEach { (offsetMillis, ids) ->
            val representativeId = ids.firstOrNull { id ->
                id == "Etc/GMT+12" ||
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

            val displayLabel = when (representativeId) {
                "Etc/GMT+12" -> "$formattedOffset Baker Island (UTC-12)"
                else -> "$formattedOffset $representativeId"
            }

            timeZoneItems.add(
                TimeZoneItem(
                    id = representativeId,
                    displayName = displayLabel,
                    offsetMillis = offsetMillis
                )
            )
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
