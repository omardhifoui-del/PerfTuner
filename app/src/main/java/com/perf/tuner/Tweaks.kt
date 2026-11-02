package com.perf.tuner

import android.content.Context
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku

data class Tweak(
    val title: String, val desc: String,
    val on: List<String>, val off: List<String> = emptyList(),
    val oneShot: Boolean = false,
    val ultra: Boolean = true,      // هل يدخل ضمن زر ULTRA
    val report: Boolean = false     // يعرض نتيجة أمر بدل تعديل شيء
) {
    val perPkg get() = on.any { "{pkg}" in it } || off.any { "{pkg}" in it }
}

val CATS = listOf(
    "🖥 الشاشة", "🧠 الذاكرة", "⚙ المعالج", "🎮 GPU", "👆 اللمس",
    "🌐 الشبكة", "🔋 الطاقة", "💾 التخزين", "🎯 الألعاب", "🛠 النظام"
)

internal fun sg(k: String, v: String) = "settings put global $k $v"
internal fun sy(k: String, v: String) = "settings put system $k $v"
internal fun ss(k: String, v: String) = "settings put secure $k $v"
private val anim = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")

// الفئات 2..9 تُملأ في الدفعات التالية بنفس الصيغة
private val BASE: Map<Int, List<Tweak>> = mapOf(
    0 to listOf(
        Tweak("أنيميشن 0.5x", "انتقالات أسرع", anim.map { sg(it, "0.5") }),
        Tweak("إيقاف الأنيميشن", "أقصى سرعة بصرية", anim.map { sg(it, "0") }),
        Tweak("تثبيت 120Hz", "يعمل إن كانت الشاشة تدعمه", listOf(sy("min_refresh_rate", "120"), sy("peak_refresh_rate", "120"))),
        Tweak("تثبيت 90Hz", "توازن بين السلاسة والبطارية", listOf(sy("min_refresh_rate", "90"), sy("peak_refresh_rate", "90"))),
        Tweak("حجم الخط 0.85", "يتسع المحتوى أكثر", listOf(sy("font_scale", "0.85"))),
        Tweak("إطفاء الشاشة 15 ثانية", "يوفر الطاقة", listOf(sy("screen_off_timeout", "15000"))),
        Tweak("الشاشة مضاءة أثناء الشحن", "مفيد للألعاب", listOf(sg("stay_on_while_plugged_in", "3"))),
        Tweak("إظهار اللمسات", "نقاط عند اللمس", listOf(sy("show_touches", "1"))),
        Tweak("إحداثيات اللمس", "شريط علوي للمس", listOf(sy("pointer_location", "1"))),
        Tweak("إلغاء ضبابية النوافذ", "يخفف حمل الرسوميات (أندرويد 12+)", listOf(sg("disable_window_blurs", "1")))
    ),
    1 to listOf(
        Tweak("رفع حد العمليات المخبأة", "يبقي تطبيقات أكثر في الذاكرة",
            listOf("cmd device_config put activity_manager max_cached_processes 32"),
            listOf("cmd device_config delete activity_manager max_cached_processes")),
        Tweak("درع العمليات الوهمية", "يمنع قتل عمليات الألعاب الفرعية (12+)",
            listOf("cmd device_config put activity_manager max_phantom_processes 2147483647",
                "settings put global settings_enable_monitor_phantom_procs false"),
            listOf("cmd device_config delete activity_manager max_phantom_processes",
                "settings put global settings_enable_monitor_phantom_procs true")),
        Tweak("إيقاف Doze", "التطبيقات تعمل بالخلفية (تستهلك بطارية)",
            listOf("dumpsys deviceidle disable"), listOf("dumpsys deviceidle enable")),
        Tweak("إيقاف البطارية التكيفية", "يمنع تقييد التطبيقات", listOf(sg("adaptive_battery_management_enabled", "0"))),
        Tweak("وضع الأداء الثابت", "قد يرفع الحرارة؛ الحماية الحرارية تبقى فعالة",
            listOf("cmd power set-fixed-performance-mode-enabled true"),
            listOf("cmd power set-fixed-performance-mode-enabled false")),
        Tweak("مسح الكاش", "تنفيذ لمرة واحدة", listOf("pm trim-caches 999G"), oneShot = true),
        Tweak("تحسين التطبيقات الآن", "bg-dexopt؛ يأخذ وقتاً", listOf("cmd package bg-dexopt-job"), oneShot = true),
        Tweak("FSTRIM", "ينظف قطاعات التخزين", listOf("sm fstrim"), oneShot = true),
        Tweak("صيانة الخمول", "يشغّل مهام الصيانة", listOf("cmd activity idle-maintenance"), oneShot = true),
        Tweak("تقليل سجلات النظام", "مخزن logcat أصغر",
            listOf("logcat -G 64K"), listOf("logcat -G 256K"))
    )
)

val TWEAKS: Map<Int, List<Tweak>> = (0..9).associateWith { BASE[it].orEmpty() + MORE[it].orEmpty() + MORE2[it].orEmpty() + MORE3[it].orEmpty() }

object Shell {
    fun run(cmd: String): Pair<Int, String> = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
        val p = m.invoke(null, arrayOf("sh", "-c", "($cmd) 2>&1"), null, null) as Process
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor() to out
    } catch (e: Throwable) { -1 to (e.message ?: "error") }
}

class Engine(ctx: Context) {
    private val sp = ctx.getSharedPreferences("perf", 0)
    private val rx = Regex("""settings put (\w+) (\S+) (\S+)""")

    fun isOn(id: String) = sp.getBoolean("on_$id", false)
    fun pkgs(): List<String> = (sp.getString("pkgs", "") ?: "")
        .split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    fun setPkgs(s: String) { sp.edit().putString("pkgs", s).apply() }
    private fun expand(c: String): List<String> =
        if ("{pkg}" in c) pkgs().map { c.replace("{pkg}", it) } else listOf(c)
    fun report(cmd: String): String =
        expand(cmd).joinToString("\n\n") { Shell.run(it).second }.ifBlank { "(لا توجد نتائج)" }
    fun runCmd(c0: String): Boolean {
        var ok = true
        for (c in expand(c0)) { val r = Shell.run(c).first == 0; ok = ok && r }
        return ok
    }
    fun choiceSel(title: String): String = sp.getString("ch_$title", "") ?: ""
    fun setChoiceSel(title: String, label: String) { sp.edit().putString("ch_$title", label).apply() }
    fun dpiDeadline(): Long = sp.getLong("dpi_dl", 0L)
    fun setDpiDeadline(v: Long) { sp.edit().putLong("dpi_dl", v).apply() }

    /** ينفذ كل الأوامر (حتى لو فشل بعضها) مع حفظ القيم الأصلية. true = نجح الجميع */
    fun apply(t: Tweak, id: String): Boolean {
        var ok = true
        for (c0 in t.on) for (c in expand(c0)) {
            val m = rx.find(c)
            if (m != null) {
                val (ns, k, v) = m.destructured
                val key = "b_${ns}_$k"
                if (!sp.contains(key)) sp.edit().putString(key, Shell.run("settings get $ns $k").second).apply()
                val r1 = Shell.run(c).first == 0
                val r2 = Shell.run("settings get $ns $k").second == v
                ok = ok && r1 && r2
            } else {
                val r = Shell.run(c).first == 0
                ok = ok && r
            }
        }
        if (ok && !t.oneShot) sp.edit().putBoolean("on_$id", true).apply()
        return ok
    }

    fun revert(t: Tweak, id: String): Boolean {
        var ok = true
        for (c in t.on) rx.find(c)?.let {
            val (ns, k, _) = it.destructured
            val orig = sp.getString("b_${ns}_$k", null) ?: return@let
            val r = if (orig == "null") Shell.run("settings delete $ns $k") else Shell.run("settings put $ns $k $orig")
            ok = ok && r.first == 0
            sp.edit().remove("b_${ns}_$k").apply()
        }
        for (c0 in t.off) for (c in expand(c0)) {
            val r = Shell.run(c).first == 0
            ok = ok && r
        }
        if (ok) sp.edit().putBoolean("on_$id", false).apply()
        return ok
    }
}

private val GAME_WATCH = setOf(
    "عدم الإزعاج (أولوية)", "إيقاف إشعارات الأعلى", "Game Mode: أداء", "أولوية اللعبة",
    "قفل تدوير الشاشة", "إيقاف اهتزاز اللمس", "تثبيت 120Hz"
)

/** يراقب التطبيق المفتوح كل 4 ثوانٍ؛ عند فتح لعبة من قائمتك يطبّق ملف الألعاب وعند الخروج يستعيد */
object GameWatcher {
    @Volatile var running = false
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ours = mutableListOf<Pair<Tweak, String>>()
    private val fg = Regex("""u\d+ ([\w.]+)/""")

    private fun targets() = TWEAKS.flatMap { (p, l) ->
        l.mapIndexedNotNull { i, t -> if (t.title in GAME_WATCH) t to "$p-$i" else null }
    }

    private fun enter(e: Engine) {
        ours.clear()
        for ((t, id) in targets()) if (!e.isOn(id) && e.apply(t, id)) ours.add(t to id)
    }

    private fun leave(e: Engine) {
        for ((t, id) in ours) e.revert(t, id)
        ours.clear()
    }

    fun start(e: Engine) {
        if (running) return
        running = true
        job = scope.launch {
            var inGame = false
            while (isActive) {
                val top = Shell.run("dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'").second
                val pkg = fg.find(top)?.groupValues?.get(1)
                val now = pkg != null && pkg in e.pkgs()
                if (now && !inGame) { enter(e); inGame = true }
                else if (!now && inGame) { leave(e); inGame = false }
                delay(4000)
            }
        }
    }

    fun stop(e: Engine) {
        running = false
        job?.cancel()
        scope.launch { leave(e) }
    }
}
