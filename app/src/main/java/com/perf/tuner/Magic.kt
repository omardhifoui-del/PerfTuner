package com.perf.tuner

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class BurstView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cols = intArrayOf(
        Color.parseColor("#00E5FF"), Color.parseColor("#B388FF"),
        Color.parseColor("#FF4081"), Color.parseColor("#FFD54F")
    )
    private var cx = 0f
    private var cy = 0f
    private var start = 0L
    private var on = false

    fun fire(x: Float, y: Float) {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        cx = x - loc[0]
        cy = y - loc[1]
        start = System.currentTimeMillis()
        on = true
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (!on) return
        val k = (System.currentTimeMillis() - start) / 800f
        if (k >= 1f) { on = false; return }
        val al = ((1f - k) * 255).toInt()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f * (1f - k)
        p.color = Color.argb(al, 0, 229, 255)
        c.drawCircle(cx, cy, 40f + 380f * k, p)
        p.color = Color.argb(al, 179, 136, 255)
        c.drawCircle(cx, cy, 20f + 250f * k, p)
        p.style = Paint.Style.FILL
        for (i in 0 until 24) {
            val ang = i * (2 * PI / 24)
            val r = 30f + (140f + (i % 4) * 50f) * k
            p.color = (cols[i % 4] and 0xFFFFFF) or (al shl 24)
            c.drawCircle(
                cx + (r * cos(ang)).toFloat(),
                cy + (r * sin(ang)).toFloat(),
                7f * (1f - k) + 1f, p
            )
        }
        postInvalidateOnAnimation()
    }
}

object Magic {
    private val CY = Color.parseColor("#00E5FF")
    private val PU = Color.parseColor("#B388FF")
    private val PK = Color.parseColor("#FF4081")

    fun install(a: Activity, root: LinearLayout) {
        val d = a.resources.displayMetrics.density
        val content = a.findViewById<ViewGroup>(android.R.id.content)

        // خلفية بعمق أثناء التمرير
        val bg = content.getChildAt(0)
        if (bg is FxView) {
            bg.scaleX = 1.25f
            bg.scaleY = 1.5f
            (content.getChildAt(1) as? ScrollView)?.setOnScrollChangeListener { _, _, y, _, _ ->
                bg.translationY = -minOf(y * 0.08f, bg.height * 0.2f)
            }
        }

        // عنوان متلألئ
        (root.getChildAt(0) as? TextView)?.let { t ->
            val w = 320f * d
            val sh = LinearGradient(0f, 0f, w, 0f, intArrayOf(CY, PU, PK, CY), null, Shader.TileMode.REPEAT)
            t.paint.shader = sh
            ValueAnimator.ofFloat(0f, w).apply {
                duration = 3000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    sh.setLocalMatrix(Matrix().apply { setTranslate(it.animatedValue as Float, 0f) })
                    t.invalidate()
                }
                start()
            }
        }

        // نبض سطر الحالة
        (root.getChildAt(1) as? TextView)?.let { s ->
            ObjectAnimator.ofFloat(s, "alpha", 1f, 0.45f).apply {
                duration = 1200
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                start()
            }
        }

        // انفجار شرارات على زر التعزيز
        val burst = BurstView(a)
        content.addView(burst, ViewGroup.LayoutParams(-1, -1))
        for (i in 0 until root.childCount) {
            val v = root.getChildAt(i)
            if (v is TextView && v.text.toString().contains("تعزيز شامل")) {
                v.setOnTouchListener { _, e ->
                    if (e.action == MotionEvent.ACTION_UP) {
                        burst.fire(e.rawX, e.rawY)
                        try {
                            val tg = ToneGenerator(AudioManager.STREAM_SYSTEM, 70)
                            tg.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                            Handler(Looper.getMainLooper()).postDelayed({ tg.release() }, 500)
                        } catch (e2: Exception) {}
                    }
                    false
                }
            }
        }

        // شاشة افتتاح
        val splash = TextView(a).apply {
            text = "⚡\nPERF TUNER"
            gravity = Gravity.CENTER
            textSize = 34f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#05060D"))
        }
        content.addView(splash, ViewGroup.LayoutParams(-1, -1))
        splash.animate().alpha(0f).setStartDelay(900).setDuration(700)
            .withEndAction { content.removeView(splash) }.start()
    }
}
