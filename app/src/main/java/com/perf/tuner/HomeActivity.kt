package com.perf.tuner

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class HomeActivity : Activity() {
    private val CY = Color.parseColor("#00E5FF")
    private val PU = Color.parseColor("#B388FF")
    private val PK = Color.parseColor("#FF4081")
    private val GR = Color.parseColor("#69F0AE")
    private val OG = Color.parseColor("#FFAB40")
    private val MUTED = Color.parseColor("#AAB4D4")

    private lateinit var sp: SharedPreferences
    private lateinit var bg: AnimeBg
    private lateinit var content: FrameLayout
    private lateinit var bar: FrameLayout
    private lateinit var pill: View
    private lateinit var statusTv: TextView
    private lateinit var modTv: TextView
    private lateinit var stateTv: TextView
    private lateinit var modBar: ProgressBar
    private lateinit var masterSw: Switch
    private lateinit var ramTv: TextView
    private lateinit var ramBar: ProgressBar
    private lateinit var cpuTv: TextView
    private lateinit var cpuBar: ProgressBar
    private lateinit var tmpTv: TextView
    private lateinit var tmpBar: ProgressBar
    private lateinit var chart: Chart
    private lateinit var pkgIn: EditText
    private val pages = ArrayList<ScrollView>()
    private val lists = ArrayList<LinearLayout>()
    private val swViews = HashMap<String, Switch>()
    private val tabIcon = arrayOfNulls<TextView>(3)
    private val tabLabel = arrayOfNulls<TextView>(3)
    private var cur = 0
    private var busy = false
    private var resumed = false
    private val h = Handler(Looper.getMainLooper())
    private val permL = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    private val tick = object : Runnable {
        override fun run() {
            if (!resumed) return
            if (ready()) {
                Thread {
                    val o = Sh.run(MainActivity.MON)
                    runOnUiThread { monitor(o) }
                }.start()
            }
            h.postDelayed(this, 3000)
        }
    }

    // ---------- أدوات مساعدة ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun rr(c: Int, r: Int, s: Int = 0) = GradientDrawable().apply {
        setColor(c)
        cornerRadius = dp(r).toFloat()
        if (s != 0) setStroke(dp(1), s)
    }

    private fun ready() = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    private fun refresh() {
        if (!::statusTv.isInitialized) return
        val ping = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
        statusTv.text = if (ready()) "● Shizuku متصل" else if (ping) "● اضغط هنا لمنح صلاحية Shizuku" else "● Shizuku غير مشغّل، شغّله أولاً"
        statusTv.setTextColor(if (ready()) GR else OG)
    }

    private fun exec(cmd: String, snd: String = "tap") {
        Sfx.play(snd)
        if (!ready()) {
            try { if (Shizuku.pingBinder()) Shizuku.requestPermission(1) } catch (e: Throwable) {}
            toast("شغّل Shizuku وامنح الصلاحية أولاً")
            refresh()
            return
        }
        Thread {
            val r = Sh.run(cmd)
            runOnUiThread {
                if (r.length > 60) AlertDialog.Builder(this).setMessage(r).setPositiveButton("حسناً", null).show()
                else toast(r)
            }
        }.start()
    }

    private fun ask(msg: String?, go: () -> Unit) {
        if (msg == null) go()
        else AlertDialog.Builder(this).setMessage(msg)
            .setPositiveButton("نفّذ") { _, _ -> go() }
            .setNegativeButton("إلغاء", null).show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun press(v: View) {
        v.setOnTouchListener { x, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> x.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    x.animate().scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(OvershootInterpolator(3f)).start()
            }
            false
        }
    }

    private fun card(parent: LinearLayout, title: String? = null): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = rr(Color.parseColor("#B30B0F24"), 22, Color.parseColor("#33FFFFFF"))
        c.setPadding(dp(14), dp(14), dp(14), dp(10))
        if (title != null) {
            c.addView(TextView(this).apply {
                text = title; setTextColor(Color.WHITE); textSize = 17f
                setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, dp(8))
            })
        }
        parent.addView(c, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(14)) })
        return c
    }

    private fun divider(c: LinearLayout) {
        c.addView(View(this).apply { setBackgroundColor(Color.parseColor("#22FFFFFF")) }, LinearLayout.LayoutParams(-1, 1))
    }

    private fun btn(label: String, col: Int, f: () -> Unit): TextView = TextView(this).apply {
        text = label; textSize = 13f; gravity = Gravity.CENTER
        setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        setPadding(dp(8), dp(12), dp(8), dp(12))
        background = rr((col and 0xFFFFFF) or 0x40000000, 16, (col and 0xFFFFFF) or 0x66000000)
        press(this)
        setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); f() }
    }

    private fun grid(c: LinearLayout, items: List<View>) {
        items.chunked(2).forEach { ch ->
            val row = LinearLayout(this)
            ch.forEach { v -> row.addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }) }
            if (ch.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            c.addView(row)
        }
    }

    private fun mkSwitch(): Switch {
        val sw = Switch(this)
        val st = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.trackTintList = ColorStateList(st, intArrayOf(PK, Color.parseColor("#55FFFFFF")))
        sw.thumbTintList = ColorStateList(st, intArrayOf(Color.WHITE, Color.parseColor("#CCCCCC")))
        return sw
    }

    private fun titleDesc(title: String, desc: String): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.addView(TextView(this).apply { text = title; setTextColor(Color.WHITE); textSize = 15f; setTypeface(null, Typeface.BOLD) })
        if (desc.isNotEmpty()) c.addView(TextView(this).apply { text = desc; setTextColor(MUTED); textSize = 12f })
        return c
    }

    // ---------- الصفوف ----------
    private fun setSw(id: String, v: Boolean) {
        sp.edit().putBoolean("sw_$id", v).apply()
        busy = true
        swViews[id]?.isChecked = v
        busy = false
        updateMaster()
    }

    private fun toggle(s: Sw, b: Boolean) {
        val go = {
            sp.edit().putBoolean("sw_" + s.id, b).apply()
            exec(if (b) s.on else s.off, if (b) "on" else "off")
            updateMaster()
        }
        if (b && s.warn != null) {
            AlertDialog.Builder(this).setMessage(s.warn)
                .setPositiveButton("تفعيل") { _, _ -> go() }
                .setNegativeButton("إلغاء") { _, _ -> setSw(s.id, false) }
                .setOnCancelListener { setSw(s.id, false) }.show()
        } else go()
    }

    private fun swRow(c: LinearLayout, s: Sw) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(8), 0, dp(8))
        row.addView(titleDesc(s.title, s.desc), LinearLayout.LayoutParams(0, -2, 1f))
        val sw = mkSwitch()
        sw.isChecked = sp.getBoolean("sw_" + s.id, false)
        sw.setOnCheckedChangeListener { _, b -> if (!busy) toggle(s, b) }
        swViews[s.id] = sw
        row.addView(sw)
        c.addView(row)
        divider(c)
    }

    private fun plainSwitch(c: LinearLayout, title: String, desc: String, init: Boolean, f: (Boolean) -> Unit) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(8), 0, dp(8))
        row.addView(titleDesc(title, desc), LinearLayout.LayoutParams(0, -2, 1f))
        val sw = mkSwitch()
        sw.isChecked = init
        sw.setOnCheckedChangeListener { _, b -> f(b) }
        row.addView(sw)
        c.addView(row)
        divider(c)
    }

    private fun slRow(c: LinearLayout, s: Sl) {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(0, dp(8), 0, dp(8))
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.addView(TextView(this).apply { text = s.title; setTextColor(Color.WHITE); textSize = 15f; setTypeface(null, Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
        val vt = TextView(this)
        vt.setTextColor(PK); vt.textSize = 14f; vt.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        head.addView(vt)
        col.addView(head)
        col.addView(TextView(this).apply { text = s.desc; setTextColor(MUTED); textSize = 12f })
        val saved = sp.getInt("sl_" + s.id, s.def)
        vt.text = s.fmt(saved)
        val sb = SeekBar(this)
        sb.max = (s.hi - s.lo) / s.step
        sb.progress = (saved - s.lo) / s.step
        sb.progressTintList = ColorStateList.valueOf(PK)
        sb.thumbTintList = ColorStateList.valueOf(PK)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b: SeekBar?, p: Int, u: Boolean) { vt.text = s.fmt(s.lo + p * s.step) }
            override fun onStartTrackingTouch(b: SeekBar?) {}
            override fun onStopTrackingTouch(b: SeekBar?) {
                val v = s.lo + (b?.progress ?: 0) * s.step
                sp.edit().putInt("sl_" + s.id, v).apply()
                exec(s.cmd(v), "nav")
            }
        })
        col.addView(sb)
        c.addView(col)
        divider(c)
    }

    private fun chRow(c: LinearLayout, ch: Ch) {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(0, dp(8), 0, dp(6))
        col.addView(titleDesc(ch.title, ""))
        val row = LinearLayout(this)
        val chips = ArrayList<TextView>()
        fun paint(sel: Int) {
            chips.forEachIndexed { i, t ->
                val on = i == sel
                t.background = rr(if (on) Color.parseColor("#66FF4081") else Color.parseColor("#22FFFFFF"), 12, if (on) PK else Color.parseColor("#44FFFFFF"))
                t.setTextColor(if (on) Color.WHITE else Color.parseColor("#C5CCE8"))
            }
        }
        ch.opts.forEachIndexed { i, o ->
            val t = TextView(this)
            t.text = o.first; t.textSize = 12f; t.gravity = Gravity.CENTER
            t.setPadding(dp(4), dp(10), dp(4), dp(10))
            press(t)
            t.setOnClickListener {
                sp.edit().putInt("ch_" + ch.id, i).apply()
                paint(i)
                exec(o.second, "nav")
            }
            chips.add(t)
            row.addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), dp(6), dp(2), dp(2)) })
        }
        paint(sp.getInt("ch_" + ch.id, ch.def))
        col.addView(row)
        c.addView(col)
        divider(c)
    }

    // ---------- القوائم والأوامر المركبة ----------
    private fun pkgs(k: String): List<String> = (sp.getStringSet("lst_$k", emptySet<String>()) ?: emptySet<String>()).toList()

    private fun listCmd(k: String): String {
        val sb = StringBuilder()
        for (p in pkgs(k)) {
            if (k == "protect") sb.append("cmd deviceidle whitelist +$p; cmd appops set $p RUN_ANY_IN_BACKGROUND allow; cmd appops set $p RUN_IN_BACKGROUND allow; am set-standby-bucket $p active; ")
            else if (k == "restrict") sb.append("cmd appops set $p RUN_IN_BACKGROUND ignore; cmd appops set $p RUN_ANY_IN_BACKGROUND ignore; am set-standby-bucket $p restricted; am force-stop $p; ")
            else sb.append("am force-stop $p; ")
        }
        return sb.toString().ifEmpty { "echo ok; " }
    }

    private fun undoCmd(): String {
        val sb = StringBuilder()
        for (p in pkgs("restrict")) sb.append("cmd appops set $p RUN_IN_BACKGROUND allow; cmd appops set $p RUN_ANY_IN_BACKGROUND allow; am set-standby-bucket $p working_set; ")
        for (p in pkgs("protect")) sb.append("cmd deviceidle whitelist -$p; ")
        return sb.toString().ifEmpty { "echo ok; " }
    }

    private fun pick(k: String, title: String) {
        if (!ready()) { toast("شغّل Shizuku أولاً"); return }
        toast("جارٍ تحميل التطبيقات...")
        Thread {
            val all = Sh.run("pm list packages -3 | cut -d: -f2 | sort").lines().map { it.trim() }
                .filter { it.contains('.') && !it.startsWith("Error") && it != "com.perf.tuner" }
            runOnUiThread {
                val saved = pkgs(k).toSet()
                val checked = BooleanArray(all.size) { all[it] in saved }
                AlertDialog.Builder(this).setTitle(title)
                    .setMultiChoiceItems(all.toTypedArray(), checked) { _, i, b -> checked[i] = b }
                    .setPositiveButton("حفظ") { _, _ ->
                        val set = HashSet<String>()
                        for (i in all.indices) if (checked[i]) set.add(all[i])
                        sp.edit().putStringSet("lst_$k", set).apply()
                        toast("تم حفظ " + set.size + " تطبيق")
                    }
                    .setNegativeButton("إلغاء", null).show()
            }
        }.start()
    }

    private fun masterCmd(on: Boolean): String {
        val sb = StringBuilder()
        for (id in Cat.master) {
            val s = Cat.sw(id)
            val v = sp.getBoolean("sw_$id", false)
            if (on && !v) { sb.append(s.on).append("; "); setSw(id, true) }
            if (!on && v) { sb.append(s.off).append("; "); setSw(id, false) }
        }
        if (on) sb.append("am kill-all; pm trim-caches 999G")
        return sb.toString().ifEmpty { "echo ok" }
    }

    private fun updateMaster() {
        if (!::masterSw.isInitialized) return
        val n = Cat.master.count { sp.getBoolean("sw_$it", false) }
        val tot = Cat.master.size
        modTv.text = "$n/$tot"
        stateTv.text = if (n == tot) "نشط ✔" else if (n > 0) "جزئي" else "جاهز"
        stateTv.setTextColor(if (n == tot) GR else if (n > 0) OG else Color.WHITE)
        modBar.setProgress(n * 100 / tot, true)
        busy = true
        masterSw.isChecked = n == tot
        busy = false
    }

    private fun restoreAll() {
        val e = sp.edit()
        for (k in sp.all.keys.toList()) if (k.startsWith("sw_") || k.startsWith("ch_") || k.startsWith("sl_")) e.remove(k)
        e.apply()
        exec(Cat.restoreCmd() + "; " + undoCmd(), "off")
        recreate()
    }

    // ---------- الإشعار ----------
    private fun showNotif() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("boost", "Boost", NotificationManager.IMPORTANCE_LOW))
        fun pi(a: String, c: Int) = PendingIntent.getBroadcast(
            this, c, Intent(this, BoostReceiver::class.java).setAction(a),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "boost") else Notification.Builder(this)
        b.setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("⚡ Perf Tuner")
            .setContentText("تعزيز سريع بلمسة واحدة")
            .setOngoing(true)
            .addAction(0, "🚀 تعزيز", pi("perf.BOOST", 1))
            .addAction(0, "♻️ استعادة", pi("perf.RESET", 2))
        nm.notify(77, b.build())
    }

    private fun hideNotif() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(77)
    }

    // ---------- المراقبة ----------
    private fun meter(c: LinearLayout, color: Int): Pair<TextView, ProgressBar> {
        val t = TextView(this)
        t.setTextColor(Color.WHITE); t.textSize = 13f; t.text = "..."
        val b = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        b.max = 100
        b.progressTintList = ColorStateList.valueOf(color)
        b.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
        c.addView(t)
        c.addView(b, LinearLayout.LayoutParams(-1, dp(10)).apply { setMargins(0, dp(2), 0, dp(10)) })
        return Pair(t, b)
    }

    private fun monitor(o: String) {
        var tot = 0L; var av = 0L; var tmp = 0L; var lvl = 0L; var fc = 0L; var fm = 0L; var n = 0
        for (l in o.lines()) {
            val s = l.trim()
            val v = s.filter { it.isDigit() }.toLongOrNull() ?: 0L
            if (s.startsWith("MemTotal")) tot = v
            else if (s.startsWith("MemAvailable")) av = v
            else if (s.startsWith("temperature")) tmp = v
            else if (s.startsWith("level")) lvl = v
            else if (s.startsWith("F ")) {
                val p = s.split(" ")
                if (p.size >= 3) { fc += p[1].toLongOrNull() ?: 0L; fm += p[2].toLongOrNull() ?: 0L; n++ }
            }
        }
        val t = tmp / 10.0
        tmpBar.setProgress((t * 100 / 60).toInt().coerceIn(0, 100), true)
        tmpTv.text = "🌡 الحرارة: ${t}°C    🔋 $lvl%"
        if (tot > 0) {
            val u = ((tot - av) * 100 / tot).toInt()
            ramBar.setProgress(u, true)
            ramTv.text = "🧠 الذاكرة: ${(tot - av) / 1024} / ${tot / 1024} MB ($u%)"
            chart.push(u.toFloat(), (t * 100 / 60).toFloat())
        }
        if (fm > 0 && n > 0) {
            val u = (fc * 100 / fm).toInt()
            cpuBar.setProgress(u, true)
            cpuTv.text = "🔥 المعالج: ${fc / n / 1000} MHz ($u%)"
        }
    }

    // ---------- بناء الصفحات ----------
    private fun page(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(14), dp(26), dp(14), dp(24))
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(l)
        content.addView(sv, FrameLayout.LayoutParams(-1, -1))
   
