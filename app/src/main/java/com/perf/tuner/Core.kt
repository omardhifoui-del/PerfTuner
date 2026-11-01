package com.perf.tuner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.view.View
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object Sfx {
    var enabled = true
    private const val SR = 22050
    private val cache = HashMap<String, ShortArray>()

    private fun note(f0: Double, f1: Double, ms: Int, vol: Double): ShortArray {
        val n = SR * ms / 1000
        val out = ShortArray(n)
        var ph = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            ph += 2 * PI * (f0 + (f1 - f0) * t) / SR
            val att = minOf(1.0, i / (SR * 0.006))
            val s = (sin(ph) + 0.25 * sin(2 * ph)) * att * exp(-4.0 * t) * vol
            out[i] = (s * 32767).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun build(name: String): ShortArray = when (name) {
        "tap" -> note(950.0, 760.0, 55, 0.30)
        "on" -> note(520.0, 880.0, 110, 0.35)
        "off" -> note(820.0, 430.0, 110, 0.30)
        "nav" -> note(640.0, 840.0, 80, 0.32)
        "boost" -> note(523.0, 523.0, 90, 0.38) + note(659.0, 659.0, 90, 0.38) + note(784.0, 784.0, 90, 0.38) + note(1047.0, 1200.0, 260, 0.42)
        else -> note(700.0, 700.0, 50, 0.20)
    }

    fun play(name: String) {
        if (!enabled) return
        Thread {
            try {
                val buf = synchronized(cache) { cache.getOrPut(name) { build(name) } }
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SR)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                    )
                    .setBufferSizeInBytes(buf.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                t.write(buf, 0, buf.size)
                t.play()
                Thread.sleep(buf.size * 1000L / SR + 80)
                t.release()
            } catch (e: Throwable) {
            }
        }.start()
    }
}

class AnimeBg(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rnd = java.util.Random(7)
    private val sx = FloatArray(70) { rnd.nextFloat() }
    private val sy = FloatArray(70) { rnd.nextFloat() * 0.6f }
    private val ss = FloatArray(70) { 0.8f + rnd.nextFloat() * 1.8f }
    private val px = FloatArray(22) { rnd.nextFloat() }
    private val py = FloatArray(22) { rnd.nextFloat() }
    private val pv = FloatArray(22) { 0.03f + rnd.nextFloat() * 0.04f }
    private val pr = FloatArray(22) { rnd.nextFloat() * 360f }
    private val far = FloatArray(14) { 0.14f + rnd.nextFloat() * 0.2f }
    private val near = FloatArray(10) { 0.07f + rnd.nextFloat() * 0.13f }
    private var sky: Shader? = null
    private var moonG: Shader? = null
    private var t = 0f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        sky = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.parseColor("#070A1E"), Color.parseColor("#1E1456"), Color.parseColor("#7A2A7E"), Color.parseColor("#E5648F")),
            floatArrayOf(0f, 0.45f, 0.78f, 1f), Shader.TileMode.CLAMP
        )
        moonG = RadialGradient(
            w * 0.74f, h * 0.2f, w * 0.34f,
            intArrayOf(Color.parseColor("#FFF6E0"), Color.parseColor("#66FFD9A8"), Color.TRANSPARENT),
            floatArrayOf(0.25f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
    }

    private fun city(c: Canvas, w: Float, h: Float, hs: FloatArray, col: Int, lit: Boolean) {
        val base = h * 0.9f
        p.shader = null
        p.color = col
        c.drawRect(0f, base - 2f, w, h, p)
        val bw = w / hs.size
        for (i in hs.indices) {
            val top = base - h * hs[i]
            p.color = col
            c.drawRect(i * bw, top, (i + 1) * bw - 3f, base, p)
            if (lit) {
                var y = top + 16f
                while (y < base - 20f) {
                    if (sin(t * 0.7f + i * 3.1f + y * 0.07f) > 0.35f) {
                        p.color = Color.argb(210, 255, 214, 130)
                        c.drawRect(i * bw + 7f, y, i * bw + 14f, y + 9f, p)
                    }
                    y += 30f
                }
            }
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.shader = sky
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        for (i in sx.indices) {
            val a = (120 + 110 * sin(t * 1.4f + i * 1.7f)).toInt().coerceIn(30, 255)
            p.color = Color.argb(a, 255, 255, 255)
            c.drawCircle(sx[i] * w, sy[i] * h, ss[i], p)
        }
        p.shader = moonG
        c.drawCircle(w * 0.74f, h * 0.2f, w * 0.34f, p)
        p.shader = null
        p.color = Color.parseColor("#FFF8E7")
        c.drawCircle(w * 0.74f, h * 0.2f, w * 0.085f, p)
        val ph = (t * 0.04f) % 1f
        if (ph < 0.1f) {
            val k = ph / 0.1f
            val x = w * (0.1f + 0.5f * k)
            val y = h * (0.05f + 0.18f * k)
            p.color = Color.argb((220 * (1f - k)).toInt(), 255, 255, 255)
            p.strokeWidth = 3f
            c.drawLine(x, y, x - 100f, y - 40f, p)
        }
        city(c, w, h, far, Color.parseColor("#3A2270"), false)
        city(c, w, h, near, Color.parseColor("#120C2E"), true)
        p.color = Color.argb(200, 255, 183, 206)
        for (i in px.indices) {
            val y = ((py[i] + t * pv[i]) % 1f) * h
            val x = (((px[i] - t * pv[i] * 0.5f + 0.04f * sin(t + i)) % 1f + 1f) % 1f) * w
            c.save()
            c.translate(x, y)
            c.rotate((pr[i] + t * 40f) % 360f)
            c.drawOval(-7f, -4f, 7f, 4f, p)
            c.restore()
        }
        p.color = Color.argb(70, 0, 0, 0)
        c.drawRect(0f, 0f, w, h, p)
        t += 0.05f
        postInvalidateDelayed(40)
    }
}

class Sw(val id: String, val title: String, val desc: String, val on: String, val off: String, val warn: String? = null)
class Sl(val id: String, val title: String, val desc: String, val lo: Int, val hi: Int, val step: Int, val def: Int, val fmt: (Int) -> String, val cmd: (Int) -> String)
class Ch(val id: String, val title: String, val opts: List<Pair<String, String>>, val def: Int = 0)
class Ac(val title: String, val cmd: String, val warn: String? = null)
class Tip(val text: String)
class Sec(val icon: String, val title: String, val items: List<Any>)

object Cat {
    private const val AN = "for k in window_animation_scale transition_animation_scale animator_duration_scale; do settings put global \$k "
    fun anim(v: String) = AN + v + "; done"
    private fun res(p: Int) = "s=\$(wm size | grep -o '[0-9]*x[0-9]*' | head -1); w=\${s%x*}; h=\${s#*x}; wm size \$((w*$p/100))x\$((h*$p/100))"
    private fun hz(n: Int) = "settings put system min_refresh_rate $n; settings put system peak_refresh_rate $n; settings put system user_refresh_rate $n"
    private const val HZ0 = "settings delete system min_refresh_rate; settings delete system peak_refresh_rate; settings delete system user_refresh_rate"

    const val GAME = "cmd notification set_dnd priority; for k in window_animation_scale transition_animation_scale animator_duration_scale; do settings put global \$k 0.5; done"
    const val FREE = "am kill-all; for p in \$(pm list packages -3 | cut -d: -f2); do am send-trim-memory \$p COMPLETE 2>/dev/null; done; pm trim-caches 999G"

    val all: List<Sw> = listOf(
        Sw("sustained", "الأداء المستقر", "ثبات أعلى للمعالج والرسوميات في الجلسات الطويلة", "cmd power set-fixed-performance-mode-enabled true", "cmd power set-fixed-performance-mode-enabled false"),
        Sw("nosave", "إيقاف توفير الطاقة التكيفي", "يمنع تقليل الأداء لتوفير البطارية", "settings put global low_power 0; cmd power set-adaptive-power-saver-enabled false", "cmd power set-adaptive-power-saver-enabled true"),
        Sw("phantom", "درع العمليات الوهمية", "يمنع أندرويد من قتل عمليات الألعاب الفرعية", "device_config set_sync_disabled_for_tests persistent; device_config put activity_manager max_phantom_processes 2147483647; settings put global settings_enable_monitor_phantom_procs false", "device_config delete activity_manager max_phantom_processes; settings put global settings_enable_monitor_phantom_procs true; device_config set_sync_disabled_for_tests none"),
        Sw("freezer", "تجميد التطبيقات المخبأة", "يوقف نشاطها فتبقى حيّة وتوفّر المعالج", "settings put global cached_apps_freezer enabled", "settings delete global cached_apps_freezer"),
        Sw("compaction", "ضغط الذاكرة", "يضغط الصفحات الخاملة ليتسع لتطبيقات أكثر", "device_config put activity_manager use_compaction true", "device_config delete activity_manager use_compaction"),
        Sw("quicktouch", "استجابة اللمس السريعة", "يقلل تأخير اللمس والضغط", "settings put system pointer_speed 7; settings put secure long_press_timeout 250; settings put secure multi_press_timeout 200", "settings put system pointer_speed 0; settings put secure long_press_timeout 400; settings put secure multi_press_timeout 300"),
        Sw("wifi_ll", "قفل Wi-Fi منخفض التأخير", "يقلل تقطع الواي فاي أثناء الألعاب", "cmd wifi force-low-latency-mode enabled", "cmd wifi force-low-latency-mode disabled"),
        Sw("blur", "إلغاء ضبابية النوافذ", "يخفف حمل الرسوميات", "settings put global disable_window_blurs 1", "settings delete global disable_window_blurs"),
        Sw("cleangfx", "تنظيف الرسوميات", "يزيل MSAA الإجباري وطبقات التصحيح وتراكبات المطور", "setprop debug.egl.force_msaa false; settings delete global gpu_debug_app; settings delete global gpu_debug_layers; settings delete global gpu_debug_layers_gles; setprop debug.hwui.overdraw false; setprop debug.hwui.profile false; settings put system pointer_location 0; settings put system show_touches 0", "setprop debug.egl.force_msaa ''"),
        Sw("tcp", "تحسين اتصال TCP", "يحسّن بدء الاتصالات", "settings put global tcp_default_init_rwnd 60", "settings delete global tcp_default_init_rwnd"),
        Sw("scan", "إيقاف المسح الخلفي للشبكات", "يوفر المعالج والبطارية", "settings put global wifi_scan_always_enabled 0; settings put global ble_scan_always_enabled 0", "settings put global wifi_scan_always_enabled 1; settings put global ble_scan_always_enabled 1"),
        Sw("gms", "تحسين خدمات Google", "يقلل نشاطها في الخلفية", "cmd appops set com.google.android.gms RUN_IN_BACKGROUND ignore; am set-standby-bucket com.google.android.gms rare", "cmd appops set com.google.android.gms RUN_IN_BACKGROUND allow; am set-standby-bucket com.google.android.gms working_set", "قد تتأخر بعض الإشعارات عند تقييد خدمات Google. متابعة؟"),
        Sw("sleep", "منع النوم العميق (Doze)", "يُبقي التطبيقات تعمل بالخلفية", "dumpsys deviceidle disable all", "dumpsys deviceidle enable all", "منع Doze يستهلك البطارية أسرع. متابعة؟"),
        Sw("dnd", "عدم الإزعاج للألعاب", "يمنع الإشعارات المنبثقة أثناء اللعب", "cmd notification set_dnd priority", "cmd notification set_dnd off"),
        Sw("hzshow", "عرض معدل التحديث", "يظهر رقم الإطارات على الشاشة", "service call SurfaceFlinger 1034 i32 1", "service call SurfaceFlinger 1034 i32 0"),
        Sw("brlock", "تثبيت سطوع الشاشة", "إيقاف السطوع التلقائي", "settings put system screen_brightness_mode 0", "settings put system screen_brightness_mode 1"),
        Sw("haptic", "إيقاف الاهتزاز والأصوات", "يقلل المقاطعات أثناء اللمس", "settings put system haptic_feedback_enabled 0; settings put system sound_effects_enabled 0", "settings put system haptic_feedback_enabled 1; settings put system sound_effects_enabled 1"),
        Sw("logs", "تقليل سجلات النظام", "يخفف الكتابة والمعالجة الخلفية", "logcat -G 64K", "logcat -G 256K"),
        Sw("wifi_hp", "Wi-Fi عالي الأداء", "يرفع أداء الاتصال على حساب البطارية", "cmd wifi force-hi-perf-mode enabled", "cmd wifi force-hi-perf-mode disabled")
    )

    val master = listOf("sustained", "nosave", "phantom", "freezer", "compaction", "quicktouch", "wifi_ll", "blur", "cleangfx", "tcp", "scan")

    fun sw(id: String): Sw = all.first { it.id == id }

    val sections: List<Sec> = listOf(
        Sec("🖥", "الشاشة والإطارات", listOf(
            Ch("hz", "معدل التحديث (Hz)", listOf("تلقائي" to HZ0, "60" to hz(60), "90" to hz(90), "120" to hz(120), "144" to hz(144))),
            Ch("anim", "سرعة الأنيميشن", listOf("1x" to anim("1.0"), "0.5x" to anim("0.5"), "بدون" to anim("0"))),
            Ch("res", "دقة الشاشة (تخفف الحمل)", listOf("أصلية" to "wm size reset", "90%" to res(90), "80%" to res(80), "70%" to res(70))),
            sw("blur"), sw("hzshow"), sw("brlock")
        )),
        Sec("🎨", "الرسوميات GPU", listOf(
            Ch("render", "محرك الرسوميات", listOf("افتراضي" to "setprop debug.hwui.renderer ''", "OpenGL" to "setprop debug.hwui.renderer opengl", "SkiaGL" to "setprop debug.hwui.renderer skiagl", "Vulkan" to "setprop debug.hwui.renderer skiavk")),
            Ch("comp", "تركيب الشاشة", listOf("هاردوير" to "service call SurfaceFlinger 1008 i32 0", "GPU" to "service call SurfaceFlinger 1008 i32 1")),
            sw("cleangfx")
        )),
        Sec("🔥", "المعالج والطاقة", listOf(
            sw("sustained"), sw("nosave"), sw("dnd"), sw("gms"),
            Ac("⚙️ تحسين سريع للتطبيقات", "cmd package bg-dexopt-job"),
            Ac("⏳ تجميع كل التطبيقات (بطيء)", "cmd package compile -m speed-profile -a"),
            Tip("🛡 حماية الحرارة تبقى مفعّلة دائماً لسلامة هاتفك، ولذلك لا يوجد خيار لتجاوزها.")
        )),
        Sec("🧠", "إبقاء التطبيقات في الذاكرة", listOf(
            sw("phantom"), sw("freezer"), sw("compaction"), sw("sleep"),
            Ch("cached", "عدد التطبيقات المخبأة", listOf("افتراضي" to "device_config delete activity_manager max_cached_processes", "16" to "device_config put activity_manager max_cached_processes 16", "32" to "device_config put activity_manager max_cached_processes 32", "64" to "device_config put activity_manager max_cached_processes 64")),
            Ac("✂️ تقليم ذاكرة الخلفية", FREE),
            Ac("📋 التطبيقات العاملة", "dumpsys activity processes | grep 'Proc #' | head -25"),
            Tip("💡 على Xiaomi: اقفل التطبيق في قائمة التطبيقات الأخيرة بزر القفل، وفعّل له التشغيل التلقائي، واجعل توفير البطارية له «بلا قيود». وهاتف 4GB يتسع لعدد محدود من التطبيقات مهما فعلت.")
        )),
        Sec("🚀", "تقليل اللاج والتهنيج", listOf(
            Ac("🧹 تنظيف شامل", "am kill-all; pm trim-caches 999G; cmd activity idle-maintenance; sm idle-maint run; cmd package bg-dexopt-job"),
            Ac("🗑 مسح الكاش", "pm trim-caches 999G"),
            Ac("💾 صيانة التخزين", "sm idle-maint run"),
            sw("haptic"), sw("scan"), sw("logs"),
            Tip("💡 اترك 15% على الأقل من التخزين فارغاً، فامتلاؤه يسبب تهنيجاً كبيراً.")
        )),
        Sec("🌐", "الشبكة", listOf(
            sw("wifi_ll"), sw("wifi_hp"), sw("tcp"),
            Ch("dns", "DNS الخاص", listOf("تلقائي" to "settings put global private_dns_mode opportunistic", "Google" to "settings put global private_dns_mode hostname; settings put global private_dns_specifier dns.google", "Cloudflare" to "settings put global private_dns_mode hostname; settings put global private_dns_specifier one.one.one.one"))
        )),
        Sec("👆", "اللمس", listOf(
            sw("quicktouch"),
            Sl("pointer", "سرعة المؤشر", "سرعة تتبع اللمس (0 إلى 7)", 0, 7, 1, 0, { "$it" }, { "settings put system pointer_speed $it" }),
            Sl("long", "مهلة الضغطة الطويلة", "كلما قلّت زادت سرعة الاستجابة", 200, 1000, 50, 400, { "${it}ms" }, { "settings put secure long_press_timeout $it" }),
            Sl("multi", "مهلة اللمس المتعدد", "نافذة استجابة الضغط المتكرر", 100, 800, 50, 300, { "${it}ms" }, { "settings put secure multi_press_timeout $it" })
        ))
    )

    fun restoreCmd(): String {
        val sb = StringBuilder()
        for (s in all) sb.append(s.off).append("; ")
        for (s in Catalog2.all) sb.append(s.off).append("; ")
        sb.append("wm size reset; device_config delete activity_manager max_cached_processes; settings put global private_dns_mode opportunistic; ")
        sb.append(HZ0).append("; ")
        sb.append(MainActivity.RESET)
        return sb.toString()
    }
}
