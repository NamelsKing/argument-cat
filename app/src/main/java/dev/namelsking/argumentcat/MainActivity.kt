package dev.namelsking.argumentcat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private lateinit var game: GameViewModel
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) game.record() else game.store.notice("Microphone access is off. Typing still works.")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        game = ViewModelProvider(this)[GameViewModel::class.java]
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF93E8D0), secondary = Color(0xFFFFC88A), background = Color(0xFF10141C), surface = Color(0xFF1B2330))) {
                GameScreen(game) {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) game.record()
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }
    override fun onStop() { game.background(); super.onStop() }
}

@Composable
private fun GameScreen(vm: GameViewModel, requestMicrophone: () -> Unit) {
    val game by vm.store.state.collectAsState()
    val voice by vm.voice.collectAsState()
    val muted by vm.muted.collectAsState()
    val input by vm.input.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val list = rememberLazyListState()
    val busy = game.phase in listOf(Phase.PREPARING, Phase.THINKING, Phase.JUDGING)
    val listening = voice == "recording"
    LaunchedEffect(game.lines.size) { if (game.lines.isNotEmpty()) list.animateScrollToItem(game.lines.lastIndex) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("ARGUMENT CAT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text("Local AI. Questionable opinions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                TextButton(onClick = vm::toggleMute) { Text(if (muted) "Voice off" else "Voice on") }
            }
            CatFace(
                mood = when {
                    game.verdict?.winner == "player" -> "annoyed"
                    game.verdict?.winner == "cat" -> "happy"
                    listening -> "listening"
                    voice == "speaking" -> "speaking"
                    busy || voice in listOf("transcribing", "synthesizing") -> "thinking"
                    else -> "neutral"
                }, Modifier.size(if (keyboardVisible) 70.dp else 122.dp),
            )
            if (game.phase == Phase.READY) {
                Text("Change my mind.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Pick an argument", color = MaterialTheme.colorScheme.outline)
                Scenarios.all.forEach { scenario ->
                    FilterChip(
                        selected = game.scenario == scenario,
                        onClick = { vm.store.configure(scenario, game.limit) },
                        label = { Text(scenario.title) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 10).forEach { turns ->
                        FilterChip(selected = game.limit == turns, onClick = { vm.store.configure(game.scenario, turns) }, label = { Text("$turns turns") })
                    }
                }
                Text("Convince the cat: ${game.scenario.goal}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
                Button(onClick = vm.store::start, modifier = Modifier.fillMaxWidth()) { Text("Start argument") }
                Text("First use downloads local models. Voice adds speech models. Fictional arguments; the verdict isn't a fact check.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 12.dp))
            } else {
                Text(game.scenario.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${game.completed} of ${game.limit} turns · ${game.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(game.lines) { _, line ->
                        Column(
                            Modifier.fillMaxWidth().background(if (line.role == "user") Color(0xFF183C39) else MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(14.dp),
                        ) {
                            Text(if (line.role == "user") "YOU" else "CAT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(line.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (game.phase == Phase.FINISHED) {
                    Card(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(if (game.verdict?.winner == "player") "Fine. You win." else if (game.verdict != null) "The cat wins. Obviously." else "No verdict", style = MaterialTheme.typography.titleMedium)
                            game.verdict?.let { Text(it.reason) }
                            Text("The cat cared about: ${game.setup?.concern.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Button(onClick = vm::restart, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Another argument") }
                } else if (!busy) {
                    if (voice != "idle") Text(when (voice) { "recording" -> "Listening… tap Stop when done (20s max)"; "transcribing" -> "Transcribing on device…"; "speaking" -> "The cat is speaking…"; else -> "Preparing the cat's voice…" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(6.dp))
                    OutlinedTextField(
                        value = input, onValueChange = { if (it.length <= 400) vm.input.value = it },
                        modifier = Modifier.fillMaxWidth(), placeholder = { Text("Make your case…") }, maxLines = 3,
                        enabled = voice !in listOf("recording", "transcribing"),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { if (listening) vm.finishRecording() else requestMicrophone() }, enabled = voice != "transcribing") { Text(if (listening) "Stop" else "Speak") }
                        Button(onClick = { keyboard?.hide(); focus.clearFocus(); vm.send() }, enabled = input.isNotBlank() && voice !in listOf("recording", "transcribing"), modifier = Modifier.weight(1f)) { Text("Send") }
                        TextButton(onClick = vm::judge, enabled = game.completed > 0 && voice !in listOf("recording", "transcribing")) { Text("Verdict") }
                    }
                    TextButton(onClick = vm::restart) { Text("Start over") }
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                    TextButton(onClick = vm::cancel) { Text("Cancel") }
                }
            }
            game.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
private fun CatFace(mood: String, modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "cat")
    val bob by motion.animateFloat(0f, 4f, infiniteRepeatable(tween(if (mood == "speaking") 180 else 1200), RepeatMode.Reverse), label = "bob")
    val blink by motion.animateFloat(1f, .12f, infiniteRepeatable(keyframes { durationMillis = 4200; 1f at 0; 1f at 3850; .12f at 3970; 1f at 4100 }, RepeatMode.Restart), label = "blink")
    Canvas(modifier.semantics { contentDescription = "A $mood cat" }) {
        val scale = size.width / 120f
        fun p(x: Float, y: Float) = Offset(x * scale, (y + bob) * scale)
        val fur = if (mood == "annoyed") Color(0xFFEA957E) else Color(0xFFFFCC8D)
        val ink = Color(0xFF252A35)
        val ears = Path().apply { moveTo(p(20f, 53f).x, p(20f, 53f).y); lineTo(p(17f, 15f).x, p(17f, 15f).y); lineTo(p(48f, 34f).x, p(48f, 34f).y); close(); moveTo(p(72f, 34f).x, p(72f, 34f).y); lineTo(p(103f, 15f).x, p(103f, 15f).y); lineTo(p(100f, 53f).x, p(100f, 53f).y); close() }
        drawPath(ears, fur)
        drawCircle(fur, 44f * scale, p(60f, 64f))
        for (x in listOf(43f, 77f)) {
            if (mood == "happy") drawLine(ink, p(x - 5, 62f), p(x + 5, 62f), 3f * scale, StrokeCap.Round)
            else drawOval(ink, p(x - 3, 61 - 6 * blink), androidx.compose.ui.geometry.Size(6f * scale, maxOf(2f, 12f * blink) * scale))
            if (mood == "annoyed") drawLine(ink, p(x - 7, if (x < 60) 49f else 55f), p(x + 7, if (x < 60) 55f else 49f), 3f * scale, StrokeCap.Round)
        }
        drawCircle(ink, 3f * scale, p(60f, 73f))
        if (mood == "speaking") drawCircle(ink, (3f + bob) * scale, p(60f, 84f))
        else {
            val mouth = Path().apply { moveTo(p(49f, 82f).x, p(49f, 82f).y); quadraticTo(p(60f, if (mood == "annoyed") 74f else 92f).x, p(60f, if (mood == "annoyed") 74f else 92f).y, p(71f, 82f).x, p(71f, 82f).y) }
            drawPath(mouth, ink, style = Stroke(2.5f * scale))
        }
        for (side in listOf(-1, 1)) for (dy in listOf(-4f, 4f)) drawLine(ink.copy(alpha = .6f), p(60f + side * 27, 77f + dy), p(60f + side * 48, 75f + dy * 2), 1.5f * scale)
    }
}
