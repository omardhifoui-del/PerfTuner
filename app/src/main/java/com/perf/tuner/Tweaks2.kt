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
    )
)
