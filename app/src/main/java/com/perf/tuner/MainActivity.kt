package com.perf.tuner

import android.app.ActivityManager
import android.content.Context
import android.media.MediaPlayer
import android.os.Build
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val h = t.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "h")
    return remember { derivedStateOf { (h.value / 3f).toInt() * 3f } }
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
        BoostBar(engine) { ver++ }
        TopTabs(sel, hue) { sel = it }
        AnimatedContent(
            targetState = sel, modifier = Modifier.weight(1f), label = "page",
            transitionSpec = {
                val d = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(260)) { it / 4 * d } + fadeIn(tween(260)))
                    .togetherWith(slideOutHorizontally(tween(200)) { -it / 4 * d } + fadeOut(tween(160)))
            }
        ) { page -> key(ver) { Page(page, engine) } }
        BottomNav(sel, hue) { sel = it }
    }
    }
}

@Composable
fun Header(hue: State<Float>, player: MediaPlayer?) {
    var music by remember { mutableStateOf(false) }
    val h = hue.value
    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("⚡ PERF TUNER", Modifier.weight(1f),
            style = TextStyle(brush = Brush.linearGradient(listOf(hc(h), hc(h, 120f))), fontSize = 24.sp, fontWeight = FontWeight.Black))
        if (player != null) TextButton(onClick = {
            music = !music; if (music) player.start() else player.pause()
        }) { Text(if (music) "🔊" else "🔇", fontSize = 22.sp) }
    }
}

@Composable
fun TopTabs(sel: Int, hue: State<Float>, onSel: (Int) -> Unit) {
    val c = hc(hue.value)
    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 5..9) Chip(CATS[i], sel == i, c, Modifier.weight(1f)) { onSel(i) }
    }
}

@Composable
fun BottomNav(sel: Int, hue: State<Float>, onSel: (Int) -> Unit) {
    val c = hc(hue.value)
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

@Composable
fun DeviceCard(hue: State<Float>) {
    val ctx = LocalContext.current
    val mi = remember {
        ActivityManager.MemoryInfo().also { (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
    }
    val h = hue.value
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .border(BorderStroke(2.dp, Brush.linearGradient(listOf(hc(h), hc(h, 120f)))), RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = .05f), RoundedCornerShape(16.dp)).padding(12.dp)
    ) {
        Text("📱 ${Build.MANUFACTURER} ${Build.MODEL}", color = hc(h), fontWeight = FontWeight.Bold)
        Text("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})  •  ${Build.HARDWARE}", color = Color.White, fontSize = 12.sp)
        Text("🧠 RAM ${mi.totalMem / 1048576} MB  •  ⚙ ${Runtime.getRuntime().availableProcessors()} cores", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun Page(page: Int, engine: Engine) {
    val list = TWEAKS[page].orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(CATS[page], color = ACCENT, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        if (list.isEmpty()) item { Text("تُضاف تفعيلات هذا التبويب لاحقاً", color = Color.Gray) }
        items(list.size) { i -> TweakRow("$page-$i", list[i], engine) }
        item { Signature() }
    }
}

@Composable
fun TweakRow(id: String, t: Tweak, engine: Engine) {
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var on by remember { mutableStateOf(engine.isOn(id)) }
    var busy by remember { mutableStateOf(false) }
    fun toast(ok: Boolean) = Toast.makeText(ctx, if (ok) "✔ تم" else "✘ فشل على هذا الجهاز", Toast.LENGTH_SHORT).show()
    Row(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = .06f), RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold)
            if (t.desc.isNotEmpty()) Text(t.desc, color = Color.Gray, fontSize = 12.sp)
        }
        if (t.oneShot) Button(enabled = !busy, onClick = {
            scope.launch { busy = true; val ok = withContext(Dispatchers.IO) { engine.apply(t, id) }; busy = false; toast(ok) }
        }) { Text("تنفيذ") }
        else Switch(checked = on, enabled = !busy, onCheckedChange = { v ->
            scope.launch {
                busy = true
                val ok = withContext(Dispatchers.IO) { if (v) engine.apply(t, id) else engine.revert(t, id) }
                if (ok) on = v; busy = false; toast(ok)
            }
        }, colors = SwitchDefaults.colors(checkedTrackColor = ACCENT))
    }
}

@Composable
fun Signature() {
    val t = rememberInfiniteTransition(label = "sig")
    val raw = t.animateFloat(-300f, 900f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "x")
    val x = remember { derivedStateOf { (raw.value / 25f).toInt() * 25f } }.value
    val brush = Brush.linearGradient(
        listOf(Color(0xFF555566), Color.White, Color(0xFF555566)),
        start = Offset(x, 0f), end = Offset(x + 300f, 0f)
    )
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Text("Made by Omar Dhaifawi", style = TextStyle(brush = brush, fontSize = 18.sp, fontWeight = FontWeight.Black))
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

@Composable
fun BoostBar(engine: Engine, onDone: () -> Unit) {
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    fun run(mode: Int) { // 0 آمن، 1 ULTRA، 2 استعادة الكل
        scope.launch {
            busy = true
            val all = TWEAKS.flatMap { (p, l) -> l.indices.map { p to it } }
            val list = when (mode) { 0 -> BOOST; 1 -> all.filter { it !in EXCL }; else -> all }
            val n = withContext(Dispatchers.IO) {
                var c = 0
                for ((p, i) in list) {
                    val t = TWEAKS[p]?.getOrNull(i) ?: continue
                    val id = "$p-$i"
                    if (mode < 2) { if (engine.apply(t, id)) c++ }
                    else if (!t.oneShot && engine.isOn(id) && engine.revert(t, id)) c++
                }
                c
            }
            busy = false; onDone()
            val label = when (mode) { 0 -> "🚀 طُبّق "; 1 -> "🔥 ULTRA طُبّق "; else -> "♻ استُعيد " }
            Toast.makeText(ctx, label + (if (mode == 2) "$n" else "$n / ${list.size}"), Toast.LENGTH_LONG).show()
        }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { run(1) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "جارٍ التنفيذ..." else "🔥 ULTRA PERFORMANCE", fontWeight = FontWeight.Black)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { run(0) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("🚀 تعزيز آمن") }
            OutlinedButton(onClick = { run(2) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("♻ استعادة الكل") }
        }
    }
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
