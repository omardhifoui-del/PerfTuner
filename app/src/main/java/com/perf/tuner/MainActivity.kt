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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.border
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
        // ضع ملف الأغنية (ملكك أو بدون حقوق) في res/raw/funk.mp3
        val id = resources.getIdentifier("funk", "raw", packageName)
        if (id != 0) player = MediaPlayer.create(this, id)?.apply { isLooping = true; setVolume(.12f, .12f) }
        val engine = Engine(this)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = darkColorScheme()) { App(engine, player) }
            }
        }
    }
    override fun onPause() { super.onPause(); player?.pause() }
    override fun onDestroy() { player?.release(); super.onDestroy() }
}

@Composable
fun rgb(offset: Float = 0f): Color {
    val t = rememberInfiniteTransition(label = "rgb")
    val h by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "h")
    return Color.hsv((h + offset) % 360f, 0.8f, 1f)
}

@Composable
fun App(engine: Engine, player: MediaPlayer?) {
    var sel by remember { mutableIntStateOf(0) }
    var music by remember { mutableStateOf(false) }
    val c1 = rgb(); val c2 = rgb(120f)
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0B18), Color(0xFF16122E))))
            .statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⚡ PERF TUNER", Modifier.weight(1f),
                style = TextStyle(brush = Brush.linearGradient(listOf(c1, c2)), fontSize = 24.sp, fontWeight = FontWeight.Black))
            if (player != null) TextButton(onClick = {
                music = !music; if (music) player.start() else player.pause()
            }) { Text(if (music) "🔊" else "🔇", fontSize = 22.sp) }
        }
        DeviceCard(c1, c2)
        // الشريط العلوي: التبويبات 5..9
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 5..9) Chip(CATS[i], sel == i, c1, Modifier.weight(1f)) { sel = i }
        }
        AnimatedContent(
            targetState = sel, modifier = Modifier.weight(1f), label = "page",
            transitionSpec = {
                val d = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(450)) { it * d } + fadeIn(tween(450)))
                    .togetherWith(slideOutHorizontally(tween(450)) { -it * d } + fadeOut(tween(300)))
            }
        ) { page -> Page(page, engine, c1) }
        // الشريط السفلي: التبويبات 0..4
        NavigationBar(containerColor = Color(0xFF0E0C20)) {
            for (i in 0..4) NavigationBarItem(
                selected = sel == i, onClick = { sel = i },
                icon = { Text(CATS[i].substringBefore(' '), fontSize = 20.sp) },
                label = { Text(CATS[i].substringAfter(' '), fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = c1.copy(alpha = .25f))
            )
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, c: Color, m: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) c.copy(alpha = .3f) else Color.White.copy(alpha = .06f), label = "chip")
    Box(
        m.clip(RoundedCornerShape(12.dp)).background(bg).clickable { onClick() }.padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text(text.substringAfter(' '), fontSize = 11.sp, color = Color.White, maxLines = 1) }
}

@Composable
fun DeviceCard(c1: Color, c2: Color) {
    val ctx = LocalContext.current
    val mi = remember {
        ActivityManager.MemoryInfo().also { (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
    }
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .border(BorderStroke(2.dp, Brush.linearGradient(listOf(c1, c2))), RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = .05f), RoundedCornerShape(16.dp)).padding(12.dp)
    ) {
        Text("📱 ${Build.MANUFACTURER} ${Build.MODEL}", color = c1, fontWeight = FontWeight.Bold)
        Text("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})  •  ${Build.HARDWARE}", color = Color.White, fontSize = 12.sp)
        Text("🧠 RAM ${mi.totalMem / 1048576} MB  •  ⚙ ${Runtime.getRuntime().availableProcessors()} cores", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun Page(page: Int, engine: Engine, accent: Color) {
    val list = TWEAKS[page].orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(CATS[page], color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        if (list.isEmpty()) item { Text("تُضاف تفعيلات هذا التبويب في الدفعة التالية", color = Color.Gray) }
        items(list.size) { i -> TweakRow("$page-$i", list[i], engine, accent) }
        item { Signature() }
    }
}

@Composable
fun TweakRow(id: String, t: Tweak, engine: Engine, accent: Color) {
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
            Text(t.desc, color = Color.Gray, fontSize = 12.sp)
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
        }, colors = SwitchDefaults.colors(checkedTrackColor = accent))
    }
}

@Composable
fun Signature() {
    val t = rememberInfiniteTransition(label = "sig")
    val x by t.animateFloat(-300f, 900f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "x")
    val brush = Brush.linearGradient(
        listOf(Color(0xFF555566), Color.White, Color(0xFF555566)),
        start = androidx.compose.ui.geometry.Offset(x, 0f),
        end = androidx.compose.ui.geometry.Offset(x + 300f, 0f)
    )
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Text("Made by Omar Dhaifawi", style = TextStyle(brush = brush, fontSize = 18.sp, fontWeight = FontWeight.Black))
    }
}
