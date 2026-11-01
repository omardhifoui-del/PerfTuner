package com.perf.tuner

// ultra=false: لا يدخل في زر ULTRA (تعارض مع غيره، أو يحتاج حزمة، أو تقرير)
private fun N(
    title: String, desc: String, on: List<String>, off: List<String> = emptyList(),
    oneShot: Boolean = false, report: Boolean = false, ultra: Boolean = false
) = Tweak(title, desc, on, off, oneShot, ultra, report)

val MORE2: Map<Int, List<Tweak>> = mapOf(
    0 to listOf(
        N("تثبيت 144Hz", "إن كانت الشاشة تدعمه", listOf(sy("min_refresh_rate", "144"), sy("peak_refresh_rate", "144"))),
        N("تثبيت 60Hz", "أقل استهلاكاً", listOf(sy("min_refresh_rate", "60"), sy("peak_refresh_rate", "60"))),
        N("Xiaomi/POCO: تحديث عالٍ", "قد لا يعمل على كل الإصدارات", listOf(ss("miui_refresh_rate", "120"))),
        N("Samsung: سلاسة عالية", "refresh_rate_mode = high", listOf(ss("refresh_rate_mode", "2"))),
        N("عرض معدل التحديث", "طبقة أرقام فوق الشاشة (قد لا تعمل)",
            listOf("service call SurfaceFlinger 1034 i32 1"), listOf("service call SurfaceFlinger 1034 i32 0")),
        N("إخفاء مؤشرات المطور", "اللمسات والإحداثيات", listOf(sy("show_touches", "0"), sy("pointer_location", "0")), ultra = true)
    ),
    1 to listOf(
        N("حد العمليات المخبأة 16", "ذاكرة حرة أكثر للعبة",
            listOf("cmd device_config put activity_manager max_cached_processes 16"),
            listOf("cmd device_config delete activity_manager max_cached_processes")),
        N("تقييد خدمات Google", "قد يؤخر الإشعارات",
            listOf("cmd appops set com.google.android.gms WAKE_LOCK ignore"),
            listOf("cmd appops set com.google.android.gms WAKE_LOCK allow")),
        N("سكون عميق الآن", "يوقف نشاط الخلفية فوراً", listOf("cmd deviceidle force-idle deep"), oneShot = true),
        N("إيقاف السكون القسري", "يرجع الوضع الطبيعي", listOf("cmd deviceidle unforce"), oneShot = true),
        N("تقرير: أكثر التطبيقات استهلاكاً للذاكرة", "",
            listOf("dumpsys meminfo | sed -n '/Total PSS by process/,/^\$/p' | head -n 15"), report = true),
        N("تقرير: حالة الذاكرة", "", listOf("head -n 6 /proc/meminfo"), report = true)
    ),
    2 to listOf(
        N("تقرير: المعالج", "", listOf("nproc; getprop ro.board.platform; getprop ro.hardware; cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"), report = true),
        N("تقرير: أكثر التطبيقات استهلاكاً للمعالج", "", listOf("top -b -n 1 -m 10 | tail -n 12"), report = true)
    ),
    3 to listOf(
        N("إلغاء MSAA الإجباري", "يوقف 4x MSAA من المطور", listOf("setprop debug.egl.force_msaa false"), ultra = true),
        N("إزالة طبقات GPU debug", "طبقات منسية تبطئ الرسوميات", listOf(sg("enable_gpu_debug_layers", "0")), ultra = true),
        N("إيقاف overlays HWUI", "Overdraw وشريط الأداء",
            listOf("setprop debug.hwui.overdraw false", "setprop debug.hwui.profile false"), oneShot = true, ultra = true),
        N("تركيب بالـ GPU", "debug.sf.hw = 0؛ يرفع استهلاك GPU",
            listOf("setprop debug.sf.hw 0"), listOf("setprop debug.sf.hw 1")),
        N("SurfaceFlinger Instant Latch", "تقليل تأخير الإطارات؛ تجريبي",
            listOf("setprop debug.sf.latch_unsignaled 1"), listOf("setprop debug.sf.latch_unsignaled 0")),
        N("HWUI Hint Manager (ADPF)", "جلسات أداء للرسم",
            listOf("setprop debug.hwui.use_hint_manager true"), listOf("setprop debug.hwui.use_hint_manager false")),
        N("تقرير: GPU", "", listOf("dumpsys SurfaceFlinger | grep -iE 'GLES|Vulkan|renderengine' | head -n 6"), report = true),
        N("تقرير: معدل التحديث", "", listOf("dumpsys display | grep -iE 'refreshRate|fps' | head -n 6"), report = true)
    ),
    4 to listOf(
        N("ضغطة طويلة 200ms", "أسرع", listOf(ss("long_press_timeout", "200"))),
        N("سرعة المؤشر 7", "الحد الأقصى", listOf(sy("pointer_speed", "7"))),
        N("لمس متعدد 100ms", "نافذة استجابة أقصر", listOf(ss("multi_press_timeout", "100")))
    ),
    5 to listOf(
        N("تعطيل فحص البوابة", "اتصال أسرع بالشبكات (captive portal)", listOf(sg("captive_portal_mode", "0")))
    ),
    6 to listOf(
        N("قائمة بيضاء للبطارية", "للحزم المكتوبة في تبويب الألعاب",
            listOf("dumpsys deviceidle whitelist +{pkg}", "cmd appops set {pkg} RUN_ANY_IN_BACKGROUND allow"),
            listOf("dumpsys deviceidle whitelist -{pkg}"))
    ),
    7 to listOf(
        N("تقرير: مساحة التخزين", "", listOf("df -h /data | tail -n 2"), report = true)
    ),
    8 to listOf(
        N("عرض حزم تطبيقاتك", "لتنسخ اسم حزمة اللعبة", listOf("pm list packages -3 | sed 's/package://' | head -n 80"), report = true),
        N("إلغاء حد 60FPS (أندرويد 14+)", "إطار افتراضي للألعاب",
            listOf("setprop debug.graphics.game_default_frame_rate_disabled true"),
            listOf("setprop debug.graphics.game_default_frame_rate_disabled false")),
        N("Game Mode: أداء", "للحزمة المحددة",
            listOf("cmd game mode performance {pkg}"), listOf("cmd game mode standard {pkg}")),
        N("Game Mode بدون سقف FPS", "يزيل قفل FPS وتصغير الدقة",
            listOf("cmd game set --mode performance --downscale disable --fps disable {pkg}"),
            listOf("cmd game reset {pkg}")),
        N("أولوية اللعبة", "Active bucket وخارج توفير البطارية",
            listOf("am set-standby-bucket {pkg} active", "dumpsys deviceidle whitelist +{pkg}"),
            listOf("dumpsys deviceidle whitelist -{pkg}", "am set-standby-bucket {pkg} working_set")),
        N("تقييد تطبيقات ثقيلة بالخلفية", "ضع حزمها في الحقل أعلاه",
            listOf("cmd appops set {pkg} RUN_IN_BACKGROUND ignore", "cmd appops set {pkg} RUN_ANY_IN_BACKGROUND ignore"),
            listOf("cmd appops set {pkg} RUN_IN_BACKGROUND allow", "cmd appops set {pkg} RUN_ANY_IN_BACKGROUND allow")),
        N("إغلاق التطبيقات المحددة", "force-stop لمرة واحدة", listOf("am force-stop {pkg}"), oneShot = true),
        N("تجميع اللعبة speed", "كود أسرع؛ يأخذ وقتاً", listOf("cmd package compile -m speed -f {pkg}"), oneShot = true),
        N("تحرير الذاكرة قبل اللعب", "am kill-all", listOf("am kill-all"), oneShot = true)
    ),
    9 to listOf(
        N("تقرير: البطارية", "", listOf("dumpsys battery | head -n 15"), report = true),
        N("تقرير: الحرارة", "", listOf("dumpsys thermalservice | head -n 20"), report = true)
    )
)
