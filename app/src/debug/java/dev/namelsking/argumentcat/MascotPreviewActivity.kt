package dev.namelsking.argumentcat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.namelsking.argumentcat.mascot.CatMascot
import dev.namelsking.argumentcat.mascot.CatPose
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

/** Debug-only visual check. Does not load AI models or use the microphone. */
class MascotPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val emotion = intent.getStringExtra("emotion") ?: "neutral"
        val activity = intent.getStringExtra("activity") ?: "idle"
        setContent {
            var t by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(Unit) { while (true) { delay(33); t += .033f } }
            Column(Modifier.fillMaxSize().background(Color(0xFF10141C)).systemBarsPadding(), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
                CatMascot(CatPose(emotion, activity, if(activity=="speaking") abs(sin(t*8f)*sin(t*2.7f)) else 0f),Modifier.size(340.dp))
                Text("$emotion · $activity",color=Color(0xFF93E8D0))
            }
        }
    }
}
