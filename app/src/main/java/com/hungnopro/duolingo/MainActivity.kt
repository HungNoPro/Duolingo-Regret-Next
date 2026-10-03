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
import androidx.appcompat.app.AppCompatActivity
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var actvTimezone: AutoCompleteTextView
    private lateinit var tvDeviceTime: TextView
    private lateinit var tvHookTime: TextView
    private lateinit var tvResetCountdown: TextView

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss - dd/MM/yyyy")
    private val handler = Handler(Looper.getMainLooper())
    private var activeZoneId: ZoneId = ZoneId.of("Pacific/Pago_Pago")

    private val updateClockRunnable = object : Runnable {
        override fun run() {
            updateClocks()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvDeviceTime = findViewById(R.id.tv_device_time)
        tvHookTime = findViewById(R.id.tv_hook_time)
        tvResetCountdown = findViewById(R.id.tv_reset_countdown)
        actvTimezone = findViewById(R.id.actv_timezone)

        val btnApply = findViewById<Button>(R.id.btn_apply)
        val btnPresetPago = findViewById<Button>(R.id.btn_preset_pago)
        val btnPresetLocal = findViewById<Button>(R.id.btn_preset_local)

        val sp = getSharedPreferences("hugo_duolingo", Context.MODE_PRIVATE)
        val savedTz = sp.getString("now_timezone", "Pacific/Pago_Pago") ?: "Pacific/Pago_Pago"

        activeZoneId = try {
            ZoneId.of(savedTz)
        } catch (_: Exception) {
            ZoneId.of("Pacific/Pago_Pago")
        }

        // Tạo danh sách chọn Timezone kèm bộ lọc
        val tzList = TimeZone.getAvailableIDs().sorted()
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tzList)
        actvTimezone.setAdapter(adapter)
        actvTimezone.setText(savedTz, false)

        // Nút lưu múi giờ
        btnApply.setOnClickListener {
            val selected = actvTimezone.text.toString().trim()
            if (tzList.contains(selected)) {
                sp.edit().putString("now_timezone", selected).apply()
                activeZoneId = ZoneId.of(selected)
                Toast.makeText(this, "Đã lưu múi giờ: $selected", Toast.LENGTH_SHORT).show()
                updateClocks()
            } else {
                Toast.makeText(this, "Múi giờ không hợp lệ!", Toast.LENGTH_SHORT).show()
            }
        }

        // Nút tắt: UTC-11 cứu streak tối đa
        btnPresetPago.setOnClickListener {
            actvTimezone.setText("Pacific/Pago_Pago", false)
            btnApply.performClick()
        }

        // Nút tắt: Đặt lại về giờ mặc định của máy
        btnPresetLocal.setOnClickListener {
            val defaultId = TimeZone.getDefault().id
            actvTimezone.setText(defaultId, false)
            btnApply.performClick()
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

    private fun updateClocks() {
        val nowDevice = ZonedDateTime.now()
        val nowHook = ZonedDateTime.now(activeZoneId)

        // Mốc nửa đêm tiếp theo theo múi giờ hook
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
