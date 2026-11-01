package com.perf.tuner

val MORE: Map<Int, List<Tweak>> = mapOf(
    3 to listOf(
        Tweak("محرك Skia GL", "ينطبق على التطبيقات التي تُفتح بعد التفعيل",
            listOf("setprop debug.hwui.renderer skiagl"), listOf("setprop debug.hwui.renderer ''")),
        Tweak("محرك Skia Vulkan", "تجريبي؛ قد لا يناسب كل الأجهزة",
            listOf("setprop debug.hwui.renderer skiavk"), listOf("setprop debug.hwui.renderer ''")),
        Tweak("تعطيل HW overlays", "تركيب الشاشة بالـ GPU؛ يرفع الاستهلاك",
            listOf("service call SurfaceFlinger 1008 i32 1"), listOf("service call SurfaceFlinger 1008 i32 0")),
        Tweak("إلغاء ضبابية النوافذ", "يخفف حمل الرسوميات", listOf(sg("disable_window_blurs", "1")))
    ),
    4 to listOf(
        Tweak("ضغطة طويلة 250ms", "استجابة أسرع للضغط المطول", listOf(ss("long_press_timeout", "250"))),
        Tweak("ضغطة مزدوجة أسرع", "فاصل أقصر بين اللمستين", listOf(ss("multi_press_timeout", "250"))),
        Tweak("سرعة المؤشر 3", "المدى من -7 إلى 7", listOf(sy("pointer_speed", "3"))),
        Tweak("إيقاف اهتزاز اللمس", "يقلل المقاطعات", listOf(sy("haptic_feedback_enabled", "0"))),
        Tweak("إيقاف أصوات اللمس", "هدوء أكثر", listOf(sy("sound_effects_enabled", "0"))),
        Tweak("إيقاف أصوات القفل", "", listOf(sy("lockscreen_sounds_enabled", "0"))),
        Tweak("إيقاف نغمات الاتصال", "", listOf(sy("dtmf_tone", "0"))),
        Tweak("إيقاف الاهتزاز مع الرنين", "يوفر الطاقة", listOf(sy("vibrate_when_ringing", "0"))),
        Tweak("إيقاف أصوات الشحن", "", listOf(sg("charging_sounds_enabled", "0")))
    ),
    5 to listOf(
        Tweak("DNS خاص: Google", "dns.google",
            listOf(sg("private_dns_mode", "hostname"), sg("private_dns_specifier", "dns.google"))),
        Tweak("DNS خاص: Cloudflare", "one.one.one.one",
            listOf(sg("private_dns_mode", "hostname"), sg("private_dns_specifier", "one.one.one.one"))),
        Tweak("إيقاف مسح Wi-Fi الخلفي", "يوفر المعالج والبطارية", listOf(sg("wifi_scan_always_enabled", "0"))),
        Tweak("إيقاف مسح Bluetooth الخلفي", "", listOf(sg("ble_scan_always_enabled", "0"))),
        Tweak("Wi-Fi منخفض التأخير", "للألعاب (أندرويد 11+)",
            listOf("cmd wifi force-low-latency-mode enabled"), listOf("cmd wifi force-low-latency-mode disabled")),
        Tweak("Wi-Fi عالي الأداء", "يستهلك بطارية أكثر",
            listOf("cmd wifi force-hi-perf-mode enabled"), listOf("cmd wifi force-hi-perf-mode disabled")),
        Tweak("تحسين نافذة TCP", "قيمة rwnd = 60", listOf(sg("tcp_default_init_rwnd", "60"))),
        Tweak("إيقاف إشعارات الشبكات", "", listOf(sg("wifi_networks_available_notification_on", "0"))),
        Tweak("إيقاف بيانات الجوال الدائمة", "توفير طاقة", listOf(sg("mobile_data_always_on", "0"))),
        Tweak("إيقاف توصيات الشبكات", "", listOf(sg("network_recommendations_enabled", "0")))
    ),
    6 to listOf(
        Tweak("تفعيل توفير الطاقة", "يخفض الأداء", listOf(sg("low_power", "1"))),
        Tweak("إيقاف توفير الطاقة التكيفي", "",
            listOf("cmd power set-adaptive-power-saver-enabled false"),
            listOf("cmd power set-adaptive-power-saver-enabled true")),
        Tweak("إيقاف App Standby", "التطبيقات لا تُقيَّد", listOf(sg("app_standby_enabled", "0"))),
        Tweak("إيقاف التوفير التلقائي", "", listOf(sg("automatic_power_save_mode", "0"))),
        Tweak("إيقاف التوفير الديناميكي", "", listOf(sg("dynamic_power_savings_enabled", "0"))),
        Tweak("سطوع يدوي", "إيقاف السطوع التلقائي", listOf(sy("screen_brightness_mode", "0"))),
        Tweak("إيقاف العرض الدائم", "يوفر بطارية", listOf(ss("doze_always_on", "0")))
    ),
    2 to listOf(
        Tweak("تجميد التطبيقات المخبأة", "App Freezer؛ يوفر المعالج (أندرويد 11+)",
            listOf(sg("cached_apps_freezer", "enabled")), listOf(sg("cached_apps_freezer", "disabled"))),
        Tweak("ضغط ذاكرة العمليات", "Compaction؛ يحرر ذاكرة الخلفية",
            listOf("cmd device_config put activity_manager use_compaction true"),
            listOf("cmd device_config delete activity_manager use_compaction")),
        Tweak("قتل العمليات المخبأة الآن", "تنفيذ لمرة واحدة", listOf("am kill-all"), oneShot = true),
        Tweak("تجميع التطبيقات speed-profile", "أسرع وأخف من speed؛ يأخذ وقتاً",
            listOf("cmd package compile -m speed-profile -a"), oneShot = true),
        Tweak("تجميع التطبيقات speed (بطيء)", "أقصى تحسين؛ قد يستغرق دقائق طويلة",
            listOf("cmd package compile -m speed -a"), oneShot = true)
    ),
    7 to listOf(
        Tweak("صيانة التخزين الآن", "sm idle-maint", listOf("sm idle-maint run"), oneShot = true),
        Tweak("مسح كاش الملفات الخارجية", "مجلدات cache فقط", listOf("rm -rf /sdcard/Android/data/*/cache/*"), oneShot = true),
        Tweak("حذف الصور المصغرة", "تُعاد عند فتح المعرض", listOf("rm -rf /sdcard/DCIM/.thumbnails"), oneShot = true),
        Tweak("مسح سجلات logcat", "تفريغ المخزن", listOf("logcat -c"), oneShot = true),
        Tweak("مسح كاش كل التطبيقات", "pm trim-caches", listOf("pm trim-caches 999G"), oneShot = true)
    ),
    8 to listOf(
        Tweak("عدم الإزعاج (أولوية)", "يمنع المقاطعات أثناء اللعب", listOf(sg("zen_mode", "1"))),
        Tweak("إيقاف إشعارات الأعلى", "لا نوافذ منبثقة أثناء اللعب", listOf(sg("heads_up_notifications_enabled", "0"))),
        Tweak("إخفاء نوافذ أخطاء التطبيقات", "يخفي العرض فقط ولا يصلح المشكلة", listOf(sg("hide_error_dialogs", "1"))),
        Tweak("إخفاء ANR الخلفي", "", listOf(ss("anr_show_background", "0"))),
        Tweak("قفل تدوير الشاشة", "", listOf(sy("accelerometer_rotation", "0"))),
        Tweak("إيقاف شاشة التوقف", "", listOf(ss("screensaver_enabled", "0")))
    ),
    9 to listOf(
        Tweak("خيارات المطور", "تفعيل القائمة", listOf(sg("development_settings_enabled", "1"))),
        Tweak("تفعيل ADB", "التصحيح عبر USB", listOf(sg("adb_enabled", "1"))),
        Tweak("إيقاف سجل بدء الأنشطة", "تقليل الكتابة", listOf(sg("activity_starts_logging_enabled", "0"))),
        Tweak("إيقاف الاستيقاظ بالرفع", "", listOf(ss("wake_gesture_enabled", "0"))),
        Tweak("إيقاف إرسال أخطاء التطبيقات", "", listOf(sg("send_action_app_error", "0")))
    )
)
