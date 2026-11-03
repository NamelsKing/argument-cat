package dev.namelsking.argumentcat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.namelsking.argumentcat.mascot.CatMascot
import dev.namelsking.argumentcat.mascot.CatPose
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
    val voiceDetail by vm.voiceDetail.collectAsState()
    val muted by vm.muted.collectAsState()
    val input by vm.input.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val list = rememberLazyListState()
    val busy = game.phase in listOf(Phase.PREPARING, Phase.THINKING)
    val listening = voice == "recording"
    LaunchedEffect(game.lines.size) { if (game.lines.isNotEmpty()) list.animateScrollToItem(game.lines.lastIndex) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("ARGUMENT CAT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    if (!keyboardVisible) Text("Local AI. Questionable opinions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                TextButton(onClick = vm::toggleMute) { Text(if (muted) "Voice off" else "Voice on") }
            }
            if (!keyboardVisible) GameCat(vm,
                CatPose(
                    emotion = when (game.verdict?.winner) { "player" -> "annoyed"; "cat" -> "happy"; else -> "neutral" },
                    activity = when {
                        listening -> "listening"
                        voice == "speaking" -> "speaking"
                        busy || voice in listOf("transcribing", "synthesizing") -> "thinking"
                        else -> "idle"
                    },
                ), Modifier.size(width = 220.dp, height = if (game.phase == Phase.READY) 160.dp else 180.dp),
            )
            if (game.phase == Phase.READY) {
                Text("Change my mind.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Pick a claim", color = MaterialTheme.colorScheme.outline)
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
                Text("Your goal: ${game.scenario.goal}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
                Button(onClick = vm.store::start, modifier = Modifier.fillMaxWidth()) { Text("Start argument") }
                Text("Win by changing the cat's mind. First use downloads the models. This is a game, not a fact check.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 12.dp))
            } else {
                Text(game.scenario.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${game.completed} of ${game.limit} turns · ${game.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (voice != "idle") Text(when (voice) { "recording" -> "Listening… tap Stop when done (20s max)"; "transcribing" -> "Transcribing on device…"; else -> voiceDetail }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(6.dp))
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
                        }
                    }
                    Button(onClick = vm::restart, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Another argument") }
                } else if (!busy) {
                    OutlinedTextField(
                        value = input, onValueChange = { if (it.length <= 400) vm.input.value = it },
                        modifier = Modifier.fillMaxWidth(), placeholder = { Text("Make your case…") }, maxLines = 3,
                        enabled = voice !in listOf("recording", "transcribing"),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { if (listening) vm.finishRecording() else requestMicrophone() }, enabled = voice != "transcribing") { Text(if (listening) "Stop" else "Speak") }
                        Button(onClick = { keyboard?.hide(); focus.clearFocus(); vm.send() }, enabled = input.isNotBlank() && voice !in listOf("recording", "transcribing"), modifier = Modifier.weight(1f)) { Text("Send") }
                        TextButton(onClick = vm::judge, enabled = game.completed > 0 && voice !in listOf("recording", "transcribing")) { Text("End round") }
                    }
                    Row {
                        TextButton(onClick = vm::restart) { Text("Start over") }
                        TextButton(onClick = vm::hearReply, enabled = voice !in listOf("recording", "transcribing")) {
                            Text(if (voice in listOf("synthesizing", "speaking")) "Stop voice" else "Hear reply")
                        }
                    }
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                    TextButton(onClick = vm::cancel) { Text("Cancel") }
                }
                if (game.phase == Phase.FINISHED) TextButton(onClick = vm::hearReply) {
                    Text(if (voice in listOf("synthesizing", "speaking")) "Stop voice" else "Hear reply")
                }
            }
            game.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
private fun GameCat(vm: GameViewModel, pose: CatPose, modifier: Modifier) {
    val level by vm.speechLevel.collectAsState()
    CatMascot(pose.copy(speech = level), modifier)
}
