package com.perf.tuner

import android.content.Context
import rikka.shizuku.Shizuku

data class Tweak(
    val title: String, val desc: String,
    val on: List<String>, val off: List<String> = emptyList(),
    val oneShot: Boolean = false
)

val CATS = listOf(
    "🖥 الشاشة", "🧠 الذاكرة", "⚙ المعالج", "🎮 GPU", "👆 اللمس",
    "🌐 الشبكة", "🔋 الطاقة", "💾 التخزين", "🎯 الألعاب", "🛠 النظام"
)

private fun sg(k: String, v: String) = "settings put global $k $v"
private fun sy(k: String, v: String) = "settings put system $k $v"
private fun ss(k: String, v: String) = "settings put secure $k $v"
private val anim = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")

// الفئات 2..9 تُملأ في الدفعات التالية بنفس الصيغة
val TWEAKS: Map<Int, List<Tweak>> = mapOf(
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

    /** ينفذ الأوامر مع حفظ القيمة الأصلية والتحقق منها. يرجع true عند النجاح */
    fun apply(t: Tweak, id: String): Boolean {
        var ok = true
        for (c in t.on) {
            val m = rx.find(c)
            if (m != null) {
                val (ns, k, v) = m.destructured
                val key = "b_${ns}_$k"
                if (!sp.contains(key)) sp.edit().putString(key, Shell.run("settings get $ns $k").second).apply()
                ok = ok && Shell.run(c).first == 0 && Shell.run("settings get $ns $k").second == v
            } else ok = ok && Shell.run(c).first == 0
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
        for (c in t.off) ok = ok && Shell.run(c).first == 0
        if (ok) sp.edit().putBoolean("on_$id", false).apply()
        return ok
    }
}
