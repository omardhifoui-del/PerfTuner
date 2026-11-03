package com.perf.tuner

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.WindowManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val id = resources.getIdentifier("funk", "raw", packageName)
        if (id != 0) player = MediaPlayer.create(this, id)?.apply { isLooping = true; setVolume(.6f, .6f) }
        val engine = Engine(this)
        try {
            if (rikka.shizuku.Shizuku.pingBinder() &&
                rikka.shizuku.Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) rikka.shizuku.Shizuku.requestPermission(0)
        } catch (_: Throwable) {}
        try {
            val dm = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val best = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY).supportedModes.maxOf { it.refreshRate }
            window.attributes = window.attributes.also { it.preferredRefreshRate = best }
        } catch (_: Throwable) {}
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = darkColorScheme()) { App(engine, player) }
            }
        }
    }
    override fun onPause() { super.onPause(); player?.pause() }
    override fun onDestroy() { player?.release(); super.onDestroy() }
}

/** اللون يتغير 10 مرات بالثانية فقط، ولا يقرؤه إلا المكوّن الصغير الذي يحتاجه */
@Composable
fun rememberHue(): State<Float> {
    val t = rememberInfiniteTransition(label = "rgb")
    return t.animateFloat(0f, 360f, infiniteRepeatable(tween(10000, easing = LinearEasing)), label = "h")
}

private fun hc(h: Float, off: Float = 0f) = Color.hsv((h + off) % 360f, 0.8f, 1f)
private val ACCENT = Color(0xFF7C4DFF)

@Composable
fun App(engine: Engine, player: MediaPlayer?) {
    var sel by remember { mutableIntStateOf(0) }
    var ver by remember { mutableIntStateOf(0) }
    val hue = rememberHue()   // لا نقرأ .value هنا حتى لا تُعاد رسم الشاشة كلها
    Box(Modifier.fillMaxSize()) {
    AppBackground()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Header(hue, player)
        DeviceCard(hue)
        TopTabs(sel, hue) { sel = it }
        AnimatedContent(
            targetState = sel, modifier = Modifier.weight(1f), label = "page",
            transitionSpec = {
                val d = if (targetState > initialState) 1 else -1
                val e = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
                (slideInHorizontally(tween(380, easing = e)) { it / 3 * d } +
                    fadeIn(tween(300, 60)) + scaleIn(tween(380, easing = e), initialScale = 0.92f))
                    .togetherWith(
                        slideOutHorizontally(tween(300, easing = e)) { -it / 4 * d } +
                            fadeOut(tween(160)) + scaleOut(tween(300), targetScale = 0.96f)
                    ).using(SizeTransform(clip = false))
            }
        ) { page -> key(ver) { Page(page, engine) { ver++ } } }
        BottomNav(sel, hue) { sel = it }
    }
    }
}

@Composable
fun Header(hue: State<Float>, player: MediaPlayer?) {
    var music by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("⚡ PERF TUNER", Modifier.weight(1f),
            style = TextStyle(brush = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFB388FF))), fontSize = 24.sp, fontWeight = FontWeight.Black))
        if (player != null) TextButton(onClick = {
            music = !music; if (music) player.start() else player.pause()
        }) { Text(if (music) "🔊" else "🔇", fontSize = 22.sp) }
    }
}

@Composable
fun TopTabs(sel: Int, hue: State<Float>, onSel: (Int) -> Unit) {
    val c = ACCENT
    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 5..9) Chip(CATS[i], sel == i, c, Modifier.weight(1f)) { onSel(i) }
    }
}

@Composable
fun BottomNav(sel: Int, hue: State<Float>, onSel: (Int) -> Unit) {
    val c = ACCENT
    NavigationBar(containerColor = Color(0xFF0E0C20)) {
        for (i in 0..4) NavigationBarItem(
            selected = sel == i, onClick = { onSel(i) },
            icon = { Text(CATS[i].substringBefore(' '), fontSize = 20.sp) },
            label = { Text(CATS[i].substringAfter(' '), fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = c.copy(alpha = .25f))
        )
    }
}

@Composable
fun Chip(text: String, selected: Boolean, c: Color, m: Modifier, onClick: () -> Unit) {
    val bg = if (selected) c.copy(alpha = .3f) else Color.White.copy(alpha = .06f)
    Box(
        m.clip(RoundedCornerShape(12.dp)).background(bg).clickable { onClick() }.padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text(text.substringAfter(' '), fontSize = 11.sp, color = Color.White, maxLines = 1) }
}

private data class Sys(
    val ramUsed: Long = 0, val ramTotal: Long = 1,
    val stUsed: Double = 0.0, val stTotal: Double = 1.0,
    val bat: Int = 0, val shizuku: Boolean = false
)

private fun f1(v: Double) = String.format(java.util.Locale.US, "%.1f", v)

@Composable
fun DeviceCard(hue: State<Float>) {
    val ctx = LocalContext.current
    var s by remember { mutableStateOf(Sys()) }
    val display = remember {
        if (Build.VERSION.SDK_INT >= 30) {
            val r = (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).maximumWindowMetrics.bounds
            "${r.width()}x${r.height()}"
        } else ctx.resources.displayMetrics.let { "${it.widthPixels}x${it.heightPixels}" }
    }
    LaunchedEffect(Unit) {
        while (true) {
            s = withContext(Dispatchers.IO) {
                val mi = ActivityManager.MemoryInfo()
                (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
                val sf = StatFs(Environment.getDataDirectory().path)
                val tot = sf.totalBytes / 1073741824.0
                val free = sf.availableBytes / 1073741824.0
                val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val lvl = b?.getIntExtra("level", 0) ?: 0
                val sc = (b?.getIntExtra("scale", 100) ?: 100).coerceAtLeast(1)
                val sh = try { rikka.shizuku.Shizuku.pingBinder() } catch (_: Throwable) { false }
                Sys(mi.totalMem - mi.availMem, mi.totalMem, tot - free, tot, lvl * 100 / sc, sh)
            }
            delay(3000)
        }
    }
    val gb = 1073741824.0
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .drawBehind {
                val h = hue.value                       // يُقرأ في مرحلة الرسم فقط → لا إعادة تركيب
                val r = CornerRadius(18.dp.toPx())
                drawRoundRect(Brush.linearGradient(listOf(hc(h, 200f).copy(alpha = .35f), hc(h, 320f).copy(alpha = .35f))), cornerRadius = r)
                drawRoundRect(Brush.linearGradient(listOf(hc(h), hc(h, 120f), hc(h, 240f))), cornerRadius = r, style = Stroke(2.dp.toPx()))
            }
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${Build.MANUFACTURER} ${Build.MODEL}".uppercase(), color = Color.White,
                    fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1)
                Text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", color = Color(0xFF9FD3FF), fontSize = 12.sp)
            }
            Row(
                Modifier.border(BorderStroke(1.5.dp, ACCENT), RoundedCornerShape(50)).padding(10.dp, 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).background(if (s.shizuku) Color(0xFF2EE66B) else Color(0xFFFF4D4D), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("Shizuku", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tile("RAM", "${f1(s.ramUsed / gb)}/${f1(s.ramTotal / gb)} GB", (s.ramUsed.toFloat() / s.ramTotal), Color(0xFF00E5FF))
            Tile("STORAGE", "${f1(s.stUsed)}/${f1(s.stTotal)} GB", (s.stUsed / s.stTotal).toFloat(), Color(0xFF7CFFB2))
            Tile("BATTERY", "${s.bat}%", s.bat / 100f, if (s.bat < 20) Color(0xFFFF4D4D) else Color(0xFFFFD54F))
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tile("SDK LEVEL", "API ${Build.VERSION.SDK_INT}", null, ACCENT)
            Tile("DISPLAY", display, null, ACCENT)
            Tile("FREE SPACE", "${f1(s.stTotal - s.stUsed)} GB", null, ACCENT)
        }
        Spacer(Modifier.height(6.dp))
        LiveLine()
    }
}

@Composable
fun RowScope.Tile(label: String, value: String, frac: Float?, c: Color) {
    Column(
        Modifier.weight(1f).background(Color.White.copy(alpha = .12f), RoundedCornerShape(12.dp)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color.White.copy(alpha = .8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
        if (frac != null) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = .25f))) {
                Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(c))
            }
        }
    }
}

@Composable
fun LiveLine() {
    val ctx = LocalContext.current
    var line by remember { mutableStateOf("⚡ جارٍ القياس...") }
    LaunchedEffect(Unit) {
        var pt = 0L; var pi = 0L
        while (true) {
            line = withContext(Dispatchers.IO) {
                var cpu = -1
                val p = Shell.run("head -n1 /proc/stat").second.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
                if (p.size >= 8) {
                    val idle = p[3] + p[4]; val tot = p.take(8).sum()
                    val dt = tot - pt; val di = idle - pi
                    if (pt > 0 && dt > 0) cpu = ((dt - di) * 100 / dt).toInt()
                    pt = tot; pi = idle
                }
                val mi = ActivityManager.MemoryInfo()
                (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
                val used = (mi.totalMem - mi.availMem) / 1048576
                val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val temp = (b?.getIntExtra("temperature", 0) ?: 0) / 10f
                val lvl = b?.getIntExtra("level", 0) ?: 0
                "⚡ CPU " + (if (cpu >= 0) "$cpu%" else "--") + "  •  RAM ${used}MB  •  🌡 ${temp}°C  •  🔋 $lvl%"
            }
            delay(3000)
        }
    }
    Text(line, color = Color(0xFF7CFFB2), fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun Page(page: Int, engine: Engine, onBoost: () -> Unit) {
    val list = TWEAKS[page].orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { PerfOptButton(engine, onBoost) }
        item { BoostBar(engine, onBoost) }
        item { Text(CATS[page], color = ACCENT, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        if (page == 8) item {
            var v by remember { mutableStateOf(engine.pkgs().joinToString(", ")) }
            OutlinedTextField(
                value = v, onValueChange = { v = it; engine.setPkgs(it) }, modifier = Modifier.fillMaxWidth(),
                label = { Text("حزم الألعاب/التطبيقات (افصل بفاصلة)") }, placeholder = { Text("com.tencent.ig") }
            )
        }
        if (page == 0) item { DpiCard(engine) }
        if (page == 8) item { GameWatchRow(engine) }
        for (ch in CHOICES[page].orEmpty()) item { ChoiceCard(ch, engine) }
        if (list.isEmpty()) item { Text("تُضاف تفعيلات هذا التبويب لاحقاً", color = Color.Gray) }
        items(list.size, key = { "$page-$it" }) { i -> TweakRow("$page-$i", list[i], engine) }
        item { Signature() }
    }
}

@Composable
fun TweakRow(id: String, t: Tweak, engine: Engine) {
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var on by remember { mutableStateOf(engine.isOn(id)) }
    var busy by remember { mutableStateOf(false) }
    var dlg by remember { mutableStateOf<String?>(null) }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    fun result(ok: Boolean) = toast(if (ok) "✔ تم" else "✘ فشل أو غير مدعوم على هذا الجهاز")
    fun needPkg(): Boolean {
        val miss = t.perPkg && engine.pkgs().isEmpty()
        if (miss) toast("اكتب حزمة اللعبة أولاً في تبويب الألعاب")
        return miss
    }
    Row(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = .06f), RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold)
            if (t.desc.isNotEmpty()) Text(t.desc, color = Color.Gray, fontSize = 12.sp)
        }
        if (t.report) Button(enabled = !busy, onClick = {
            if (!needPkg()) scope.launch { busy = true; val o = withContext(Dispatchers.IO) { engine.report(t.on.first()) }; busy = false; dlg = o }
        }) { Text("عرض") }
        else if (t.oneShot) Button(enabled = !busy, onClick = {
            if (!needPkg()) scope.launch { busy = true; val ok = withContext(Dispatchers.IO) { engine.apply(t, id) }; busy = false; result(ok) }
        }) { Text("تنفيذ") }
        else Switch(checked = on, enabled = !busy, onCheckedChange = { v ->
            if (!needPkg()) scope.launch {
                busy = true
                val ok = withContext(Dispatchers.IO) { if (v) engine.apply(t, id) else engine.revert(t, id) }
                if (ok) on = v; busy = false; result(ok)
            }
        }, colors = SwitchDefaults.colors(checkedTrackColor = ACCENT))
    }
    dlg?.let { out ->
        AlertDialog(
            onDismissRequest = { dlg = null },
            confirmButton = { TextButton(onClick = { dlg = null }) { Text("إغلاق") } },
            title = { Text(t.title, fontSize = 15.sp) },
            text = { Text(out, Modifier.verticalScroll(rememberScrollState()), fontSize = 11.sp) }
        )
    }
}

@Composable
fun Signature() {
    val t = rememberInfiniteTransition(label = "sig")
    val a = t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Text(
            "Made by Omar Dhaifawi", Modifier.graphicsLayer { alpha = a.value },
            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black,
            style = TextStyle(shadow = Shadow(Color(0xFF00E5FF), blurRadius = 18f))
        )
    }
}

// (التبويب، رقم التفعيلة): مجموعة آمنة لا ترفع الحرارة ولا تستهلك البطارية بشكل مبالغ
private val BOOST = listOf(
    0 to 0, 0 to 9, 1 to 0, 1 to 1, 1 to 3, 1 to 9,
    2 to 0, 2 to 1, 2 to 2, 4 to 0, 4 to 1, 5 to 2, 5 to 3
)

// أوامر مستبعدة من ULTRA لأنها تتعارض مع غيرها أو تضر (حذف ملفات، تغيير مظهر، رفع حرارة بلا فائدة)
private val EXCL = setOf(
    0 to 1, 0 to 3, 0 to 4, 0 to 5, 0 to 6, 0 to 7, 0 to 8,
    1 to 6, 1 to 8, 2 to 3, 2 to 4, 3 to 1, 3 to 2, 5 to 1,
    6 to 0, 6 to 5, 7 to 1, 7 to 2, 8 to 0, 8 to 1, 8 to 2, 8 to 4, 9 to 0, 9 to 1
)

private fun allIdx() = TWEAKS.flatMap { (p, l) -> l.indices.map { p to it } }
private fun ultraIdx() = allIdx().filter { k ->
    k !in EXCL && TWEAKS[k.first]!![k.second].let { t -> t.ultra && !t.report && !t.perPkg }
}
private fun byTitle(titles: Set<String>) = allIdx().filter { TWEAKS[it.first]!![it.second].title in titles }

private val PROFILES = listOf("افتراضي", "متوازن", "توفير", "أداء", "ألعاب", "أقصى")
private val P_BALANCED = setOf(
    "أنيميشن 0.5x", "إلغاء ضبابية النوافذ", "تجميد التطبيقات المخبأة",
    "ضغط ذاكرة العمليات", "ضغطة طويلة 250ms", "ضغطة مزدوجة أسرع"
)
private val P_BATTERY = setOf(
    "تفعيل توفير الطاقة", "إيقاف العرض الدائم", "تثبيت 60Hz", "إيقاف مسح Wi-Fi الخلفي",
    "إيقاف مسح Bluetooth الخلفي", "أنيميشن 0.5x", "إيقاف اهتزاز اللمس", "إيقاف أصوات اللمس"
)
private val P_GAME_EXTRA = setOf("Game Mode: أداء", "أولوية اللعبة", "إلغاء حد 60FPS (أندرويد 14+)")

private fun profileList(i: Int): List<Pair<Int, Int>> = when (i) {
    1 -> byTitle(P_BALANCED)
    2 -> byTitle(P_BATTERY)
    3 -> BOOST
    4 -> ultraIdx() + byTitle(P_GAME_EXTRA)
    5 -> ultraIdx()
    else -> emptyList()
}

private fun applyList(engine: Engine, list: List<Pair<Int, Int>>): Int {
    var c = 0
    for ((p, i) in list) {
        val t = TWEAKS[p]?.getOrNull(i) ?: continue
        if (t.report || (t.perPkg && engine.pkgs().isEmpty())) continue
        if (engine.apply(t, "$p-$i")) c++
    }
    return c
}

private fun restoreAll(engine: Engine): Int {
    var c = 0
    PERF_OPT.forEachIndexed { i, t -> if (engine.isOn("perfopt-$i") && engine.revert(t, "perfopt-$i")) c++ }
    for ((p, i) in allIdx()) {
        val t = TWEAKS[p]!![i]; val id = "$p-$i"
        if (!t.oneShot && !t.report && engine.isOn(id) && engine.revert(t, id)) c++
    }
    for (ch in CHOICES.values.flatten()) if (engine.choiceSel(ch.title).isNotEmpty()) {
        if (engine.runCmd(ch.reset)) c++
        engine.setChoiceSel(ch.title, "")
    }
    return c
}

@Composable
fun BoostBar(engine: Engine, onDone: () -> Unit) {
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var showProfiles by remember { mutableStateOf(false) }
    var showRam by remember { mutableStateOf(false) }
    fun run(mode: Int) { // 0 آمن، 1 ULTRA، 2 استعادة، 3 تحرير RAM، 4 مسح الكاش
        scope.launch {
            busy = true
            val (n, total) = withContext(Dispatchers.IO) {
                when (mode) {
                    0 -> applyList(engine, BOOST) to BOOST.size
                    1 -> { val l = ultraIdx(); applyList(engine, l) to l.size }
                    2 -> restoreAll(engine) to 0
                    3 -> { Shell.run("am kill-all"); 0 to 0 }
                    else -> { Shell.run("pm trim-caches 999G"); 0 to 0 }
                }
            }
            busy = false; onDone()
            val msg = when (mode) {
                0 -> "🚀 طُبّق $n / $total"
                1 -> "🔥 ULTRA طُبّق $n / $total"
                2 -> "♻ استُعيد $n"
                3 -> "🧠 حُرِّرت الذاكرة"
                else -> "🧹 مُسح الكاش"
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }
    val pad = PaddingValues(4.dp)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { run(1) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "جارٍ التنفيذ..." else "🔥 ULTRA PERFORMANCE", fontWeight = FontWeight.Black)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { run(0) }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = pad) { Text("🚀 آمن", fontSize = 12.sp) }
            OutlinedButton(onClick = { run(3) }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = pad) { Text("🧠 RAM", fontSize = 12.sp) }
            OutlinedButton(onClick = { run(4) }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = pad) { Text("🧹 كاش", fontSize = 12.sp) }
            OutlinedButton(onClick = { run(2) }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = pad) { Text("♻ استعادة", fontSize = 12.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { showProfiles = true }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = pad) { Text("🎛 ملفات وتحليل", fontSize = 12.sp) }
            OutlinedButton(onClick = { showRam = true }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = pad) { Text("📊 RAM Booster", fontSize = 12.sp) }
        }
    }
    if (showProfiles) ProfilesDialog(engine, { showProfiles = false }, onDone)
    if (showRam) RamBoosterDialog { showRam = false }
}

@Composable
fun ProfilesDialog(engine: Engine, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var checks by remember { mutableStateOf<List<Pair<String, Boolean>>?>(null) }
    var sel by remember { mutableIntStateOf(3) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        checks = withContext(Dispatchers.IO) {
            listOf(
                "اتصال Shizuku" to (Shell.run("echo ok").second == "ok"),
                "محرك الشاشة" to Shell.run("dumpsys display | head -n 3").second.isNotBlank(),
                "تحليل الذاكرة" to Shell.run("head -n 1 /proc/meminfo").second.isNotBlank(),
                "التطبيقات الخلفية" to Shell.run("dumpsys activity processes | head -n 2").second.isNotBlank(),
                "وضع الألعاب" to (Shell.run("cmd -l | grep -q game").first == 0),
                "الحالة الحرارية" to Shell.run("dumpsys thermalservice | head -n 2").second.isNotBlank()
            )
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحليل الجهاز والملفات", fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val c = checks
                if (c == null) Text("جارٍ التحليل...", color = Color.Gray)
                else c.forEach { (n, ok) -> Text((if (ok) "✅ " else "❌ ") + n, fontSize = 13.sp) }
                Spacer(Modifier.height(10.dp))
                Text("الملف:", fontWeight = FontWeight.Bold)
                PROFILES.forEachIndexed { i, name ->
                    Row(Modifier.fillMaxWidth().clickable { sel = i }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sel == i, onClick = { sel = i })
                        Text(name)
                    }
                }
                Text("«ألعاب» يستخدم الحزم المكتوبة في تبويب الألعاب. التطبيق يستعيد كل شيء أولاً ثم يطبّق الملف.",
                    fontSize = 11.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(enabled = !busy && checks != null, onClick = {
                scope.launch {
                    busy = true
                    val n = withContext(Dispatchers.IO) { restoreAll(engine); applyList(engine, profileList(sel)) }
                    busy = false
                    Toast.makeText(ctx, "🎛 ${PROFILES[sel]}: طُبّق $n", Toast.LENGTH_LONG).show()
                    onDone(); onDismiss()
                }
            }) { Text(if (busy) "..." else "▶ تطبيق") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

@Composable
fun RamBoosterDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<Pair<String, Long>>?>(null) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val out = Shell.run("dumpsys meminfo | sed -n '/Total PSS by process/,/^$/p'").second
            val rx = Regex("""\s*([\d,]+)K: (\S+) \(pid (\d+)""")
            val safe = Regex("[A-Za-z0-9._]+")
            out.lines().mapNotNull { l ->
                val m = rx.find(l) ?: return@mapNotNull null
                val pkg = m.groupValues[2].substringBefore(':')
                val mb = (m.groupValues[1].replace(",", "").toLongOrNull() ?: 0L) / 1024
                if (!safe.matches(pkg) || pkg == ctx.packageName || pkg == "system" ||
                    pkg.startsWith("com.android.") || pkg.startsWith("android.") ||
                    pkg.contains("launcher") || pkg.contains("inputmethod") || pkg.contains("keyboard")
                ) null else pkg to mb
            }.distinctBy { it.first }.take(12)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📊 RAM Booster", fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val list = apps
                if (list == null) Text("جارٍ القراءة...", color = Color.Gray)
                else if (list.isEmpty()) Text("لا توجد تطبيقات ثقيلة الآن", color = Color.Gray)
                else list.forEach { (pkg, mb) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pkg, fontSize = 12.sp, maxLines = 1)
                            Text("$mb MB", fontSize = 11.sp, color = Color.Gray)
                        }
                        TextButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { Shell.run("am force-stop $pkg") }
                                apps = apps?.filter { it.first != pkg }
                            }
                        }) { Text("إيقاف") }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = !apps.isNullOrEmpty(), onClick = {
                scope.launch {
                    val l = apps.orEmpty().take(8)
                    withContext(Dispatchers.IO) { l.forEach { Shell.run("am force-stop ${it.first}") } }
                    Toast.makeText(ctx, "أُوقف ${l.size} تطبيقات", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }) { Text("إيقاف أثقل 8") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

@Composable
fun AppBackground() {
    val ctx = LocalContext.current
    val id = remember { ctx.resources.getIdentifier("app_bg", "drawable", ctx.packageName) }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B18))) {
        if (id != 0) {
            Image(painterResource(id), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            // تعتيم فوق الصورة حتى تبقى النصوص واضحة (غيّر 0.78 و0.88 لتفتيحها أو تغميقها)
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.88f)))))
        }
    }
}

@Composable
fun ChoiceCard(ch: Choice, engine: Engine) {
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var sel by remember { mutableStateOf(engine.choiceSel(ch.title)) }
    var busy by remember { mutableStateOf(false) }
    fun pick(label: String, cmd: String) {
        if (cmd.contains("{pkg}") && engine.pkgs().isEmpty()) {
            Toast.makeText(ctx, "اكتب حزمة اللعبة أولاً في تبويب الألعاب", Toast.LENGTH_SHORT).show(); return
        }
        scope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) { engine.runCmd(cmd) }
            busy = false
            if (ok) { sel = label; engine.setChoiceSel(ch.title, label) }
            Toast.makeText(ctx, if (ok) "✔ تم" else "✘ فشل أو غير مدعوم", Toast.LENGTH_SHORT).show()
        }
    }
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .06f), RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(ch.title, color = Color.White, fontWeight = FontWeight.Bold)
        if (ch.desc.isNotEmpty()) Text(ch.desc, color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val all = listOf("افتراضي" to ch.reset) + ch.opts
            for ((label, cmd) in all) {
                val on = if (label == "افتراضي") sel.isEmpty() else sel == label
                OutlinedButton(
                    enabled = !busy, onClick = { pick(if (label == "افتراضي") "" else label, cmd) },
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (on) ACCENT.copy(alpha = .4f) else Color.Transparent),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) { Text(label, fontSize = 12.sp, color = Color.White) }
            }
        }
    }
}

@Composable
fun DpiCard(engine: Engine) {
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var left by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun test(d: Int) {
        job?.cancel()
        job = scope.launch {
            withContext(Dispatchers.IO) { Shell.run("wm density $d") }
            left = 15
            while (left > 0) { delay(1000); left-- }
            withContext(Dispatchers.IO) { Shell.run("wm density reset") }
            Toast.makeText(ctx, "رجعت الكثافة الأصلية", Toast.LENGTH_SHORT).show()
        }
    }
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .06f), RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text("🔍 DPI مخصص", color = Color.White, fontWeight = FontWeight.Bold)
        Text("كل تجربة تدوم 15 ثانية وترجع وحدها إن لم تضغط «تثبيت». أقل = عناصر أصغر، أعلى = أكبر.", color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in listOf(240, 280, 320, 360, 400)) OutlinedButton(onClick = { test(d) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text("$d", fontSize = 12.sp, color = Color.White)
            }
            OutlinedButton(onClick = {
                job?.cancel(); left = 0
                scope.launch { withContext(Dispatchers.IO) { Shell.run("wm density reset") } }
            }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text("الأصلي", fontSize = 12.sp, color = Color.White) }
        }
        if (left > 0) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = { job?.cancel(); left = 0; Toast.makeText(ctx, "✔ ثُبّتت", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) {
                Text("✔ تثبيت ($left)")
            }
        }
    }
}

@Composable
fun GameWatchRow(engine: Engine) {
    var on by remember { mutableStateOf(GameWatcher.running) }
    Row(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = .06f), RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("🎯 كشف الألعاب تلقائياً", color = Color.White, fontWeight = FontWeight.Bold)
            Text("عند فتح لعبة من حزمك: عدم إزعاج، أولوية، Game Mode، قفل تدوير… وتُستعاد عند الخروج. يعمل ما دام التطبيق حياً في الخلفية.",
                color = Color.Gray, fontSize = 12.sp)
        }
        Switch(checked = on, onCheckedChange = { v ->
            on = v
            if (v) GameWatcher.start(engine) else GameWatcher.stop(engine)
        }, colors = SwitchDefaults.colors(checkedTrackColor = ACCENT))
    }
}

private val PERF_CMDS = listOf(
    "settings put global window_animation_scale 0.0",
    "settings put global animator_duration_scale 0.0",
    "settings put system rakuten_denwa 0",
    "settings put system send_security_reports 0",
    "settings put secure send_action_app_error 0",
    "settings put global activity_starts_logging_enabled 0",
    "settings put system peak_refresh_rate 120.0",
    "settings put system min_refresh_rate 120.0",
    "settings put global disable_window_blurs 1",
    "settings put global accessibility_reduce_transparency 1",
    "cmd power set-fixed-performance-mode-enabled true",
    "settings put secure long_press_timeout 250",
    "settings put secure multi_press_timeout 250",
    "settings put secure tap_duration_threshold 0.0",
    "settings put secure touch_blocking_period 0.0",
    "setprop debug.force-opengl 1",
    "settings put system multicore_packet_scheduler 1",
    "settings put global sem_enhanced_cpu_responsiveness 1",
    "settings put system k2hd_effect 1",
    "settings put system tube_amp_effect 1"
)

// كل أمر تفعيلة مستقلة: يفشل أحدها (مثل مفاتيح سامسونج) دون أن يوقف الباقي، والأوامر غير settings لها عكس
private val PERF_OPT = PERF_CMDS.map { c ->
    Tweak(c, "", listOf(c), when {
        c.startsWith("cmd power") -> listOf("cmd power set-fixed-performance-mode-enabled false")
        c.startsWith("setprop") -> listOf("setprop debug.force-opengl 0")
        else -> emptyList()
    })
}

@Composable
fun PerfOptButton(engine: Engine, onDone: () -> Unit) {
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var on by remember { mutableStateOf(PERF_OPT.indices.any { engine.isOn("perfopt-$it") }) }
    var report by remember { mutableStateOf<String?>(null) }
    val t = rememberInfiniteTransition(label = "glow")
    val g = t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "g")
    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .drawBehind {
                val pad = 6.dp.toPx()
                drawRoundRect(
                    Color(0xFF00E5FF).copy(alpha = .22f * g.value),
                    topLeft = Offset(-pad, -pad), size = Size(size.width + 2 * pad, size.height + 2 * pad),
                    cornerRadius = CornerRadius(34.dp.toPx())
                )
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Color(0xFF00E5FF).copy(alpha = g.value), Color(0xFFB388FF).copy(alpha = g.value))),
                    cornerRadius = CornerRadius(28.dp.toPx()), style = Stroke(3.dp.toPx())
                )
            }
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF14102B))
            .clickable(enabled = !busy) {
                scope.launch {
                    busy = true
                    val wasOn = on
                    val fails = mutableListOf<String>()
                    val n = withContext(Dispatchers.IO) {
                        var c = 0
                        PERF_OPT.forEachIndexed { i, tw ->
                            val id = "perfopt-$i"
                            if (!wasOn) { if (engine.apply(tw, id)) c++ else fails.add(explain(tw.on[0])) }
                            else if (engine.isOn(id) && engine.revert(tw, id)) c++
                        }
                        c
                    }
                    if (!wasOn && fails.isNotEmpty()) report = "نجح $n من ${PERF_OPT.size}\n\n" + fails.joinToString("\n\n")
                    busy = false
                    on = PERF_OPT.indices.any { engine.isOn("perfopt-$it") }
                    Toast.makeText(ctx, (if (wasOn) "♻ أُعيد " else "⚡ طُبّق ") + "$n / ${PERF_OPT.size}", Toast.LENGTH_LONG).show()
                    if (report == null) onDone()
                }
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (busy) "جارٍ التنفيذ..." else "⚡ Performance Optimization", color = Color.White,
                fontSize = 17.sp, fontWeight = FontWeight.Black,
                style = TextStyle(shadow = Shadow(Color(0xFF00E5FF), blurRadius = 16f))
            )
            Text(if (on) "ON ✔  اضغط للإيقاف والاستعادة" else "20 أمراً بضغطة واحدة", color = Color(0xFF9FD3FF), fontSize = 11.sp)
        }
    }
    report?.let { rp ->
        AlertDialog(
            onDismissRequest = { report = null; onDone() },
            confirmButton = { TextButton(onClick = { report = null; onDone() }) { Text("إغلاق") } },
            title = { Text("أوامر لم تعمل على جهازك", fontSize = 15.sp) },
            text = { Text(rp, Modifier.verticalScroll(rememberScrollState()), fontSize = 11.sp) }
        )
    }
}

/** يعيد تشغيل الأمر الفاشل ويلتقط سبب فشله (رسالة النظام + القيمة الحالية للإعداد) */
private fun explain(cmd: String): String {
    val r = Shell.run(cmd)
    val s = Regex("""settings put (\w+) (\S+) (\S+)""").find(cmd)
    val cur = s?.let { Shell.run("settings get ${it.groupValues[1]} ${it.groupValues[2]}").second }
    val why = r.second.lineSequence().firstOrNull { it.isNotBlank() }?.take(110)
        ?: if (r.first != 0) "رمز الخروج ${r.first}" else "نُفّذ لكن القيمة لم تثبت"
    return "✘ $cmd\n   ← $why" + (if (cur != null) "\n   القيمة الحالية: $cur" else "")
}
