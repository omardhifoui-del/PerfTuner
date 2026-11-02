package com.perf.tuner

private fun N(
    title: String, desc: String, on: List<String>, off: List<String> = emptyList(),
    oneShot: Boolean = false, report: Boolean = false, ultra: Boolean = false
) = Tweak(title, desc, on, off, oneShot, ultra, report)

/** مجموعة خيارات: كل خيار أمر، و«الافتراضي» يستخدم أمر reset */
data class Choice(val title: String, val desc: String, val opts: List<Pair<String, String>>, val reset: String)

private fun scale(p: Int) =
    "S=\$(wm size | grep -o '[0-9]*x[0-9]*' | head -n1); W=\${S%x*}; H=\${S#*x}; wm size \$((W*$p/100))x\$((H*$p/100))"

private val SOCIAL = "com.facebook.katana com.instagram.android com.zhiliaoapp.musically com.google.android.youtube com.snapchat.android com.twitter.android"

val MORE3: Map<Int, List<Tweak>> = mapOf(
    0 to listOf(
        N("Hz تكيفي 60↔120", "النظام يرفع التردد عند اللمس ويخفضه عند السكون",
            listOf(sy("min_refresh_rate", "60"), sy("peak_refresh_rate", "120"))),
        N("ألوان حقيقية", "يوقف فلتر الليل الأصفر", listOf(ss("night_display_activated", "0")), ultra = true)
    ),
    1 to listOf(
        N("تجميد تطبيقات السوشيال", "منع الخلفية لفيسبوك وانستغرام وتيك توك ويوتيوب وغيرها",
            listOf("for p in $SOCIAL; do cmd appops set \$p RUN_ANY_IN_BACKGROUND ignore; done; true"),
            listOf("for p in $SOCIAL; do cmd appops set \$p RUN_ANY_IN_BACKGROUND allow; done; true"))
    ),
    6 to listOf(
        N("موفر البيانات", "يمنع بيانات الخلفية",
            listOf("cmd netpolicy set restrict-background true"), listOf("cmd netpolicy set restrict-background false")),
        N("إيقاف GPS", "يوفر المعالج والبطارية",
            listOf("cmd location set-location-enabled false"), listOf("cmd location set-location-enabled true"))
    ),
    8 to listOf(
        N("قياس إطارات اللعبة", "عدد التقطعات ونسبتها (افتح اللعبة ثم اعرض)",
            listOf("dumpsys gfxinfo {pkg} | grep -E 'Total frames|Janky|50th|90th|95th|99th'"), report = true),
        N("تصفير عدادات الإطارات", "ابدأ قياساً جديداً", listOf("dumpsys gfxinfo {pkg} reset"), oneShot = true),
        N("تقرير Game Mode", "هل أبقى أندرويد الوضع؟",
            listOf("cmd game list-modes {pkg}; dumpsys game | head -n 25"), report = true),
        N("لعبة بلا إنترنت", "قطع الشبكة عن الحزمة (أندرويد 14+)",
            listOf("cmd connectivity set-package-networking-enabled false {pkg}"),
            listOf("cmd connectivity set-package-networking-enabled true {pkg}")),
        N("حساسات للحزمة فقط", "يمنع تطبيقات أخرى من الحساسات",
            listOf("cmd sensorservice set-uid-state {pkg} idle"), listOf("cmd sensorservice reset-uid-state {pkg}")),
        N("حواف حرة", "بلا سحب رجوع من الجانبين",
            listOf(ss("back_gesture_inset_scale_left", "0"), ss("back_gesture_inset_scale_right", "0"))),
        N("ثلاثة أزرار تنقل", "بدل الإيماءات",
            listOf("cmd overlay enable com.android.internal.systemui.navbar.threebutton"),
            listOf("cmd overlay enable com.android.internal.systemui.navbar.gestural")),
        N("إيقاف مساعد Google", "يمنع الميكروفون عن تطبيق البحث",
            listOf("cmd appops set com.google.android.googlequicksearchbox RECORD_AUDIO ignore"),
            listOf("cmd appops set com.google.android.googlequicksearchbox RECORD_AUDIO allow")),
        N("إغلاق النوافذ العائمة", "لمرة واحدة", listOf("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS"), oneShot = true)
    )
)

val CHOICES: Map<Int, List<Choice>> = mapOf(
    0 to listOf(
        Choice("ألوان زاهية", "تشبّع الألوان؛ قد يكلّف FPS على المعالجات الضعيفة",
            listOf("1.1", "1.2", "1.3", "1.5").map { it to "service call SurfaceFlinger 1022 f $it" },
            "service call SurfaceFlinger 1022 f 1.0"),
        Choice("دقة الشاشة (Universal Scale)", "يخفف حمل GPU؛ الصورة أقل حدة. «افتراضي» يعيد الدقة الأصلية",
            listOf(90, 80, 70, 60).map { "$it%" to scale(it) }, "wm size reset")
    ),
    4 to listOf(
        Choice("ضغطة طويلة (ms)", "",
            listOf("150", "200", "250", "300", "400").map { it to "settings put secure long_press_timeout $it" },
            "settings delete secure long_press_timeout"),
        Choice("ضغطة مزدوجة (ms)", "",
            listOf("150", "200", "250", "300").map { it to "settings put secure multi_press_timeout $it" },
            "settings delete secure multi_press_timeout"),
        Choice("سرعة المؤشر", "",
            listOf("-3", "0", "3", "5", "7").map { it to "settings put system pointer_speed $it" },
            "settings delete system pointer_speed")
    ),
    8 to listOf(
        Choice("دقة اللعبة (Render Scale)", "للحزم المكتوبة أعلاه",
            listOf(90, 80, 70, 60).map { "$it%" to "cmd game set --mode performance --downscale ${it / 100.0} {pkg}" },
            "cmd game reset {pkg}")
    )
)
