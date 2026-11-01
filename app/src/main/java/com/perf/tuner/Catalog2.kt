package com.perf.tuner

object Catalog2 {
    private fun s(id: String, t: String, d: String, on: String, off: String, w: String? = null) = Sw("v2_$id", t, d, on, off, w)
    private fun ch(id: String, t: String, o: List<Pair<String, String>>) = Ch("v2_$id", t, o, -1)
    private fun game(name: String, pkg: String) = Ac(
        "🎮 $name",
        "cmd game set --mode performance $pkg; cmd deviceidle whitelist +$pkg; am set-standby-bucket $pkg active; monkey -p $pkg -c android.intent.category.LAUNCHER 1"
    )
    private fun dens(p: Int) = "d=\$(wm density | grep -o '[0-9]*\$' | head -1); wm density \$((d*$p/100))"

    val sections: List<Sec> = listOf(
        Sec("🖥", "الشاشة والإشعارات (V2)", listOf(
            s("dark", "الوضع الداكن", "يريح العين ويوفر الطاقة", "cmd uimode night yes", "cmd uimode night no"),
            s("headsup", "إيقاف الإشعارات المنبثقة", "لا نوافذ تقاطعك أثناء اللعب", "settings put global heads_up_notifications_enabled 0", "settings put global heads_up_notifications_enabled 1"),
            s("ambient", "إيقاف الشاشة المحيطة", "يقلل إيقاظ الشاشة والمعالج", "settings put secure doze_enabled 0; settings put secure doze_always_on 0", "settings put secure doze_enabled 1"),
            s("wake", "إيقاف إيقاظ الشاشة بالرفع واللمس", "يمنع الإيقاظ العرضي", "settings put secure doze_pulse_on_pick_up 0; settings put secure doze_pulse_on_double_tap 0; settings put secure wake_gesture_enabled 0", "settings put secure doze_pulse_on_pick_up 1; settings put secure doze_pulse_on_double_tap 1; settings put secure wake_gesture_enabled 1"),
            s("rotate", "قفل تدوير الشاشة", "يقلل استخدام المستشعرات", "settings put system accelerometer_rotation 0", "settings put system accelerometer_rotation 1"),
            s("lockfx", "إيقاف أصوات القفل واللمس", "هدوء أكثر وحمل أقل", "settings put system lockscreen_sounds_enabled 0; settings put system dtmf_tone 0; settings put system sound_effects_enabled 0", "settings put system lockscreen_sounds_enabled 1; settings put system dtmf_tone 1; settings put system sound_effects_enabled 1"),
            s("vibring", "إيقاف الاهتزاز مع الرنين", "يوفر الطاقة", "settings put system vibrate_when_ringing 0", "settings put system vibrate_when_ringing 1"),
            s("stayon", "الشاشة مضاءة أثناء الشحن", "مفيد عند اللعب والهاتف موصول", "settings put global stay_on_while_plugged_in 3", "settings put global stay_on_while_plugged_in 0"),
            s("saver", "إيقاف شاشة التوقف", "يوفر المعالج", "settings put secure screensaver_enabled 0", "settings put secure screensaver_enabled 1"),
            s("errdlg", "إخفاء نوافذ أخطاء التطبيقات", "لا مقاطعة عند تعطل تطبيق خلفي", "settings put global hide_error_dialogs 1", "settings put global hide_error_dialogs 0"),
            s("anr", "إخفاء نوافذ ANR الخلفية", "يمنع نوافذ عدم الاستجابة", "settings put global anr_show_background 0", "settings put global anr_show_background 1"),
            ch("timeout", "مهلة إطفاء الشاشة", listOf("15ث" to "settings put system screen_off_timeout 15000", "30ث" to "settings put system screen_off_timeout 30000", "1د" to "settings put system screen_off_timeout 60000", "2د" to "settings put system screen_off_timeout 120000", "5د" to "settings put system screen_off_timeout 300000")),
            ch("font", "حجم الخط", listOf("0.85" to "settings put system font_scale 0.85", "1.0" to "settings put system font_scale 1.0", "1.15" to "settings put system font_scale 1.15", "1.3" to "settings put system font_scale 1.3")),
            ch("bright", "سطوع الشاشة", listOf("تلقائي" to "settings put system screen_brightness_mode 1", "منخفض" to "settings put system screen_brightness_mode 0; settings put system screen_brightness 60", "متوسط" to "settings put system screen_brightness_mode 0; settings put system screen_brightness 130", "عالٍ" to "settings put system screen_brightness_mode 0; settings put system screen_brightness 220")),
            ch("density", "كثافة الشاشة (أخف على المعالج)", listOf("أصلية" to "wm density reset", "90%" to dens(90), "80%" to dens(80)))
        )),
        Sec("📡", "الشبكة والاتصال (V2)", listOf(
            s("netrec", "إيقاف توصيات الشبكات", "يقلل المسح والإشعارات", "settings put global network_recommendations_enabled 0", "settings put global network_recommendations_enabled 1"),
            s("wifinotif", "إيقاف إشعار الشبكات المتاحة", "إشعارات أقل", "settings put global wifi_networks_available_notification_on 0", "settings put global wifi_networks_available_notification_on 1"),
            s("wifiwake", "إيقاف تشغيل الواي فاي التلقائي", "يمنع التشغيل قرب الشبكات المحفوظة", "settings put global wifi_wakeup_enabled 0", "settings put global wifi_wakeup_enabled 1"),
            s("scanthr", "تقييد مسح الواي فاي", "يخفف مسح التطبيقات للشبكات", "settings put global wifi_scan_throttle_enabled 1", "settings put global wifi_scan_throttle_enabled 0"),
            s("mdata", "إيقاف بيانات الجوال الدائمة", "لا تبقى البيانات نشطة مع الواي فاي", "settings put global mobile_data_always_on 0", "settings put global mobile_data_always_on 1"),
            s("datasaver", "موفّر البيانات", "يمنع بيانات الخلفية للتطبيقات", "cmd netpolicy set restrict-background true", "cmd netpolicy set restrict-background false", "قد تتأخر إشعارات بعض التطبيقات. متابعة؟"),
            s("bt", "إيقاف البلوتوث", "يوفر الطاقة", "svc bluetooth disable", "svc bluetooth enable"),
            s("nfc", "إيقاف NFC", "يوفر الطاقة", "svc nfc disable", "svc nfc enable")
        )),
        Sec("🔋", "الطاقة والخلفية (V2)", listOf(
            s("abm", "إيقاف البطارية التكيفية", "تمنع النظام من تقييد تطبيقاتك", "settings put global adaptive_battery_management_enabled 0", "settings put global adaptive_battery_management_enabled 1"),
            s("lowpower", "وضع توفير الطاقة", "للخروج من الألعاب إلى الراحة", "settings put global low_power 1", "settings put global low_power 0", "سيقلل الأداء ويخفف الأنيميشن. متابعة؟"),
            s("loc", "إيقاف الموقع (GPS)", "يوقف طلبات الموقع بالخلفية", "cmd location set-location-enabled false", "cmd location set-location-enabled true", "بعض التطبيقات تحتاج الموقع. متابعة؟")
        )),
        Sec("🛠", "أدوات النظام والتحليل (V2)", listOf(
            Ac("🧱 تجميع speed لكل التطبيقات (بطيء)", "cmd package compile -m speed -a"),
            Ac("↩️ إعادة ضبط التجميع", "cmd package compile --reset -a"),
            Ac("🧼 تنظيف ART", "cmd package art cleanup"),
            Ac("📈 تصفير إحصاءات البطارية", "dumpsys batterystats --reset"),
            Ac("📉 تصفير إحصاءات العمليات", "dumpsys procstats --clear"),
            Ac("📊 استهلاك الذاكرة", "dumpsys meminfo | head -40"),
            Ac("🌡 حالة الحرارة", "dumpsys thermalservice | head -30"),
            Ac("⚙️ معلومات المعالج", "head -22 /proc/cpuinfo"),
            Ac("💽 مساحة التخزين", "df -h /data"),
            Ac("🔋 الأكثر استهلاكاً للبطارية", "dumpsys batterystats | grep -A 12 'Estimated power use' | head -16")
        )),
        Sec("🎮", "ألعاب جاهزة (تشغيل بوضع الأداء)", listOf(
            game("PUBG Mobile", "com.tencent.ig"),
            game("Free Fire", "com.dts.freefireth"),
            game("Call of Duty Mobile", "com.activision.callofduty.shooter"),
            game("Genshin Impact", "com.miHoYo.GenshinImpact"),
            game("Mobile Legends", "com.mobile.legends"),
            game("Roblox", "com.roblox.client"),
            game("Minecraft", "com.mojang.minecraftpe"),
            game("Brawl Stars", "com.supercell.brawlstars"),
            game("Clash of Clans", "com.supercell.clashofclans"),
            game("Subway Surfers", "com.kiloo.subwaysurf"),
            Tip("اللعبة غير المثبتة يظهر لها خطأ بسيط، وهذا طبيعي.")
        ))
    )

    val all: List<Sw> = sections.flatMap { it.items }.filterIsInstance<Sw>()
}
