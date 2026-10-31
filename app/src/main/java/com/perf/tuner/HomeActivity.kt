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
        pages.add(sv)
        lists.add(l)
        return l
    }
    private fun buildHome(l: LinearLayout) {
        val t = TextView(this)
        t.text = "⚡ PERF TUNER"; t.textSize = 30f; t.setTypeface(null, Typeface.BOLD)
        val w = dp(300).toFloat()
        val sh = LinearGradient(0f, 0f, w, 0f, intArrayOf(CY, PU, PK, CY), null, Shader.TileMode.REPEAT)
        t.paint.shader = sh
        ValueAnimator.ofFloat(0f, w).apply {
            duration = 3200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                sh.setLocalMatrix(Matrix().apply { setTranslate(it.animatedValue as Float, 0f) })
                t.invalidate()
            }
            start()
        }
        l.addView(t)
        l.addView(TextView(this).apply { text = "مركز التحكم بأداء هاتفك"; setTextColor(MUTED); textSize = 13f })
        statusTv = TextView(this)
        statusTv.textSize = 13f
        statusTv.setPadding(0, dp(6), 0, dp(14))
        statusTv.setOnClickListener { exec("echo ok") }
        l.addView(statusTv)
        refresh()

        // التعزيز الرئيسي
        val mc = card(l)
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(TextView(this).apply {
            text = "⚡"; textSize = 26f; gravity = Gravity.CENTER
            background = rr(Color.parseColor("#33B388FF"), 30); setPadding(dp(14), dp(10), dp(14), dp(10))
        })
        val tc = titleDesc("التعزيز الرئيسي", "لمسة واحدة لتسريع ألعابك")
        tc.setPadding(dp(12), 0, dp(12), 0)
        top.addView(tc, LinearLayout.LayoutParams(0, -2, 1f))
        masterSw = mkSwitch()
        masterSw.setOnCheckedChangeListener { _, b -> if (!busy) { if (b) exec(masterCmd(true), "boost") else exec(masterCmd(false), "off") } }
        top.addView(masterSw)
        mc.addView(top)
        val stats = LinearLayout(this)
        stats.orientation = LinearLayout.HORIZONTAL
        fun box(title: String): TextView {
            val v = LinearLayout(this)
            v.orientation = LinearLayout.VERTICAL
            v.background = rr(Color.parseColor("#22FFFFFF"), 14)
            v.setPadding(dp(12), dp(10), dp(12), dp(10))
            v.addView(TextView(this).apply { text = title; setTextColor(MUTED); textSize = 11f })
            val x = TextView(this)
            x.setTextColor(Color.WHITE); x.textSize = 18f; x.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            v.addView(x)
            stats.addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(10), dp(3), dp(6)) })
            return x
        }
        modTv = box("الوحدات")
        stateTv = box("الحالة")
        mc.addView(stats)
        modBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        modBar.max = 100
        modBar.progressTintList = ColorStateList.valueOf(CY)
        modBar.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
        mc.addView(modBar, LinearLayout.LayoutParams(-1, dp(8)).apply { setMargins(dp(3), 0, dp(3), dp(6)) })

        // إجراءات سريعة
        val qa = card(l, "⚡ إجراءات سريعة")
        grid(qa, listOf(
            btn("🛡 تعزيز آمن", CY) { exec("am kill-all; pm trim-caches 999G; cmd activity idle-maintenance", "boost") },
            btn("🎮 تعزيز الألعاب", PU) { exec(masterCmd(true) + "; " + Cat.GAME, "boost") },
            btn("🧠 تحرير الذاكرة", GR) { exec(Cat.FREE, "boost") },
            btn("♻️ استعادة", OG) { ask("سيرجع كل الإعدادات للوضع الأصلي. متابعة؟") { restoreAll() } },
            btn("🏆 يوم المباراة", PK) { exec(masterCmd(true) + "; " + Cat.GAME + "; " + listCmd("close") + listCmd("restrict"), "boost") },
            btn("🧹 مسح الكاش", CY) { exec("pm trim-caches 999G") }
        ))

        // المراقبة
        val m = card(l, "📊 المراقبة المباشرة")
        meter(m, CY).let { ramTv = it.first; ramBar = it.second }
        meter(m, PK).let { cpuTv = it.first; cpuBar = it.second }
        meter(m, OG).let { tmpTv = it.first; tmpBar = it.second }
        chart = Chart(this)
        m.addView(TextView(this).apply { text = "🔵 الذاكرة   🟠 الحرارة"; setTextColor(MUTED); textSize = 12f })
        m.addView(chart, LinearLayout.LayoutParams(-1, dp(130)).apply { setMargins(0, dp(6), 0, dp(6)) })
    }

    private fun buildPerf(l: LinearLayout) {
        for (s in Cat.sections) {
            val c = card(l, s.icon + " " + s.title)
            val acts = ArrayList<TextView>()
            for (x in s.items) {
                when (x) {
                    is Sw -> swRow(c, x)
                    is Sl -> slRow(c, x)
                    is Ch -> chRow(c, x)
                    is Ac -> acts.add(btn(x.title, PU) { ask(x.warn) { exec(x.cmd) } })
                    is Tip -> c.addView(TextView(this).apply { text = x.text; setTextColor(MUTED); textSize = 12f; setPadding(dp(2), dp(8), dp(2), dp(4)) })
                }
            }
            if (acts.isNotEmpty()) grid(c, acts)
        }
    }

    private fun pkgBtn(label: String, cmd: String): TextView = btn(label, CY) {
        val p = pkgIn.text.toString().trim()
        if (!Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(p)) toast("اكتب اسم حزمة صحيح")
        else exec(cmd.replace("%P%", p))
    }

    private fun listRow(c: LinearLayout, k: String, label: String) {
        c.addView(TextView(this).apply {
            text = label; setTextColor(Color.WHITE); textSize = 14f
            setTypeface(null, Typeface.BOLD); setPadding(0, dp(8), 0, dp(2))
        })
        grid(c, listOf(
            btn("📋 اختيار", CY) { pick(k, label) },
            btn("▶ تطبيق الآن", PK) { exec(listCmd(k), "boost") }
        ))
    }

    private fun buildGames(l: LinearLayout) {
        Profiles.add(this, l) { exec(it, "boost") }

        val c = card(l, "🎯 أدوات لتطبيق أو لعبة")
        pkgIn = EditText(this)
        pkgIn.hint = "اسم الحزمة مثل com.tencent.ig"
        pkgIn.setHintTextColor(Color.GRAY); pkgIn.setTextColor(Color.WHITE); pkgIn.textSize = 14f
        pkgIn.setSingleLine(); pkgIn.inputType = InputType.TYPE_CLASS_TEXT
        pkgIn.background = rr(Color.parseColor("#22FFFFFF"), 12, Color.parseColor("#44FFFFFF"))
        pkgIn.setPadding(dp(12), dp(10), dp(12), dp(10))
        c.addView(pkgIn, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(6)) })
        grid(c, listOf(
            pkgBtn("🎮 وضع الأداء", "cmd game set --mode performance %P%"),
            pkgBtn("🔓 بدون سقف FPS", "cmd game set --mode performance --downscale disable --fps disable %P%"),
            pkgBtn("⚙️ تجميع speed", "cmd package compile -m speed -f %P%"),
            pkgBtn("⭐ أولوية اللعبة", "am set-standby-bucket %P% active; cmd deviceidle whitelist +%P%"),
            pkgBtn("🚫 تقييد الخلفية", "cmd appops set %P% RUN_IN_BACKGROUND ignore"),
            pkgBtn("✅ إلغاء التقييد", "cmd appops set %P% RUN_IN_BACKGROUND allow"),
            pkgBtn("▶ تشغيل", "monkey -p %P% -c android.intent.category.LAUNCHER 1"),
            pkgBtn("⛔ إيقاف", "am force-stop %P%"),
            pkgBtn("ℹ️ معلومات", "dumpsys package %P% | grep -E 'versionName|firstInstallTime'")
        ))

        val ls = card(l, "🧠 قوائم التطبيقات")
        listRow(ls, "protect", "🛡 تطبيقات محمية (تبقى في الذاكرة)")
        listRow(ls, "restrict", "🚫 تطبيقات مقيّدة في الخلفية")
        listRow(ls, "close", "❌ تطبيقات تُغلق عند اللعب")

        val tl = card(l, "🛠 أدوات")
        plainSwitch(tl, "🔊 المؤثرات الصوتية", "أصوات ناعمة عند اللمس والتنقل", Sfx.enabled) {
            Sfx.enabled = it
            sp.edit().putBoolean("sound", it).apply()
        }
        plainSwitch(tl, "🔔 إشعار التعزيز السريع", "تعزيز واستعادة من شريط الإشعارات", false) {
            if (it) showNotif() else hideNotif()
        }
        grid(tl, listOf(
            btn("🔍 تشخيص الجهاز", CY) { exec("getprop ro.product.model; getprop ro.board.platform; getprop ro.build.version.release; wm size; wm density; dumpsys display | grep -iE 'refreshRate|fps=' | head -6") },
            btn("♻️ استعادة كل شيء", OG) { ask("سيرجع كل الإعدادات للوضع الأصلي. متابعة؟") { restoreAll() } }
        ))
    }

    // ---------- شريط التبويبات والتنقل ----------
    private fun buildBar() {
        bar = FrameLayout(this)
        bar.layoutDirection = View.LAYOUT_DIRECTION_LTR
        bar.background = rr(Color.parseColor("#EE0A0D20"), 0)
        bar.setPadding(0, dp(8), 0, dp(8))
        pill = View(this)
        pill.background = rr(Color.parseColor("#44B388FF"), 26, Color.parseColor("#88B388FF"))
        bar.addView(pill, FrameLayout.LayoutParams(dp(90), dp(56), Gravity.CENTER_VERTICAL))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val names = listOf("الرئيسية", "الأداء", "الألعاب")
        val icons = listOf("🏠", "⚡", "🎮")
        for (pos in 0..2) {
            val idx = 2 - pos
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            val ic = TextView(this)
            ic.text = icons[idx]; ic.textSize = 22f; ic.gravity = Gravity.CENTER
            val lb = TextView(this)
            lb.text = names[idx]; lb.textSize = 11f; lb.gravity = Gravity.CENTER
            cell.addView(ic)
            cell.addView(lb)
            tabIcon[idx] = ic
            tabLabel[idx] = lb
            press(cell)
            cell.setOnClickListener { show(idx) }
            row.addView(cell, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
        bar.addView(row, FrameLayout.LayoutParams(-1, dp(56)))
        bar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> movePill(false) }
    }

    private fun movePill(anim: Boolean) {
        val w = bar.width / 3f
        if (w <= 0f) return
        val lp = pill.layoutParams as FrameLayout.LayoutParams
        val pw = (w - dp(24)).toInt()
        if (lp.width != pw) { lp.width = pw; pill.layoutParams = lp }
        val x = (2 - cur) * w + dp(12)
        if (anim) pill.animate().translationX(x).setDuration(420).setInterpolator(OvershootInterpolator(1.2f)).start()
        else pill.translationX = x
    }

    private fun refreshTabs() {
        for (i in 0..2) {
            val on = i == cur
            val s = if (on) 1.3f else 1f
            tabIcon[i]?.animate()?.scaleX(s)?.scaleY(s)?.setDuration(320)?.setInterpolator(OvershootInterpolator(2.5f))?.start()
            tabLabel[i]?.setTextColor(if (on) Color.WHITE else MUTED)
        }
    }

    private fun stagger(i: Int) {
        val l = lists[i]
        for (k in 0 until l.childCount) {
            val c = l.getChildAt(k)
            c.alpha = 0f
            c.translationY = dp(40).toFloat()
            c.animate().alpha(1f).translationY(0f).setStartDelay(70L * k).setDuration(480)
                .setInterpolator(OvershootInterpolator(1.05f)).start()
        }
    }

    private fun show(i: Int) {
        if (i == cur) return
        Sfx.play("nav")
        val old = pages[cur]
        val nw = pages[i]
        val w = content.width.toFloat()
        val dir = if (i > cur) -1f else 1f
        old.animate().alpha(0f).translationX(-dir * w * 0.25f).setDuration(220)
            .withEndAction { if (pages[cur] !== old) old.visibility = View.GONE }.start()
        nw.visibility = View.VISIBLE
        nw.alpha = 0f
        nw.translationX = dir * w * 0.25f
        nw.animate().alpha(1f).translationX(0f).setDuration(380).setInterpolator(DecelerateInterpolator(2f)).start()
        cur = i
        stagger(i)
        movePill(true)
        refreshTabs()
        bg.animate().translationX((1 - i) * dp(18).toFloat()).setDuration(600).start()
    }

    // ---------- دورة الحياة ----------
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        sp = getSharedPreferences("home", Context.MODE_PRIVATE)
        Sfx.enabled = sp.getBoolean("sound", true)
        window.statusBarColor = Color.parseColor("#070A1E")
        window.navigationBarColor = Color.parseColor("#0A0D20")
        try { Shizuku.addRequestPermissionResultListener(permL) } catch (e: Throwable) {}

        val root = FrameLayout(this)
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        bg = AnimeBg(this)
        bg.scaleX = 1.15f
        bg.scaleY = 1.15f
        root.addView(bg, FrameLayout.LayoutParams(-1, -1))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        content = FrameLayout(this)
        col.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        buildBar()
        col.addView(bar, LinearLayout.LayoutParams(-1, -2))
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        buildHome(page())
        buildPerf(page())
        buildGames(page())
        pages[1].visibility = View.GONE
        pages[2].visibility = View.GONE
        updateMaster()
        refreshTabs()
        stagger(0)

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 7)
        }
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(1)
        } catch (e: Throwable) {}
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        refresh()
        h.post(tick)
    }

    override fun onPause() {
        resumed = false
        h.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(permL) } catch (e: Throwable) {}
        super.onDestroy()
    }
}
    
        
