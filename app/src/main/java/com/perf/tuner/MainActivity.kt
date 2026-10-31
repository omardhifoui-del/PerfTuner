package com.perf.tuner

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
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
        (out + err).trim().ifEmpty { "تم بنجاح ✔" }
    } catch (e: Throwable) {
        "Error: " + (e.cause?.message ?: e.message)
    }
}

class MainActivity : Activity() {
    companion object {
        const val MON = "grep -E 'MemTotal|MemAvailable' /proc/meminfo; dumpsys battery | grep -E 'level|temperature'; for f in /sys/devices/system/cpu/cpu[0-9]*/cpufreq; do echo F \$(cat \$f/scaling_cur_freq) \$(cat \$f/cpuinfo_max_freq); done"
        const val BOOST = "for k in window_animation_scale transition_animation_scale animator_duration_scale; do settings put global \$k 0.5; done; am kill-all; pm trim-caches 999G; cmd power set-fixed-performance-mode-enabled true; settings put global low_power 0; settings put system pointer_speed 5"
        const val RESET = "for k in window_animation_scale transition_animation_scale animator_duration_scale; do settings put global \$k 1.0; done; settings delete system min_refresh_rate; settings delete system peak_refresh_rate; settings put system pointer_speed 0; settings put secure long_press_timeout 400; settings put secure multi_press_timeout 300; settings put system show_touches 0; settings put system pointer_location 0; setprop debug.hwui.renderer ''; service call SurfaceFlinger 1008 i32 0; cmd power set-fixed-performance-mode-enabled false; cmd thermalservice reset; settings delete global disable_window_blurs"
    }

    private val bg1 = Color.parseColor("#0B0F1A")
    private val bg2 = Color.parseColor("#1B1442")
    private val cy = Color.parseColor("#00E5FF")
    private val pu = Color.parseColor("#B388FF")
    private val pk = Color.parseColor("#FF4081")
    private val og = Color.parseColor("#FFAB40")
    private val gr = Color.parseColor("#69F0AE")

    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var pkg: EditText
    private lateinit var ramBar: ProgressBar
    private lateinit var ramTxt: TextView
    private lateinit var cpuBar: ProgressBar
    private lateinit var cpuTxt: TextView
    private lateinit var tmpBar: ProgressBar
    private lateinit var tmpTxt: TextView
    private var cardIdx = 0
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (ready()) Thread { val o = Sh.run(MON); runOnUiThread { show(o) } }.start()
            h.postDelayed(this, 3000)
        }
    }
    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun anim(v: String) = "for k in window_animation_scale transition_animation_scale animator_duration_scale; do settings put global \$k $v; done"
    private fun bg(c: Int, r: Int, s: Int = 0) = GradientDrawable().apply {
        setColor(c); cornerRadius = dp(r).toFloat(); if (s != 0) setStroke(dp(1), s)
    }

    private fun ready() = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    private fun refresh() {
        status.text = when {
            ready() -> "● Shizuku متصل"
            try { Shizuku.pingBinder() } catch (e: Throwable) { false } -> "● Shizuku يعمل، اضغط هنا لمنح الصلاحية"
            else -> "● Shizuku غير مشغّل، شغّله أولاً"
        }
        status.setTextColor(if (ready()) gr else og)
    }

    private fun exec(raw: String) {
        if (!ready()) {
            try { if (Shizuku.pingBinder()) Shizuku.requestPermission(1) } catch (e: Throwable) {}
            toast("Shizuku غير جاهز"); refresh(); return
        }
        var cmd = raw
        if (raw.contains("%PKG%")) {
            val p = pkg.text.toString().trim()
            if (p.isEmpty()) { toast("اكتب اسم الحزمة أولاً"); return }
            cmd = raw.replace("%PKG%", p)
        }
        toast("جارٍ التنفيذ...")
        Thread {
            val r = Sh.run(cmd)
            runOnUiThread {
                if (r.length > 40) AlertDialog.Builder(this@MainActivity).setMessage(r).setPositiveButton("حسناً", null).show()
                else toast(r)
            }
        }.start()
    }

    private fun card(title: String, accent: Int): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(0x1AFFFFFF, 20, 0x33FFFFFF)
            setPadding(dp(12), dp(14), dp(12), dp(8))
        }
        c.addView(TextView(this).apply {
            text = title; setTextColor(accent); textSize = 17f
            setTypeface(null, Typeface.BOLD); setPadding(dp(4), 0, 0, dp(8))
        })
        root.addView(c, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(14)) })
        c.alpha = 0f; c.translationY = 60f
        c.animate().alpha(1f).translationY(0f).setStartDelay(80L * cardIdx++).setDuration(450).start()
        return c
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun mk(label: String, accent: Int, cmd: String) = TextView(this).apply {
        text = label; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        setPadding(dp(8), dp(12), dp(8), dp(12))
        background = bg((accent and 0xFFFFFF) or 0x33000000, 14, (accent and 0xFFFFFF) or 0x66000000)
        setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(.95f).scaleY(.95f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
        setOnClickListener { it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); exec(cmd) }
    }

    private fun grid(p: LinearLayout, accent: Int, vararg items: Pair<String, String>) {
        items.toList().chunked(2).forEach { ch ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            ch.forEach { (l, c) ->
                row.addView(mk(l, accent, c), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            if (ch.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            p.addView(row)
        }
    }

    private fun meter(p: LinearLayout, c: Int): Pair<ProgressBar, TextView> {
        val t = TextView(this).apply { setTextColor(Color.WHITE); textSize = 13f; text = "..." }
        val b = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(c)
            progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
        }
        p.addView(t)
        p.addView(b, LinearLayout.LayoutParams(-1, dp(10)).apply { setMargins(0, dp(2), 0, dp(10)) })
        return b to t
    }

    private fun num(s: String) = s.filter { it.isDigit() }.toLongOrNull() ?: 0L

    private fun show(o: String) {
        var tot = 0L; var av = 0L; var tmp = 0L; var lvl = 0L; var cur = 0L; var mx = 0L; var n = 0
        for (l in o.lines()) {
            val t = l.trim()
            when {
                t.startsWith("MemTotal") -> tot = num(t)
                t.startsWith("MemAvailable") -> av = num(t)
                t.startsWith("temperature") -> tmp = num(t)
                t.startsWith("level") -> lvl = num(t)
                t.startsWith("F ") -> {
                    val p = t.split(" ")
                    if (p.size >= 3) { cur += p[1].toLongOrNull() ?: 0L; mx += p[2].toLongOrNull() ?: 0L; n++ }
                }
            }
        }
        if (tot > 0) {
            val u = ((tot - av) * 100 / tot).toInt()
            ramBar.setProgress(u, true)
            ramTxt.text = "🧠 RAM: ${(tot - av) / 1024} / ${tot / 1024} MB  ($u%)"
        }
        if (mx > 0 && n > 0) {
            val u = (cur * 100 / mx).toInt()
            cpuBar.setProgress(u, true)
            cpuTxt.text = "🔥 CPU: ${cur / n / 1000} MHz  ($u%)"
        }
        val t = tmp / 10.0
        tmpBar.setProgress((t * 100 / 60).toInt().coerceIn(0, 100), true)
        tmpTxt.text = "🌡 الحرارة: $t°C   🔋 $lvl%"
    }

    override fun onCreate(b: Bundle?) {
        setTheme(android.R.style.Theme_DeviceDefault_NoActionBar)
        super.onCreate(b)
        window.statusBarColor = bg1; window.navigationBarColor = bg2
        try { Shizuku.addRequestPermissionResultListener(permListener) } catch (e: Throwable) {}
        val sv = ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(bg1, bg2))
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(28), dp(14), dp(28))
        }
        sv.addView(root); setContentView(sv)

        root.addView(TextView(this).apply {
            text = "⚡ PERF TUNER"; textSize = 30f; setTypeface(null, Typeface.BOLD)
            paint.shader = LinearGradient(0f, 0f, dp(260).toFloat(), 0f, cy, pu, Shader.TileMode.CLAMP)
        })
        status = TextView(this).apply {
            textSize = 13f; setPadding(0, dp(4), 0, dp(12)); setOnClickListener { exec("echo ok") }
        }
        root.addView(status)
        val boost = TextView(this).apply {
            text = "🚀  تعزيز شامل بضغطة واحدة"; textSize = 17f; gravity = Gravity.CENTER
            setTextColor(Color.BLACK); setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(18), dp(16), dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(cy, pu)).apply { cornerRadius = dp(28).toFloat() }
            setOnClickListener { it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); exec(BOOST) }
        }
        root.addView(boost, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(16)) })
        ObjectAnimator.ofPropertyValuesHolder(
            boost, PropertyValuesHolder.ofFloat("scaleX", 1f, 1.03f), PropertyValuesHolder.ofFloat("scaleY", 1f, 1.03f)
        ).apply { duration = 900; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start() }

        val d = card("📊 مراقبة مباشرة", cy)
        meter(d, cy).let { ramBar = it.first; ramTxt = it.second }
        meter(d, pk).let { cpuBar = it.first; cpuTxt = it.second }
        meter(d, og).let { tmpBar = it.first; tmpTxt = it.second }

        grid(card("🖥 الشاشة و FPS", cy), cy,
            "أنيميشن 0.5x" to anim("0.5"), "إيقاف الأنيميشن" to anim("0"),
            "أنيميشن افتراضي" to anim("1.0"), "إلغاء ضبابية النوافذ" to "settings put global disable_window_blurs 1",
            "تحديث 60Hz" to "settings put system min_refresh_rate 60; settings put system peak_refresh_rate 60",
            "تحديث 90Hz" to "settings put system min_refresh_rate 90; settings put system peak_refresh_rate 90",
            "تحديث 120Hz" to "settings put system min_refresh_rate 120; settings put system peak_refresh_rate 120",
            "تحديث تلقائي" to "settings delete system min_refresh_rate; settings delete system peak_refresh_rate")

        grid(card("🧠 الذاكرة RAM", pu), pu,
            "إغلاق الخلفية" to "am kill-all",
            "إغلاق كل تطبيقات المستخدم" to "for p in \$(pm list packages -3 | cut -d: -f2); do [ \$p != com.perf.tuner ] && am force-stop \$p; done",
            "مسح الكاش لكل التطبيقات" to "pm trim-caches 999G",
            "صيانة الذاكرة" to "cmd activity idle-maintenance")

        grid(card("🔥 المعالج CPU", pk), pk,
            "وضع الأداء الثابت ON" to "cmd power set-fixed-performance-mode-enabled true",
            "وضع الأداء الثابت OFF" to "cmd power set-fixed-performance-mode-enabled false",
            "تجاوز الحرارة (تجريبي)" to "cmd thermalservice override-status 0",
            "إعادة الحرارة للطبيعي" to "cmd thermalservice reset",
            "إلغاء توفير الطاقة" to "settings put global low_power 0; cmd power set-adaptive-power-saver-enabled false",
            "تحسين سريع للتطبيقات" to "cmd package bg-dexopt-job",
            "تجميع كل التطبيقات (بطيء)" to "cmd package compile -m speed-profile -a")

        grid(card("🎮 المعالج الرسومي GPU", og), og,
            "Skia GL" to "setprop debug.hwui.renderer skiagl",
            "Skia Vulkan (تجريبي)" to "setprop debug.hwui.renderer skiavk",
            "تعطيل HW overlays" to "service call SurfaceFlinger 1008 i32 1",
            "إعادة GPU للأصل" to "setprop debug.hwui.renderer ''; service call SurfaceFlinger 1008 i32 0")

        grid(card("👆 اللمس", gr), gr,
            "حساسية المؤشر 7" to "settings put system pointer_speed 7",
            "حساسية المؤشر 3" to "settings put system pointer_speed 3",
            "ضغطة طويلة 250ms" to "settings put secure long_press_timeout 250",
            "ضغطة مزدوجة أسرع" to "settings put secure multi_press_timeout 200",
            "إظهار اللمسات" to "settings put system show_touches 1",
            "إخفاء اللمسات" to "settings put system show_touches 0",
            "إظهار إحداثيات اللمس" to "settings put system pointer_location 1",
            "إخفاء الإحداثيات" to "settings put system pointer_location 0")

        val g = card("🎯 تطبيق أو لعبة محددة", cy)
        pkg = EditText(this).apply {
            hint = "مثال: com.tencent.ig"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
            textSize = 14f; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT
            background = bg(0x22FFFFFF, 12, 0x44FFFFFF); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        g.addView(pkg, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(3), 0, dp(3), dp(6)) })
        grid(g, cy,
            "وضع الأداء" to "cmd game set --mode performance %PKG%",
            "الوضع القياسي" to "cmd game set --mode standard %PKG%",
            "FPS 60" to "cmd game set --mode performance --fps 60 %PKG%",
            "FPS 90" to "cmd game set --mode performance --fps 90 %PKG%",
            "FPS 120" to "cmd game set --mode performance --fps 120 %PKG%",
            "دقة كاملة (بدون downscale)" to "cmd game set --mode performance --downscale disable %PKG%",
            "إيقاف التطبيق" to "am force-stop %PKG%",
            "تجميع speed للتطبيق" to "cmd package compile -m speed -f %PKG%",
            "تقييد الخلفية" to "cmd appops set %PKG% RUN_IN_BACKGROUND ignore",
            "إلغاء التقييد" to "cmd appops set %PKG% RUN_IN_BACKGROUND allow",
            "استثناء من Doze" to "cmd deviceidle whitelist +%PKG%",
            "معلومات الحزمة" to "dumpsys package %PKG% | grep -E 'versionName|firstInstallTime'")

        grid(card("ℹ️ أدوات", pu), pu,
            "معلومات الجهاز" to "getprop ro.product.model; getprop ro.board.platform; getprop ro.hardware; getprop ro.build.version.release",
            "أكثر التطبيقات استهلاكاً" to "dumpsys cpuinfo | head -12",
            "♻️ استعادة كل الإعدادات" to RESET)
    Profiles.add(this, root) { exec(it) }
    Extras.add(this, root) { exec(it) }
    Legend.install(this, root) { exec(it) }
}
    override fun onResume() { super.onResume(); refresh(); h.post(tick) }
    override fun onPause() { h.removeCallbacks(tick); super.onPause() }
    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(permListener) } catch (e: Throwable) {}
        super.onDestroy()
    }
}
