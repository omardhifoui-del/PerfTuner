package com.perf.tuner

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.widget.*

object Extras {
    private const val RES = "s=\$(wm size | grep -o '[0-9]*x[0-9]*' | head -1); w=\${s%x*}; h=\${s#*x}; wm size \$((w*%P%/100))x\$((h*%P%/100))"
    private const val DNS = "settings put global private_dns_mode hostname; settings put global private_dns_specifier "

    fun add(ctx: Context, parent: LinearLayout, run: (String) -> Unit) {
        val dens = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * dens).toInt()
        fun box(c: Int, r: Int, s: Int = 0) = GradientDrawable().apply {
            setColor(c); cornerRadius = dp(r).toFloat(); if (s != 0) setStroke(dp(1), s)
        }
        val pink = Color.parseColor("#FF4081")

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = box(Color.parseColor("#1AFFFFFF"), 20, Color.parseColor("#33FFFFFF"))
            setPadding(dp(12), dp(14), dp(12), dp(10))
        }
        card.addView(TextView(ctx).apply {
            text = "⚡ ميزات متقدمة"; setTextColor(pink); textSize = 17f
            setTypeface(null, Typeface.BOLD); setPadding(dp(4), 0, 0, dp(4))
        })

        fun head(t: String) = card.addView(TextView(ctx).apply {
            text = t; setTextColor(Color.WHITE); textSize = 14f
            setTypeface(null, Typeface.BOLD); setPadding(dp(4), dp(12), 0, dp(4))
        })

        fun btn(label: String, cmd: () -> String?): TextView = TextView(ctx).apply {
            text = label; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setPadding(dp(8), dp(12), dp(8), dp(12))
            background = box(Color.parseColor("#33FF4081"), 14, Color.parseColor("#66FF4081"))
            setOnClickListener {
                val c = cmd()
                if (c == null) Toast.makeText(ctx, "اسم الحزمة غير صحيح", Toast.LENGTH_SHORT).show()
                else run(c)
            }
        }

        fun grid(vararg items: Pair<String, String>) {
            items.toList().chunked(2).forEach { ch ->
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                ch.forEach { (l, c) ->
                    row.addView(btn(l) { c }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
                }
                if (ch.size == 1) row.addView(android.view.View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
                card.addView(row)
            }
        }

        head("📐 دقة الشاشة (ترفع FPS كثيراً)")
        grid(
            "دقة 90%" to RES.replace("%P%", "90"),
            "دقة 80%" to RES.replace("%P%", "80"),
            "دقة 70%" to RES.replace("%P%", "70"),
            "♻️ الدقة الأصلية" to "wm size reset; wm density reset"
        )

        head("🔕 التركيز واللعب")
        grid(
            "عدم الإزعاج ON" to "cmd notification set_dnd priority",
            "عدم الإزعاج OFF" to "cmd notification set_dnd off",
            "الشاشة مضاءة أثناء الشحن" to "settings put global stay_on_while_plugged_in 3",
            "إلغاء بقاء الشاشة" to "settings put global stay_on_while_plugged_in 0"
        )

        head("🌐 تقليل البنق (DNS خاص)")
        grid(
            "Google DNS" to (DNS + "dns.google"),
            "Cloudflare DNS" to (DNS + "one.one.one.one"),
            "DNS تلقائي" to "settings put global private_dns_mode opportunistic"
        )

        head("🔎 تحليل")
        grid(
            "أكثر التطبيقات استهلاكاً للذاكرة" to "dumpsys meminfo | head -28",
            "أكثر التطبيقات استهلاكاً للمعالج" to "dumpsys cpuinfo | head -15",
            "تفاصيل البطارية" to "dumpsys battery | head -20"
        )

        head("🧊 تجميد تطبيق (يوقفه تماماً)")
        val pkgIn = EditText(ctx).apply {
            hint = "اسم حزمة تطبيق تثبيته أنت"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
            textSize = 14f; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT
            background = box(Color.parseColor("#22FFFFFF"), 12, Color.parseColor("#44FFFFFF"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        card.addView(pkgIn, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        val ok = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
        fun pkgCmd(f: (String) -> String): () -> String? = {
            val p = pkgIn.text.toString().trim()
            if (ok.matches(p)) f(p) else null
        }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(btn("🧊 تجميد", pkgCmd { "pm disable-user --user 0 $it" }),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        row.addView(btn("🔥 إلغاء التجميد", pkgCmd { "pm enable $it" }),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        card.addView(row)

        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(14)) })
    }
}
