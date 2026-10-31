package com.perf.tuner

import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.cos
import kotlin.math.sin

class BoostReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val pr = goAsync()
        Thread {
            val cmd = if (i.action == "perf.RESET") MainActivity.RESET else MainActivity.BOOST
            val r = Sh.run(cmd)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(c, if (r.startsWith("Error")) "Shizuku غير جاهز" else "تم ⚡", Toast.LENGTH_SHORT).show()
            }
            pr.finish()
        }.start()
    }
}

class FxView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val seed = FloatArray(36) { (it * 97 % 53 + 7).toFloat() }
    private val cols = intArrayOf(Color.parseColor("#00E5FF"), Color.parseColor("#B388FF"), Color.parseColor("#FF4081"))
    private var t = 0f
    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.shader = LinearGradient(0f, 0f, 0f, h, Color.parseColor("#05060D"), Color.parseColor("#160B34"), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        for (i in 0..2) {
            val cx = w * (0.5f + 0.45f * sin(t * (0.5f + i * 0.2f) + i * 2f))
            val cy = h * (0.2f + 0.3f * i) + 70f * cos(t * 0.4f + i)
            val r = w * 0.75f
            p.shader = RadialGradient(cx, cy, r, (cols[i] and 0xFFFFFF) or 0x38000000, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
        }
        p.shader = null
        for (i in 0 until 36) {
            val x = (seed[i] * 53f + i * 29f) % w
            val y = h - ((seed[i] * 40f + t * (20f + (i % 6) * 8f)) % h)
            p.color = Color.argb(50 + (i % 5) * 28, 255, 255, 255)
            c.drawCircle(x, y, 1.5f + (i % 3), p)
        }
        t += 0.05f
        postInvalidateDelayed(45)
    }
}

class Chart(ctx: Context) : View(ctx) {
    private val ram = ArrayList<Float>()
    private val tmp = ArrayList<Float>()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun push(r: Float, t: Float) {
        ram.add(r); tmp.add(t)
        if (ram.size > 40) { ram.removeAt(0); tmp.removeAt(0) }
        invalidate()
    }

    private fun line(c: Canvas, d: List<Float>, col: Int) {
        if (d.size < 2) return
        val w = width.toFloat()
        val h = height.toFloat()
        val path = Path()
        d.forEachIndexed { i, v ->
            val x = w * i / 39f
            val y = h - h * v.coerceIn(0f, 100f) / 100f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.color = col
        c.drawPath(path, p)
    }

    override fun onDraw(c: Canvas) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = Color.parseColor("#22FFFFFF")
        for (i in 1..3) c.drawLine(0f, height * i / 4f, width.toFloat(), height * i / 4f, p)
        line(c, ram, Color.parseColor("#00E5FF"))
        line(c, tmp, Color.parseColor("#FFAB40"))
    }
}

object Legend {
    private const val ANIM = "for k in window_animation_scale transition_animation_scale animator_duration_scale; do settings put global \$k "
    private const val MAX = "0.5; done; am kill-all; pm trim-caches 999G; cmd power set-fixed-performance-mode-enabled true; settings put global low_power 0; cmd power set-adaptive-power-saver-enabled false; settings put system pointer_speed 5; sm idle-maint run"
    private const val BAL = "0.7; done; cmd power set-fixed-performance-mode-enabled false; settings put global low_power 0; settings put system pointer_speed 0"
    private const val SAVE = "0.5; done; cmd power set-fixed-performance-mode-enabled false; settings put global low_power 1; cmd netpolicy set restrict-background true"

    fun install(a: Activity, root: LinearLayout, run: (String) -> Unit) {
        val content = a.findViewById<ViewGroup>(android.R.id.content)
        content.addView(FxView(a), 0, ViewGroup.LayoutParams(-1, -1))
        content.getChildAt(1)?.setBackgroundColor(Color.TRANSPARENT)
        chartCard(a, root)
        deepCard(a, root, run)
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 7)
        } else {
            showNotif(a)
        }
    }

    private fun showNotif(c: Context) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("boost", "Boost", NotificationManager.IMPORTANCE_LOW))
        }
        fun pi(act: String, code: Int) = PendingIntent.getBroadcast(
            c, code, Intent(c, BoostReceiver::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, "boost") else Notification.Builder(c)
        b.setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("⚡ Perf Tuner")
            .setContentText("تعزيز سريع بلمسة واحدة")
            .setOngoing(true)
            .addAction(0, "🚀 تعزيز", pi("perf.BOOST", 1))
            .addAction(0, "♻️ استعادة", pi("perf.RESET", 2))
        nm.notify(77, b.build())
    }

    private fun hideNotif(c: Context) {
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(77)
    }

    private fun mkCard(a: Context, title: String, col: Int): LinearLayout {
        val d = a.resources.displayMetrics.density
        return LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1AFFFFFF"))
                cornerRadius = 20 * d
                setStroke(d.toInt().coerceAtLeast(1), Color.parseColor("#33FFFFFF"))
            }
            setPadding((12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            addView(TextView(a).apply {
                text = title; setTextColor(col); textSize = 17f; setTypeface(null, Typeface.BOLD)
            })
        }
    }

    private fun put(root: LinearLayout, v: View) {
        val d = root.resources.displayMetrics.density
        root.addView(v, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, (14 * d).toInt()) })
    }

    private fun parse(o: String): Pair<Float, Float> {
        var tot = 0L
        var av = 0L
        var tmp = 0L
        for (l in o.lines()) {
            val s = l.trim()
            val n = s.filter { it.isDigit() }.toLongOrNull() ?: 0L
            if (s.startsWith("MemTotal")) tot = n
            else if (s.startsWith("MemAvailable")) av = n
            else if (s.startsWith("temperature")) tmp = n
        }
        if (tot == 0L) return Pair(-1f, 0f)
        return Pair((tot - av) * 100f / tot, tmp / 10f * 100f / 60f)
    }

    private fun chartCard(a: Activity, root: LinearLayout) {
        val d = a.resources.displayMetrics.density
        val card = mkCard(a, "📈 رسم بياني مباشر", Color.parseColor("#00E5FF"))
        card.addView(TextView(a).apply {
            text = "🔵 RAM %      🟠 الحرارة"; setTextColor(Color.WHITE); textSize = 12f
        })
        val ch = Chart(a)
        card.addView(ch, LinearLayout.LayoutParams(-1, (140 * d).toInt()).apply {
            setMargins(0, (8 * d).toInt(), 0, (4 * d).toInt())
        })
        put(root, card)
        val h = Handler(Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                if (a.isFinishing) return
                if (ch.windowVisibility == View.VISIBLE) {
                    Thread {
                        val r = parse(Sh.run(MainActivity.MON))
                        h.post { if (r.first >= 0f) ch.push(r.first, r.second) }
                    }.start()
                }
                h.postDelayed(this, 3000)
            }
        }
        h.post(tick)
    }

    private fun t(l: String, c: String, w: String? = null): Triple<String, String, String?> = Triple(l, c, w)

    private fun btn(a: Context, label: String, col: Int, warn: String?, run: (String) -> Unit, cmd: String): TextView {
        val d = a.resources.displayMetrics.density
        return TextView(a).apply {
            text = label; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setPadding((8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt())
            background = GradientDrawable().apply {
                setColor((col and 0xFFFFFF) or 0x33000000)
                cornerRadius = 14 * d
                setStroke(d.toInt().coerceAtLeast(1), (col and 0xFFFFFF) or 0x66000000)
            }
            setOnClickListener {
                if (warn == null) run(cmd)
                else AlertDialog.Builder(a).setMessage(warn)
                    .setPositiveButton("نفّذ") { _, _ -> run(cmd) }
                    .setNegativeButton("إلغاء", null).show()
            }
        }
    }

    private fun deepCard(a: Activity, root: LinearLayout, run: (String) -> Unit) {
        val d = a.resources.displayMetrics.density
        val col = Color.parseColor("#FF4081")
        val card = mkCard(a, "☢️ أوامر عميقة وأوضاع ذكية", col)
        val runner: (String) -> Unit = { c ->
            when (c) {
                "@on" -> showNotif(a)
                "@off" -> hideNotif(a)
                else -> run(c)
            }
        }
        val items = listOf(
            t("🔥 وضع الأداء الأقصى", ANIM + MAX, "يرفع الأداء لكنه يزيد الحرارة والبطارية. للألعاب فقط، وللرجوع اضغط استعادة. متابعة؟"),
            t("⚖️ وضع متوازن", ANIM + BAL),
            t("🔋 وضع التوفير", ANIM + SAVE, "يفعّل توفير الطاقة وتقييد بيانات الخلفية. متابعة؟"),
            t("🔔 إظهار إشعار التعزيز", "@on"),
            t("🔕 إخفاء الإشعار", "@off"),
            t("👻 إيقاف قاتل العمليات الوهمية", "device_config set_sync_disabled_for_tests persistent; device_config put activity_manager max_phantom_processes 2147483647; settings put global settings_enable_monitor_phantom_procs false"),
            t("🧠 تقليل العمليات المخبأة (16)", "device_config put activity_manager max_cached_processes 16"),
            t("↩️ إرجاع العمليات للأصل", "device_config delete activity_manager max_cached_processes"),
            t("😴 إيقاف Doze", "dumpsys deviceidle disable", "إيقاف Doze يستهلك البطارية أسرع. متابعة؟"),
            t("😴 تفعيل Doze", "dumpsys deviceidle enable"),
            t("📡 إيقاف المسح الخلفي للشبكات", "settings put global wifi_scan_always_enabled 0; settings put global ble_scan_always_enabled 0"),
            t("👆 لمس أسرع", "settings put system pointer_speed 7; settings put secure long_press_timeout 200; settings put secure multi_press_timeout 150"),
            t("📳 إيقاف الاهتزاز والأصوات", "settings put system haptic_feedback_enabled 0; settings put system sound_effects_enabled 0"),
            t("🧾 تقليل سجلات النظام", "logcat -c; logcat -G 64K"),
            t("🧹 صيانة التخزين", "sm idle-maint run"),
            t("📋 التطبيقات العاملة بالخلفية", "dumpsys activity processes | grep 'Proc #' | head -25")
        )
        items.chunked(2).forEach { ch ->
            val row = LinearLayout(a)
            ch.forEach { (l, c, w) ->
                row.addView(btn(a, l, col, w, runner, c), LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins((3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt())
                })
            }
            if (ch.size == 1) row.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
            card.addView(row)
        }
        put(root, card)
    }
}
