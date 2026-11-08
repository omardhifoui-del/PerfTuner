package com.perf.tuner

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.TextUnit
import com.google.firebase.auth.UserProfileChangeRequest
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogWindowProvider
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
        getSharedPreferences("stats", 0).let { it.edit().putInt("launches", it.getInt("launches", 0) + 1).apply() }
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
                MaterialTheme(colorScheme = darkColorScheme()) { AuthGate(engine, player) }
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
    var showProfile by remember { mutableStateOf(false) }
    BackHandler(enabled = showProfile) { showProfile = false }
    val hue = rememberHue()   // لا نقرأ .value هنا حتى لا تُعاد رسم الشاشة كلها
    CompositionLocalProvider(LocalHue provides hue) {
    Box(Modifier.fillMaxSize()) {
    AppBackground()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Header(hue, player) { showProfile = true }
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
    AnimatedVisibility(
        visible = showProfile,
        enter = slideInVertically(tween(420, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))) { it / 2 } + fadeIn(tween(300)),
        exit = slideOutVertically(tween(300)) { it / 2 } + fadeOut(tween(200))
    ) { ProfileScreen(engine) { showProfile = false } }
    }
    }
}

@Composable
fun Header(hue: State<Float>, player: MediaPlayer?, onProfile: () -> Unit) {
    var music by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("⚡ PERF TUNER", Modifier.weight(1f),
            style = TextStyle(brush = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFB388FF))), fontSize = 24.sp, fontWeight = FontWeight.Black))
        TextButton(onClick = onProfile) { Text("👤", fontSize = 22.sp) }
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
    val pos = animateFloatAsState(if (sel in 0..4) sel.toFloat() else 0f, spring(dampingRatio = 0.6f, stiffness = 360f), label = "lens")
    val show = animateFloatAsState(if (sel in 0..4) 1f else 0f, tween(220), label = "lensShow")
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().height(66.dp)
            .glass(33.dp, hue)
            .drawBehind {
                val w = size.width / 5f
                val rtl = layoutDirection == LayoutDirection.Rtl
                val x = if (rtl) size.width - (pos.value + 1f) * w else pos.value * w
                val pad = 5.dp.toPx(); val s = show.value
                val tl = Offset(x + pad, pad); val sz = Size(w - 2 * pad, size.height - 2 * pad)
                val r = CornerRadius(28.dp.toPx())
                drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .40f * s), Color.White.copy(alpha = .12f * s))), tl, sz, r)
                drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .95f * s), Color.White.copy(alpha = .15f * s), Color.White.copy(alpha = .6f * s))), tl, sz, r, style = Stroke(1.6.dp.toPx()))
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0..4) NavItem(i, sel == i, Modifier.weight(1f)) { onSel(i) }
    }
}

@Composable
fun NavItem(i: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val sc = animateFloatAsState(if (selected) 1.2f else 1f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "navScale")
    Column(
        modifier.fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text(CATS[i].substringBefore(' '), fontSize = 22.sp, modifier = Modifier.graphicsLayer { scaleX = sc.value; scaleY = sc.value })
        Text(CATS[i].substringAfter(' '), fontSize = 10.sp, color = if (selected) Color.White else Color(0xFFB8B3D9),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, c: Color, m: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        m.then(if (selected) Modifier.glass(14.dp, LocalHue.current) else Modifier.glass(14.dp))
            .clip(shape).clickable { onClick() }.padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text.substringAfter(' '), fontSize = 11.sp, color = Color.White,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
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
            .glass(18.dp, hue)
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
        Modifier.weight(1f).glass(12.dp).padding(8.dp),
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
        item { ThermalBoostButton(engine, onBoost) }
        item { BoostBar(engine, onBoost) }
        item { Text(CATS[page], color = ACCENT, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        if (page == 9) item { AccountCard(engine, onBoost) }
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
        Modifier.fillMaxWidth().glass(14.dp).padding(12.dp),
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
        GlassAlert(
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
        GlassButton(if (busy) "جارٍ التنفيذ..." else "🔥 ULTRA PERFORMANCE", Modifier.fillMaxWidth(), !busy) { run(1) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GlassButton("🚀 آمن", Modifier.weight(1f), !busy) { run(0) }
            GlassButton("🧠 RAM", Modifier.weight(1f), !busy) { run(3) }
            GlassButton("🧹 كاش", Modifier.weight(1f), !busy) { run(4) }
            GlassButton("♻ استعادة", Modifier.weight(1f), !busy) { run(2) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GlassButton("🎛 ملفات وتحليل", Modifier.weight(1f), !busy) { showProfiles = true }
            GlassButton("📊 RAM Booster", Modifier.weight(1f), !busy) { showRam = true }
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
    GlassAlert(
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
    GlassAlert(
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
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.70f), Color.Black.copy(alpha = 0.82f)))))
        }
        val hue = LocalHue.current
        Box(Modifier.fillMaxSize().drawBehind {
            val h = hue.value
            val w = size.width; val ht = size.height
            fun blob(c: Color, x: Float, y: Float, r: Float) =
                drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(x, y), radius = r), radius = r, center = Offset(x, y))
            blob(hc(h).copy(alpha = .30f), w * .15f, ht * .15f, w * .8f)
            blob(hc(h, 140f).copy(alpha = .26f), w * .9f, ht * .55f, w * .85f)
            blob(hc(h, 260f).copy(alpha = .24f), w * .3f, ht * .98f, w * .8f)
        })
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
    Column(Modifier.fillMaxWidth().glass(14.dp).padding(12.dp)) {
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
    Column(Modifier.fillMaxWidth().glass(14.dp).padding(12.dp)) {
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
        Modifier.fillMaxWidth().glass(14.dp).padding(12.dp),
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
    "settings put system tube_amp_effect 1",
    "settings put system max_events_per_second 300",
    "settings put system max_pointer_events_per_second 300",
    "settings put system touch_pressure_scale 0.55",
    "settings put system touch_distance_scale 0.40",
    "settings put system touch_filter_enable 1",
    "settings put system touch_noise_suppression 1",
    "settings put system touch_jitter_suppression 1",
    "settings put system touch_filter_deadzone_radius 0.1",
    "settings put system touch_area_filtering_enable 1",
    "settings put global force_gpu_rendering 1",
    "settings put secure game_mode 1",
    "setprop persist.sys.game.mode 1",
    "settings put system min_refresh_rate 120.0",
    "settings put system peak_refresh_rate 120.0",
    "settings put system user_refresh_rate 120.0",
    "setprop persist.sys.touch.sampling 1",
    "setprop debug.touch.sampling 1",
    "settings put secure speed_mode_enable 1",
    "settings put system hw3d.force 1",
    "settings put system hwui.disable_vsync true",
    "settings put system hwui.render_dirty_regions false",
    "settings put system persist.sys.composition.type gpu",
    "settings put system persist.sys.ui.hw 1",
    "settings put system ro.config.enable.hw_accel true",
    "settings put system video.accelerate.hw 1",
    "settings put system game.accelerate.hw 1",
    "setprop debug.composition.type gpu",
    "setprop debug.performance.tuning 1",
    "setprop debug.performance.accoustic.force true",
    "setprop debug.javafx.animation.fullspeed true",
    "setprop debug.cpurend.vsync false",
    "setprop debug.gpurend.vsync false",
    "setprop debug.rs.max-threads 8",
    "setprop debug.rs.min-perf_percent 100",
    "setprop debug.sf.disable_backpressure 1",
    "setprop debug.sf.latch_unsignaled 1",
    "setprop debug.sf.enable_hwc_vds 1",
    "setprop debug.sf.hw 0",
    "setprop debug.hwui.fps_divisor -1",
    "setprop debug.systemuicompilerfilter speed",
    "setprop debug.sf.scroll_boost_refreshrate 1",
    "settings put global development_settings_enabled 1",
    "settings put global animator_duration_scale 0.5",
    "settings put global transition_animation_scale 0.5",
    "settings put global window_animation_scale 0.5",
    "settings put system hw3d.force 1",
    "settings put system hwui.disable_vsync false",
    "settings put system persist.sys.ui.hw 1",
    "settings put system video.accelerate.hw 1",
    "settings put system ro.config.enable.hw_accel true",
    "setprop debug.composition.type gpu",
    "setprop debug.performance.tuning 1",
    "setprop debug.sf.latch_unsignaled 1",
    "setprop debug.sf.enable_hwc_vds 1",
    "setprop debug.egl.hw 1",
    "cmd device_config put activity_manager sf.high_fps_early_phase_offset_ns 0",
    "cmd device_config put activity_manager sf.high_fps_early_gl_phase_offset_ns 0",
    "settings put system thermal_limit_refresh_rate 10"
)

// كل أمر تفعيلة مستقلة: يفشل أحدها (مثل مفاتيح سامسونج) دون أن يوقف الباقي، والأوامر غير settings لها عكس
private val PERF_OPT = PERF_CMDS.map { c ->
    val sp = Regex("""setprop (\S+) .*""").find(c)
    val dc = Regex("""cmd device_config put (\S+) (\S+) .*""").find(c)
    Tweak(c, "", listOf(c), when {
        c.startsWith("cmd power") -> listOf("cmd power set-fixed-performance-mode-enabled false")
        sp != null -> listOf("setprop ${sp.groupValues[1]} \"\"")
        dc != null -> listOf("cmd device_config delete ${dc.groupValues[1]} ${dc.groupValues[2]}")
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
            .background(Brush.verticalGradient(listOf(Color(0xFF2A2350).copy(alpha = .75f), Color(0xFF14102B).copy(alpha = .6f))))
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
            Text(if (on) "ON ✔  اضغط للإيقاف والاستعادة" else "${PERF_OPT.size} أمراً بضغطة واحدة", color = Color(0xFF9FD3FF), fontSize = 11.sp)
        }
    }
    report?.let { rp ->
        GlassAlert(
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

// ───────────── Liquid Glass ─────────────
val LocalHue = staticCompositionLocalOf<State<Float>> { mutableStateOf(0f) }

/** سطح زجاجي: جسم شفاف متدرج + لمعة علوية + حافة مضيئة. مع hue تتلوّن الحافة بالـ RGB (تُقرأ في مرحلة الرسم فقط) */
fun Modifier.glass(radius: Dp = 16.dp, hue: State<Float>? = null): Modifier = this.drawBehind {
    val r = CornerRadius(radius.toPx())
    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .20f), Color.White.copy(alpha = .06f), Color.White.copy(alpha = .12f))), cornerRadius = r)
    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .26f), Color.Transparent), endY = size.height * .45f), cornerRadius = r)
    val h = hue?.value
    val rim = if (h != null) Brush.linearGradient(listOf(hc(h).copy(alpha = .95f), Color.White.copy(alpha = .25f), hc(h, 180f).copy(alpha = .85f)))
              else Brush.linearGradient(listOf(Color.White.copy(alpha = .65f), Color.White.copy(alpha = .08f), Color.White.copy(alpha = .35f)))
    val i = .75.dp.toPx()
    drawRoundRect(rim, topLeft = Offset(i, i), size = Size(size.width - 2 * i, size.height - 2 * i), cornerRadius = r, style = Stroke(1.5.dp.toPx()))
}

@Composable
fun GlassButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.height(44.dp).glass(22.dp, LocalHue.current).clip(RoundedCornerShape(22.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (enabled) Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
}

/** تغبيش ما خلف النافذة (أندرويد 12+). لا يعمل إذا كان disable_window_blurs مفعّلاً */
@Composable
fun DialogBlur() {
    val view = LocalView.current
    SideEffect {
        val w = ((view as? DialogWindowProvider) ?: (view.parent as? DialogWindowProvider))?.window
        if (w != null && Build.VERSION.SDK_INT >= 31) {
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            w.attributes = w.attributes.also { it.blurBehindRadius = 40 }
        }
    }
}

@Composable
fun GlassAlert(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest, confirmButton = confirmButton, dismissButton = dismissButton, title = title,
        text = { DialogBlur(); text?.invoke() },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFF1B1738).copy(alpha = 0.74f),
        titleContentColor = Color.White, textContentColor = Color.White, tonalElevation = 0.dp
    )
}

// ───────────── Thermal Boost ─────────────
private val THERMAL_SET_TWEAKS = (THERMAL_SETTINGS + THERMAL_DRIVER_CMD).map { Tweak(it, "", listOf(it)) }

/** يراقب حرارة البطارية كل 10 ثوانٍ؛ عند الوصول للحد يلغي التجاوز تلقائياً (يعمل ما دام التطبيق حياً) */
private object ThermalGuard {
    @Volatile var running = false
    private var job: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    fun start(ctx: Context, limitC: Float = 44f, onTrip: (Float) -> Unit) {
        if (running) return
        running = true
        job = scope.launch {
            while (running) {
                val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val t = (b?.getIntExtra("temperature", 0) ?: 0) / 10f
                if (t >= limitC) {
                    Shell.run("cmd thermalservice reset")
                    Shell.run("cmd power set-fixed-performance-mode-enabled false")
                    running = false
                    onTrip(t)
                    break
                }
                delay(10_000)
            }
        }
    }

    fun stop() { running = false; job?.cancel() }
}

private fun thermalApply(ctx: Context, engine: Engine, progress: (String) -> Unit): String {
    val sp = ctx.getSharedPreferences("perf", 0)
    val rep = StringBuilder()
    val res = Shell.run("wm size | grep -o '[0-9]*x[0-9]*' | head -n1").second.ifBlank { "720x1640" }
    val props = THERMAL_PROPS.map { it.first to it.second.replace("{RES}", res) }

    progress("حفظ القيم الأصلية…")
    val snap = Shell.run(props.joinToString("; ") { "echo \"${it.first}=\$(getprop ${it.first})\"" }).second
    sp.edit().putString("thermal_orig", snap).apply()

    progress("إغلاق التطبيقات الخلفية…")
    Shell.run("cmd activity kill-all")

    progress("تجاوز Thermal Service…")
    val thermal = Shell.run("cmd thermalservice override-status 0").first == 0
    val perf = Shell.run("cmd power set-fixed-performance-mode-enabled true").first == 0

    progress("تطبيق ${props.size} خاصية…")
    val out = Shell.run(props.joinToString("; ") { "setprop ${it.first} ${it.second} >/dev/null 2>&1 && echo ok || echo \"fail ${it.first}\"" }).second
    val okProps = out.lines().count { it.trim() == "ok" }

    progress("تطبيق إعدادات النظام…")
    var okSet = 0
    THERMAL_SET_TWEAKS.forEachIndexed { i, tw -> if (engine.apply(tw, "thermal-s$i")) okSet++ }

    progress("وضع الأداء للألعاب…")
    var okModes = 0
    for (p in THERMAL_MODE_PKGS) if (Shell.run("cmd game mode performance $p").first == 0) okModes++

    var compiled = 0
    for (p in THERMAL_COMPILE_PKGS) {
        progress("تجميع $p …")
        if (Shell.run("cmd package compile -m everything -f $p").second.contains("Success")) compiled++
    }

    sp.edit().putBoolean("thermal_on", true).apply()
    rep.append("🌡 تجاوز Thermal Service: ").append(if (thermal) "✔" else "✘ (غير مدعوم)").append('\n')
    rep.append("⚡ الأداء الثابت: ").append(if (perf) "✔" else "✘ (غير مدعوم)").append('\n')
    rep.append("🧩 خصائص setprop: $okProps / ${props.size}\n")
    rep.append("⚙ إعدادات النظام: $okSet / ${THERMAL_SET_TWEAKS.size}\n")
    rep.append("🎮 Game Mode: $okModes / ${THERMAL_MODE_PKGS.size} (المثبّت فقط)\n")
    rep.append("🧪 ألعاب جُمّعت: $compiled / ${THERMAL_COMPILE_PKGS.size} (المثبّت فقط)\n\n")
    rep.append("حارس الحرارة فعّال: يلغي التجاوز تلقائياً عند 44°م.\n")
    rep.append("ملاحظة: معظم خصائص debug.* لا يقرؤها النظام فلا تغيّر شيئاً. أثر هذا الزر الحقيقي في تجاوز Thermal Service والأداء الثابت وGame Mode والتجميع.")
    return rep.toString()
}

private fun thermalRevert(ctx: Context, engine: Engine) {
    val sp = ctx.getSharedPreferences("perf", 0)
    ThermalGuard.stop()
    Shell.run("cmd thermalservice reset")
    Shell.run("cmd power set-fixed-performance-mode-enabled false")
    val orig = (sp.getString("thermal_orig", "") ?: "").lines().mapNotNull { l ->
        val i = l.indexOf('=')
        if (i > 0) l.substring(0, i) to l.substring(i + 1) else null
    }
    if (orig.isNotEmpty()) Shell.run(orig.joinToString("; ") { "setprop ${it.first} \"${it.second}\" >/dev/null 2>&1" })
    THERMAL_SET_TWEAKS.forEachIndexed { i, tw -> engine.revert(tw, "thermal-s$i") }
    Shell.run(THERMAL_MODE_PKGS.joinToString("; ") { "cmd game mode standard $it" })
    sp.edit().putBoolean("thermal_on", false).apply()
}

@Composable
fun ThermalBoostButton(engine: Engine, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sp = remember { ctx.getSharedPreferences("perf", 0) }
    var on by remember { mutableStateOf(sp.getBoolean("thermal_on", false)) }
    var busy by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<String?>(null) }

    fun startGuard() = ThermalGuard.start(ctx) { temp ->
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            on = false
            sp.edit().putBoolean("thermal_on", false).apply()
            Toast.makeText(ctx, "🛑 حارس الحرارة ألغى Thermal Boost عند ${temp}°م", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(Unit) { if (on && !ThermalGuard.running) startGuard() }

    val t = rememberInfiniteTransition(label = "fire")
    val phase = t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "fp")
    val pulse = t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "fpp")
    val fire = listOf(Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00))

    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp)) {
        Box(
            Modifier.fillMaxWidth().height(64.dp)
                .drawBehind {
                    val r = CornerRadius(30.dp.toPx())
                    val brush = Brush.linearGradient(fire, start = Offset(0f, 0f), end = Offset(size.width, size.height))
                    for (i in 5 downTo 1) drawRoundRect(brush, cornerRadius = r, style = Stroke((i * 5).dp.toPx()), alpha = pulse.value * 0.08f)
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF3A1208).copy(alpha = .80f), Color(0xFF14102B).copy(alpha = .65f))), cornerRadius = r)
                    val x = size.width * (phase.value * 1.6f - 0.3f)
                    drawRoundRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .22f), Color.Transparent),
                        startX = x - size.width * .25f, endX = x + size.width * .25f), cornerRadius = r)
                    drawRoundRect(brush, cornerRadius = r, style = Stroke(2.5.dp.toPx()))
                }
                .clip(RoundedCornerShape(30.dp))
                .clickable(enabled = !busy) {
                    scope.launch {
                        busy = true
                        if (!on) {
                            val rep = withContext(Dispatchers.IO) { thermalApply(ctx, engine) { s -> stage = s } }
                            on = true; report = rep
                            startGuard()
                        } else {
                            withContext(Dispatchers.IO) { thermalRevert(ctx, engine) }
                            on = false
                            Toast.makeText(ctx, "♻ أُلغي Thermal Boost وعادت الإعدادات", Toast.LENGTH_LONG).show()
                        }
                        busy = false
                        if (report == null) onDone()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (busy) "..." else "🔥 THERMAL BOOST", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(
                    if (busy) stage else if (on) "ON ✔  حارس 44°م فعّال — اضغط للإيقاف" else "تجاوز تقييد الحرارة + أداء ثابت + تحسين الألعاب",
                    color = Color(0xFFFFD6A5), fontSize = 11.sp, maxLines = 1
                )
            }
        }
    }
    report?.let { rp ->
        GlassAlert(
            onDismissRequest = { report = null; onDone() },
            confirmButton = { TextButton(onClick = { report = null; onDone() }) { Text("إغلاق") } },
            title = { Text("نتيجة Thermal Boost", fontSize = 15.sp) },
            text = { Text(rp, Modifier.verticalScroll(rememberScrollState()), fontSize = 12.sp) }
        )
    }
}

// ───────────── الحساب (Firebase) ─────────────
@Composable
fun AuthGate(engine: Engine, player: MediaPlayer?) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("auth", 0) }
    val firebaseOk = remember { try { FirebaseApp.getApps(ctx).isNotEmpty() } catch (e: Throwable) { false } }
    var skipped by remember { mutableStateOf(sp.getBoolean("skip", false)) }
    var user by remember { mutableStateOf(if (firebaseOk) FirebaseAuth.getInstance().currentUser else null) }
    if (!firebaseOk || user != null || skipped) App(engine, player)
    else AuthScreen(
        onUser = { user = FirebaseAuth.getInstance().currentUser },
        onSkip = { sp.edit().putBoolean("skip", true).apply(); skipped = true }
    )
}

@Composable
fun AuthScreen(onUser: () -> Unit, onSkip: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val auth = remember { FirebaseAuth.getInstance() }
    fun check(): Boolean {
        if (email.isBlank() || pass.isBlank()) { msg = "اكتب البريد وكلمة المرور"; return false }
        return true
    }
    fun login() {
        if (!check()) return
        busy = true; msg = null
        auth.signInWithEmailAndPassword(email.trim(), pass).addOnCompleteListener { t ->
            busy = false
            if (t.isSuccessful) onUser() else msg = Cloud.errorText(t.exception)
        }
    }
    fun register() {
        if (!check()) return
        busy = true; msg = null
        auth.createUserWithEmailAndPassword(email.trim(), pass).addOnCompleteListener { t ->
            busy = false
            if (t.isSuccessful) onUser() else msg = Cloud.errorText(t.exception)
        }
    }
    fun reset() {
        if (email.isBlank()) { msg = "اكتب بريدك أولاً ثم اضغط «نسيت كلمة المرور»"; return }
        auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener { t ->
            msg = if (t.isSuccessful) "أُرسل رابط استعادة كلمة المرور إلى بريدك" else Cloud.errorText(t.exception)
        }
    }
    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("⚡ PERF TUNER", style = TextStyle(brush = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFB388FF))),
                fontSize = 30.sp, fontWeight = FontWeight.Black))
            Spacer(Modifier.height(10.dp))
            Text("سجّل الدخول لتبقى إعداداتك محفوظة في حسابك", color = Color(0xFFB8B3D9), fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth().glass(24.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("البريد الإلكتروني") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("كلمة المرور") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                msg?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 12.sp) }
                GlassButton(if (busy) "..." else "تسجيل الدخول", Modifier.fillMaxWidth(), !busy) { login() }
                GlassButton("إنشاء حساب جديد", Modifier.fillMaxWidth(), !busy) { register() }
                TextButton(onClick = { reset() }) { Text("نسيت كلمة المرور؟") }
            }
            TextButton(onClick = onSkip) { Text("المتابعة بدون حساب", color = Color.Gray) }
        }
    }
}

@Composable
fun AccountCard(engine: Engine, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = remember { try { FirebaseAuth.getInstance().currentUser } catch (e: Throwable) { null } }
    var snap by remember { mutableStateOf<Cloud.Snapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()
    fun leave() {
        ctx.getSharedPreferences("auth", 0).edit().putBoolean("skip", false).apply()
        (ctx as? Activity)?.recreate()
    }
    Column(Modifier.fillMaxWidth().glass(16.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("👤 الحساب", color = Color.White, fontWeight = FontWeight.Bold)
        if (user == null) {
            Text("تستخدم التطبيق بدون حساب؛ إعداداتك تبقى على هذا الهاتف فقط.", color = Color.Gray, fontSize = 12.sp)
            GlassButton("تسجيل الدخول / إنشاء حساب", Modifier.fillMaxWidth()) { leave() }
        } else {
            Text(user.email ?: "", color = Color(0xFF9FD3FF), fontSize = 13.sp)
            Text("يُحفظ في حسابك: الألعاب المضافة، والتفعيلات التي شغّلتها، وخياراتك. القيم الأصلية لكل هاتف لا تُرفع.",
                color = Color.Gray, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassButton("☁ حفظ", Modifier.weight(1f), !busy) {
                    busy = true; Cloud.save(engine) { busy = false; toast(it) }
                }
                GlassButton("⬇ استرجاع", Modifier.weight(1f), !busy) {
                    busy = true
                    Cloud.load { s, m -> busy = false; if (s == null) toast(m) else snap = s }
                }
                GlassButton("خروج", Modifier.weight(1f), !busy) { FirebaseAuth.getInstance().signOut(); leave() }
            }
        }
    }
    snap?.let { s ->
        GlassAlert(
            onDismissRequest = { snap = null },
            confirmButton = {
                Button(onClick = {
                    snap = null
                    scope.launch {
                        busy = true
                        val n = withContext(Dispatchers.IO) { Cloud.restore(engine, s) }
                        busy = false; toast("⬇ طُبّق $n على هذا الهاتف"); onDone()
                    }
                }) { Text("تطبيق") }
            },
            dismissButton = { TextButton(onClick = { snap = null }) { Text("إلغاء") } },
            title = { Text("استرجاع الإعدادات", fontSize = 15.sp) },
            text = { Text("سيطبّق التطبيق ${s.on.size} تفعيلة و${s.choices.size} خيارات و${s.pkgs.split(',').count { it.isNotBlank() }} لعبة على هاتفك الآن.") }
        )
    }
}

// ───────────── الملف الشخصي ─────────────
private val RANKS = listOf(0 to "🌱 مبتدئ", 100 to "⚡ متقدّم", 250 to "🔥 محترف", 500 to "💎 خبير", 900 to "👑 أسطورة")

private fun fmtTime(ms: Long?): String =
    if (ms == null || ms <= 0L) "—" else java.text.SimpleDateFormat("yyyy-MM-dd  HH:mm", java.util.Locale.US).format(java.util.Date(ms))

@Composable
fun RgbText(text: String, hue: State<Float>, size: TextUnit) {
    val c = remember { derivedStateOf { hc((hue.value / 6f).toInt() * 6f) } }
    Text(text, color = c.value, fontSize = size, fontWeight = FontWeight.Black, maxLines = 1)
}

@Composable
fun Avatar(letter: String, hue: State<Float>) {
    Box(
        Modifier.size(124.dp).drawBehind {
            val h = hue.value
            val r = size.minDimension / 2
            for (i in 4 downTo 1) drawCircle(hc(h).copy(alpha = .07f * i), radius = r + (i * 4).dp.toPx())
            val colors = List(7) { hc(h, it * 60f) }
            rotate(h) { drawCircle(Brush.sweepGradient(colors + colors.first()), radius = r - 4.dp.toPx(), style = Stroke(6.dp.toPx())) }
            drawCircle(Color(0xFF14102B), radius = r - 10.dp.toPx())
        },
        contentAlignment = Alignment.Center
    ) { Text(letter, fontSize = 54.sp, fontWeight = FontWeight.Black, color = Color.White) }
}

@Composable
fun ProfileBadge(text: String) {
    Box(Modifier.glass(14.dp).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun InfoCard(title: String, rows: List<Pair<String, String>>, hue: State<Float>? = null) {
    Column(Modifier.fillMaxWidth().glass(20.dp, hue).padding(14.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        rows.forEachIndexed { i, (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k, color = Color(0xFFB8B3D9), fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(v, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            }
            if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(.5.dp).background(Color.White.copy(alpha = .12f)))
        }
    }
}

@Composable
fun ProfileScreen(engine: Engine, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val hue = LocalHue.current
    val st = remember { ctx.getSharedPreferences("stats", 0) }
    val user = remember { try { FirebaseAuth.getInstance().currentUser } catch (e: Throwable) { null } }
    var name by remember { mutableStateOf(user?.displayName?.takeIf { it.isNotBlank() } ?: user?.email?.substringBefore('@') ?: "ضيف") }
    var nick by remember { mutableStateOf(user?.displayName ?: "") }
    var busy by remember { mutableStateOf(false) }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()
    fun leave() {
        ctx.getSharedPreferences("auth", 0).edit().putBoolean("skip", false).apply()
        (ctx as? Activity)?.recreate()
    }

    // أرقام الاستخدام والرتبة
    val total = remember { TWEAKS.values.sumOf { it.size } }
    val onN = remember { engine.onIds().size }
    val perfOn = remember { PERF_OPT.indices.any { engine.isOn("perfopt-$it") } }
    val thermalOn = remember { ctx.getSharedPreferences("perf", 0).getBoolean("thermal_on", false) }
    val launches = st.getInt("launches", 0)
    val saves = st.getInt("saves", 0)
    val xp = onN * 10 + launches * 3 + saves * 25 + (if (perfOn) 40 else 0) + (if (thermalOn) 40 else 0)
    val rankIdx = RANKS.indexOfLast { xp >= it.first }
    val nextXp = RANKS.getOrNull(rankIdx + 1)?.first
    val frac = if (nextXp == null) 1f else (xp - RANKS[rankIdx].first).toFloat() / (nextXp - RANKS[rankIdx].first)

    // معلومات الجهاز
    val pkgInfo = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0) } catch (e: Throwable) { null } }
    val days = pkgInfo?.let { ((System.currentTimeMillis() - it.firstInstallTime) / 86_400_000L).toInt() } ?: 0
    val shz = remember { try { if (rikka.shizuku.Shizuku.pingBinder()) "متصل • v${rikka.shizuku.Shizuku.getVersion()}" else "غير متصل" } catch (e: Throwable) { "غير متاح" } }
    val mi = remember { ActivityManager.MemoryInfo().also { (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) } }
    val sf = remember { StatFs(Environment.getDataDirectory().path) }
    val bat = remember { ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
    val temp = (bat?.getIntExtra("temperature", 0) ?: 0) / 10f
    val level = bat?.getIntExtra("level", 0) ?: 0
    @Suppress("DEPRECATION")
    val hz = remember { (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate }
    val gb = 1073741824.0

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B18))) {
        AppBackground()
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("الملف الشخصي", Modifier.weight(1f), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                GlassButton("✕", Modifier.width(52.dp)) { onClose() }
            }

            // البطاقة الرئيسية
            Column(Modifier.fillMaxWidth().glow(28.dp, hue).glass(28.dp, hue).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar((name.firstOrNull() ?: '?').uppercaseChar().toString(), hue)
                Spacer(Modifier.height(14.dp))
                RgbText(name, hue, 26.sp)
                Text(user?.email ?: "حساب ضيف — إعداداتك على هذا الهاتف فقط", color = Color(0xFF9FD3FF), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileBadge(RANKS[rankIdx].second)
                    ProfileBadge(if (user == null) "👤 ضيف" else if (user.isEmailVerified) "✔ بريد موثّق" else "✉ بريد غير موثّق")
                }
                Spacer(Modifier.height(14.dp))
                Text("XP $xp" + (nextXp?.let { "  /  $it" } ?: "  •  أعلى رتبة"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = .15f))) {
                    Box(Modifier.fillMaxWidth(frac.coerceIn(0.03f, 1f)).fillMaxHeight().drawBehind {
                        drawRect(Brush.horizontalGradient(List(5) { hc(hue.value, it * 70f) }))
                    })
                }
                Text("الرتبة تعتمد على استخدامك للتطبيق (تفعيلات وحفظ سحابي)، وليست مقياساً لأداء الهاتف.", color = Color.Gray, fontSize = 10.sp)
            }

            // أرقام
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("ACTIVE", "$onN/$total", onN.toFloat() / total, hc(hue.value))
                Tile("GAMES", "${engine.pkgs().size}", null, ACCENT)
                Tile("LAUNCHES", "$launches", null, ACCENT)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("DAYS", "$days", null, ACCENT)
                Tile("CLOUD SAVES", "$saves", null, ACCENT)
                Tile("RANK", "${rankIdx + 1}/${RANKS.size}", (rankIdx + 1f) / RANKS.size, hc(hue.value, 120f))
            }

            // الإنجازات
            AchievementsCard(
                listOf(
                    Triple("🔗", "Shizuku متصل", shz.startsWith("متصل")),
                    Triple("🚀", "أول تفعيل", onN >= 1),
                    Triple("🎛", "10 تفعيلات", onN >= 10),
                    Triple("🧙", "30 تفعيلة", onN >= 30),
                    Triple("⚡", "Performance ON", perfOn),
                    Triple("🔥", "Thermal ON", thermalOn),
                    Triple("🎮", "صائد الألعاب", engine.pkgs().isNotEmpty()),
                    Triple("☁", "أول حفظ سحابي", saves >= 1),
                    Triple("✔", "بريد موثّق", user?.isEmailVerified == true),
                    Triple("📅", "أسبوع معنا", days >= 7),
                    Triple("🔁", "20 فتحة", launches >= 20),
                    Triple("👑", "رتبة أسطورة", rankIdx == RANKS.lastIndex)
                ),
                hue
            )

            // الحساب
            InfoCard(
                "🔐 معلومات الحساب",
                listOf(
                    "الاسم" to name,
                    "البريد" to (user?.email ?: "—"),
                    "المعرّف (UID)" to (user?.uid?.let { it.take(8) + "…" } ?: "—"),
                    "إنشاء الحساب" to fmtTime(user?.metadata?.creationTimestamp),
                    "آخر دخول" to fmtTime(user?.metadata?.lastSignInTimestamp),
                    "طريقة الدخول" to (user?.providerData?.lastOrNull()?.providerId?.let { if (it == "password") "بريد وكلمة مرور" else it } ?: "—"),
                    "توثيق البريد" to (if (user == null) "—" else if (user.isEmailVerified) "موثّق ✔" else "غير موثّق"),
                    "آخر حفظ سحابي" to fmtTime(st.getLong("last_save", 0L))
                ),
                hue
            )

            if (user != null) {
                Column(Modifier.fillMaxWidth().glass(20.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✏ إدارة الحساب", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    OutlinedTextField(
                        value = nick, onValueChange = { nick = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("اسم العرض") }
                    )
                    GlassButton("حفظ الاسم", Modifier.fillMaxWidth(), !busy && nick.isNotBlank()) {
                        busy = true
                        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(nick.trim()).build()).addOnCompleteListener { t ->
                            busy = false
                            if (t.isSuccessful) { name = nick.trim(); toast("✔ حُفظ الاسم") } else toast("✘ " + Cloud.errorText(t.exception))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassButton("📋 نسخ المعرّف", Modifier.weight(1f)) {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("uid", user.uid))
                            toast("نُسخ المعرّف")
                        }
                        GlassButton("☁ حفظ", Modifier.weight(1f), !busy) {
                            busy = true; Cloud.save(engine) { busy = false; toast(it) }
                        }
                    }
                    if (!user.isEmailVerified) GlassButton("✉ إرسال رابط توثيق البريد", Modifier.fillMaxWidth(), !busy) {
                        busy = true
                        user.sendEmailVerification().addOnCompleteListener { t ->
                            busy = false
                            toast(if (t.isSuccessful) "أُرسل الرابط إلى بريدك" else "✘ " + Cloud.errorText(t.exception))
                        }
                    }
                    GlassButton("تسجيل الخروج", Modifier.fillMaxWidth(), !busy) { FirebaseAuth.getInstance().signOut(); leave() }
                }
            } else {
                GlassButton("تسجيل الدخول / إنشاء حساب", Modifier.fillMaxWidth()) { leave() }
            }

            // الجهاز والتطبيق
            InfoCard(
                "📱 الجهاز والنظام",
                listOf(
                    "الجهاز" to "${Build.MANUFACTURER} ${Build.MODEL}".uppercase(),
                    "أندرويد" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    "المعالج" to "${Build.HARDWARE} • ${Runtime.getRuntime().availableProcessors()} أنوية",
                    "الذاكرة" to "${f1(mi.totalMem / gb)} GB (متاح ${f1(mi.availMem / gb)})",
                    "التخزين" to "${f1(sf.totalBytes / gb)} GB (حر ${f1(sf.availableBytes / gb)})",
                    "معدل التحديث الحالي" to "${f1(hz.toDouble())} Hz",
                    "البطارية" to "$level% • ${temp}°C",
                    "الدقة" to ctx.resources.displayMetrics.let { "${it.widthPixels}x${it.heightPixels}" },
                    "تحديث الأمان" to (Build.VERSION.SECURITY_PATCH ?: "—"),
                    "النواة" to (System.getProperty("os.version") ?: "—"),
                    "مدة تشغيل الهاتف" to "${android.os.SystemClock.elapsedRealtime() / 3_600_000} ساعة",
                    "اللغة" to java.util.Locale.getDefault().displayLanguage,
                    "Shizuku" to shz
                ),
                hue
            )
            InfoCard(
                "⚙ التطبيق",
                listOf(
                    "الإصدار" to (pkgInfo?.versionName ?: "—"),
                    "الأيام منذ التثبيت" to "$days",
                    "عدد مرات الفتح" to "$launches",
                    "عدد التفعيلات الكلي" to "$total",
                    "التفعيلات النشطة" to "$onN",
                    "Performance Optimization" to (if (perfOn) "ON ✔" else "OFF"),
                    "Thermal Boost" to (if (thermalOn) "ON ✔" else "OFF")
                )
            )
            Signature()
        }
    }
}

/** توهج RGB نابض حول البطاقة (يُقرأ hue في مرحلة الرسم فقط) */
fun Modifier.glow(radius: Dp, hue: State<Float>): Modifier = this.drawBehind {
    val r = CornerRadius(radius.toPx())
    val h = hue.value
    val brush = Brush.linearGradient(listOf(hc(h), hc(h, 120f), hc(h, 240f)))
    for (i in 6 downTo 1) drawRoundRect(brush, cornerRadius = r, style = Stroke((i * 5).dp.toPx()), alpha = 0.05f)
}

@Composable
fun AchievementsCard(items: List<Triple<String, String, Boolean>>, hue: State<Float>) {
    val got = items.count { it.third }
    Column(Modifier.fillMaxWidth().glass(20.dp, hue).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🏆 الإنجازات  $got/${items.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (icon, title, on) ->
                    Column(
                        Modifier.weight(1f).then(if (on) Modifier.glass(14.dp, hue) else Modifier.glass(14.dp)).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(if (on) icon else "🔒", fontSize = 24.sp, modifier = Modifier.graphicsLayer { alpha = if (on) 1f else .45f })
                        Text(title, color = if (on) Color.White else Color(0xFF8E89B0), fontSize = 10.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 2)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
