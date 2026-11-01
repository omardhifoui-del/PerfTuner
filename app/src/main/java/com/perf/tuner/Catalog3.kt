package com.perf.tuner

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView

object Catalog3 {
    private fun s(id: String, t: String, d: String, on: String, off: String, w: String? = null) = Sw("v3_$id", t, d, on, off, w)
    private const val SOC = "com.facebook.katana com.facebook.orca com.instagram.android com.zhiliaoapp.musically com.snapchat.android com.twitter.android"

    val sections: List<Sec> = listOf(
        Sec("🧰", "أدوات من النسخ السابقة", listOf(
            s("touches", "إظهار اللمسات", "نقاط على الشاشة عند اللمس", "settings put system show_touches 1", "settings put system show_touches 0"),
            s("ptrloc", "إظهار إحداثيات اللمس", "شريط بيانات اللمس أعلى الشاشة", "settings put system pointer_location 1", "settings put system pointer_location 0"),
            s("standby", "إيقاف تقييد التطبيقات (App Standby)", "تبقى التطبيقات نشطة في الخلفية", "settings put global app_standby_enabled 0", "settings put global app_standby_enabled 1", "قد تستهلك التطبيقات الخلفية بطارية أكثر. متابعة؟"),
            s("charge", "إيقاف أصوات الشحن", "هدوء وحمل أقل", "settings put global charging_sounds_enabled 0", "settings put global charging_sounds_enabled 1"),
            s("social", "تقييد تطبيقات التواصل بالخلفية", "فيسبوك وانستغرام وتيك توك وسناب وتويتر", "for p in $SOC; do cmd appops set \$p RUN_IN_BACKGROUND ignore; cmd appops set \$p RUN_ANY_IN_BACKGROUND ignore; done", "for p in $SOC; do cmd appops set \$p RUN_IN_BACKGROUND allow; cmd appops set \$p RUN_ANY_IN_BACKGROUND allow; done", "ستتأخر إشعارات هذه التطبيقات. متابعة؟"),
            Ac("🧨 إغلاق كل تطبيقات المستخدم", "for p in \$(pm list packages -3 | cut -d: -f2); do [ \"\$p\" != com.perf.tuner ] && am force-stop \$p; done"),
            Ac("🛠 صيانة الذاكرة", "cmd activity idle-maintenance"),
            Ac("📲 معلومات الجهاز", "getprop ro.product.model; getprop ro.board.platform; getprop ro.hardware; getprop ro.build.version.release; wm size; wm density"),
            Ac("📈 الأكثر استهلاكاً للمعالج", "dumpsys cpuinfo | head -15"),
            Ac("🔋 تفاصيل البطارية", "dumpsys battery | head -20"),
            Ac("🖥 معلومات الشاشة", "dumpsys display | grep -iE 'refreshRate|fps=' | head -8"),
            Ac("🔄 إعادة تشغيل واجهة النظام", "am restart", "سيعيد تشغيل النظام الأمامي لثوانٍ. متابعة؟"),
            Ac("📶 حالة الشبكة", "dumpsys connectivity | head -30"),
            Ac("💽 مساحة التخزين", "df -h /data")
        ))
    )

    val all: List<Sw> = sections.flatMap { it.items }.filterIsInstance<Sw>()
}

object Finale {
    fun install(a: Activity) {
        val content = a.findViewById<ViewGroup>(android.R.id.content)

        val burst = BurstView(a)
        content.addView(burst, ViewGroup.LayoutParams(-1, -1))
        find(content)?.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) burst.fire(e.rawX, e.rawY)
            false
        }

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

    private fun find(v: View): Switch? {
        if (v is Switch) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val r = find(v.getChildAt(i))
                if (r != null) return r
            }
        }
        return null
    }
}
