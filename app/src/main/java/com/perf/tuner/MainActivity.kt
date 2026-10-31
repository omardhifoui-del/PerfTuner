package com.perf.tuner

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import rikka.shizuku.Shizuku

object Sh {
    fun run(cmd: String): String = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        p.waitFor()
        (out + err).trim().ifEmpty { "OK" }
    } catch (e: Throwable) {
        "Error: " + (e.cause?.message ?: e.message)
    }
}

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var pkg: EditText
    private lateinit var root: LinearLayout

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    private fun ready() = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun refresh() {
        status.text = when {
            ready() -> "Shizuku: متصل ✔"
            try { Shizuku.pingBinder() } catch (e: Throwable) { false } -> "Shizuku يعمل، اضغط لمنح الصلاحية"
            else -> "Shizuku غير مشغّل. شغّله أولاً (Wireless debugging أو adb)"
        }
    }

    private fun exec(cmd: String) {
        if (!ready()) {
            try {
                if (Shizuku.pingBinder()) Shizuku.requestPermission(1)
            } catch (e: Throwable) {}
            log.text = "لا توجد صلاحية Shizuku بعد."
            refresh(); return
        }
        log.text = "..."
        Thread {
            val r = Sh.run(cmd)
            runOnUiThread { log.text = r }
        }.start()
    }

    private fun section(t: String) {
        root.addView(TextView(this).apply {
            text = t; textSize = 18f; setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(20), 0, dp(4))
        })
    }

    private fun btn(label: String, cmd: () -> String) {
        root.addView(Button(this).apply {
            text = label; isAllCaps = false
            setOnClickListener { exec(cmd()) }
        })
    }

    private fun btn(label: String, cmd: String) = btn(label) { cmd }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try { Shizuku.addRequestPermissionResultListener(permListener) } catch (e: Throwable) {}
        val sv = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        sv.addView(root); setContentView(sv)

        root.addView(TextView(this).apply { text = "Perf Tuner"; textSize = 26f; setTypeface(null, Typeface.BOLD) })
        status = TextView(this).apply { setPadding(0, dp(8), 0, dp(8)); setOnClickListener { exec("echo ok") } }
        root.addView(status)
        log = TextView(this).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8)); setBackgroundColor(0x11000000)
            typeface = Typeface.MONOSPACE; textSize = 12f; setTextIsSelectable(true)
            text = "النتيجة تظهر هنا"
        }
        root.addView(log)

        section("FPS والشاشة")
        btn("أنيميشن سريع (0.5x)", "for k in window_animation_scale transition_animation_scale animator_duration_scale; do settings put global \$k 0.5; done")
        btn("إيقاف الأنيميشن", "for k in window_animation_scale transition_animation_scale animator_duration_scale; do settings put global \$k 0; done")
        for (hz in listOf(60, 90, 120)) btn("معدل التحديث $hz Hz", "settings put system min_refresh_rate $hz; settings put system peak_refresh_rate $hz")

        section("RAM")
        btn("إغلاق تطبيقات الخلفية", "am kill-all")
        btn("صيانة الذاكرة (idle maintenance)", "cmd activity idle-maintenance")

        section("CPU")
        btn("تحسين التطبيقات (dexopt سريع)", "cmd package bg-dexopt-job")
        btn("تجميع كل التطبيقات speed-profile (بطيء)", "cmd package compile -m speed-profile -a")

        section("GPU")
        btn("Skia GL renderer", "setprop debug.hwui.renderer skiagl")
        btn("Skia Vulkan renderer (تجريبي)", "setprop debug.hwui.renderer skiavk")
        btn("تعطيل HW overlays", "service call SurfaceFlinger 1008 i32 1")
        btn("إعادة GPU للافتراضي", "setprop debug.hwui.renderer ''; service call SurfaceFlinger 1008 i32 0")

        section("اللمس")
        btn("حساسية المؤشر عالية", "settings put system pointer_speed 7")
        btn("ضغطة طويلة أسرع (250ms)", "settings put secure long_press_timeout 250")

        section("وضع الألعاب لتطبيق")
        pkg = EditText(this).apply {
            hint = "اسم الحزمة مثل com.tencent.ig"; inputType = InputType.TYPE_CLASS_TEXT
            gravity = Gravity.START
        }
        root.addView(pkg)
        btn("وضع الأداء") { "cmd game set --mode performance ${pkg.text.toString().trim()}" }
        btn("الوضع القياسي") { "cmd game set --mode standard ${pkg.text.toString().trim()}" }

        section("مراقبة")
        btn("RAM + ترددات CPU + حرارة البطارية",
            "head -3 /proc/meminfo; for f in /sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_cur_freq; do echo \$f \$(cat \$f); done; dumpsys battery | grep -i temp")

        section("استعادة")
        btn("إرجاع كل الإعدادات للافتراضي",
            "for k in window_animation_scale transition_animation_scale animator_duration_scale; do settings put global \$k 1.0; done; " +
            "settings delete system min_refresh_rate; settings delete system peak_refresh_rate; " +
            "settings put system pointer_speed 0; settings put secure long_press_timeout 400; " +
            "setprop debug.hwui.renderer ''; service call SurfaceFlinger 1008 i32 0")
    }

    override fun onResume() { super.onResume(); refresh() }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(permListener) } catch (e: Throwable) {}
        super.onDestroy()
    }
}
