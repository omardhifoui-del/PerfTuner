package com.perf.tuner

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

object Profiles {
    private const val PREF = "profiles"
    private const val KEY = "list"

    fun add(ctx: Context, parent: LinearLayout, run: (String) -> Unit) {
        val dens = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * dens).toInt()
        fun box(c: Int, r: Int, s: Int = 0) = GradientDrawable().apply {
            setColor(c); cornerRadius = dp(r).toFloat(); if (s != 0) setStroke(dp(1), s)
        }
        val cyan = Color.parseColor("#00E5FF")
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        fun load() = try { JSONArray(sp.getString(KEY, "[]")) } catch (e: Exception) { JSONArray() }
        fun save(a: JSONArray) = sp.edit().putString(KEY, a.toString()).apply()

        fun command(o: JSONObject): String {
            val p = o.getString("pkg")
            val f = o.getInt("fps")
            val sb = StringBuilder()
            if (o.getBoolean("anim")) sb.append("for k in window_animation_scale transition_animation_scale animator_duration_scale; do settings put global \$k 0.5; done; ")
            if (o.getBoolean("kill")) sb.append("for a in \$(pm list packages -3 | cut -d: -f2); do [ \"\$a\" != com.perf.tuner ] && [ \"\$a\" != $p ] && am force-stop \$a; done; ")
            sb.append("cmd game set --mode performance --fps $f $p; ")
            if (o.getBoolean("launch")) sb.append("monkey -p $p -c android.intent.category.LAUNCHER 1")
            return sb.toString()
        }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = box(Color.parseColor("#1AFFFFFF"), 20, Color.parseColor("#33FFFFFF"))
            setPadding(dp(12), dp(14), dp(12), dp(10))
        }
        card.addView(TextView(ctx).apply {
            text = "🎮 ملفات الألعاب"; setTextColor(cyan); textSize = 17f
            setTypeface(null, Typeface.BOLD); setPadding(dp(4), 0, 0, dp(8))
        })

        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        fun refresh() {
            list.removeAllViews()
            val a = load()
            if (a.length() == 0) {
                list.addView(TextView(ctx).apply {
                    text = "لا توجد ملفات محفوظة بعد"; textSize = 13f
                    setTextColor(Color.parseColor("#99FFFFFF")); setPadding(dp(4), dp(4), dp(4), dp(8))
                })
                return
            }
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    background = box(Color.parseColor("#22FFFFFF"), 14)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                row.addView(TextView(ctx).apply {
                    text = o.getString("name") + "\n" + o.getInt("fps") + " FPS"
                    setTextColor(Color.WHITE); textSize = 14f
                }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(TextView(ctx).apply {
                    text = "▶ تشغيل"; setTextColor(Color.BLACK); textSize = 13f
                    setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    background = box(cyan, 12)
                    setOnClickListener { run(command(o)) }
                })
                row.addView(TextView(ctx).apply {
                    text = "🗑"; textSize = 18f; setPadding(dp(14), dp(4), dp(4), dp(4))
                    setOnClickListener {
                        val cur = load(); cur.remove(i); save(cur); refresh()
                    }
                })
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) })
            }
        }

        fun field(hint: String) = EditText(ctx).apply {
            this.hint = hint; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
            textSize = 14f; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT
            background = box(Color.parseColor("#22FFFFFF"), 12, Color.parseColor("#44FFFFFF"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val nameIn = field("اسم اللعبة (مثال: ببجي)")
        val pkgIn = field("اسم الحزمة (مثال: com.tencent.ig)")

        var fps = 60
        val values = listOf(60, 90, 120)
        val chips = values.map { v ->
            TextView(ctx).apply {
                text = "$v FPS"; gravity = Gravity.CENTER; textSize = 13f
                setTextColor(Color.WHITE); setPadding(dp(8), dp(10), dp(8), dp(10))
            }
        }
        fun paint() {
            chips.forEachIndexed { i, t ->
                val on = values[i] == fps
                t.background = box(
                    if (on) Color.parseColor("#6600E5FF") else Color.parseColor("#22FFFFFF"), 12,
                    if (on) cyan else Color.parseColor("#44FFFFFF")
                )
            }
        }
        val chipRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        chips.forEachIndexed { i, t ->
            t.setOnClickListener { fps = values[i]; paint() }
            chipRow.addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(6), dp(3), dp(6)) })
        }
        paint()

        fun check(label: String, on: Boolean) = CheckBox(ctx).apply {
            text = label; setTextColor(Color.WHITE); textSize = 13f; isChecked = on
        }
        val cKill = check("إغلاق التطبيقات الأخرى قبل التشغيل", true)
        val cAnim = check("تسريع الأنيميشن (0.5x)", true)
        val cLaunch = check("فتح اللعبة تلقائياً", true)

        val saveBtn = TextView(ctx).apply {
            text = "💾 حفظ الملف"; gravity = Gravity.CENTER; textSize = 15f
            setTextColor(Color.BLACK); setTypeface(null, Typeface.BOLD)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(cyan, Color.parseColor("#B388FF"))
            ).apply { cornerRadius = dp(14).toFloat() }
            setOnClickListener {
                val p = pkgIn.text.toString().trim()
                if (!Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(p)) {
                    Toast.makeText(ctx, "اسم الحزمة غير صحيح", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }
                val n = nameIn.text.toString().trim().ifEmpty { p }
                val o = JSONObject()
                    .put("name", n).put("pkg", p).put("fps", fps)
                    .put("kill", cKill.isChecked).put("anim", cAnim.isChecked).put("launch", cLaunch.isChecked)
                val a = load(); a.put(o); save(a)
                nameIn.setText(""); pkgIn.setText("")
                refresh()
                Toast.makeText(ctx, "تم الحفظ ✔", Toast.LENGTH_SHORT).show()
            }
        }

        card.addView(list)
        card.addView(TextView(ctx).apply {
            text = "➕ ملف جديد"; setTextColor(Color.WHITE); textSize = 14f
            setTypeface(null, Typeface.BOLD); setPadding(dp(4), dp(12), 0, dp(6))
        })
        card.addView(nameIn, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        card.addView(pkgIn, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        card.addView(chipRow)
        card.addView(cKill); card.addView(cAnim); card.addView(cLaunch)
        card.addView(saveBtn, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(3), dp(10), dp(3), dp(3)) })
        refresh()
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(14)) })
    }
}
