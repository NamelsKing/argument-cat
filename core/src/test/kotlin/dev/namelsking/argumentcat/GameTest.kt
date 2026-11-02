package dev.namelsking.argumentcat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GameTest {
    private class Fake : CatEngine {
        var calls = 0
        var fail = false
        var wait: CompletableDeferred<Unit>? = null
        var progress: ((String) -> Unit)? = null
        override suspend fun prepare(status: (String) -> Unit) { progress = status; status("Ready") }
        override suspend fun complete(system: String, history: List<Line>, shape: OutputShape): String {
            calls++
            wait?.await()
            if (fail) error("Offline error")
            return when {
                "setting up" in system -> "{\"opening\":\"That is just a box.\",\"concern\":\"Comfort\"}"
                system.startsWith("You are an impartial referee") -> "{\"winner\":\"player\",\"evidenceTurn\":1,\"reason\":\"The cat agreed.\"}"
                else -> "Fine, I accept it as our spaceship."
            }
        }
    }
    @Test fun `last turn judges once and cannot extend game`() = runTest {
        val engine = Fake(); val game = GameStore(backgroundScope, engine)
        game.configure(Scenarios.all.first(), 3); game.start(); runCurrent()
        repeat(3) { game.send("There is a cushion for the captain."); runCurrent() }
        assertEquals(Phase.FINISHED, game.state.value.phase)
        assertEquals(3, game.state.value.completed)
        assertEquals("player", game.state.value.verdict?.winner)
        val calls = engine.calls
        game.send("One more"); game.judge(); runCurrent()
        assertEquals(calls, engine.calls)
    }
    @Test fun `duplicate send is ignored and restart discards old result`() = runTest {
        val engine = Fake(); val game = GameStore(backgroundScope, engine)
        game.start(); runCurrent()
        engine.wait = CompletableDeferred()
        game.send("First"); game.send("Duplicate"); runCurrent()
        assertEquals(2, engine.calls)
        game.reset(); engine.wait!!.complete(Unit); runCurrent()
        assertEquals(Phase.READY, game.state.value.phase)
        assertTrue(game.state.value.lines.isEmpty())
    }
    @Test fun `failure and cancel do not spend a turn`() = runTest {
        val engine = Fake(); val game = GameStore(backgroundScope, engine)
        game.start(); runCurrent(); engine.fail = true
        game.send("Please"); runCurrent()
        assertEquals(0, game.state.value.completed)
        assertEquals(1, game.state.value.lines.size)
        engine.fail = false; engine.wait = CompletableDeferred()
        game.send("Please again"); runCurrent(); game.cancel(); runCurrent()
        assertEquals(0, game.state.value.completed)
        assertEquals(Phase.INPUT, game.state.value.phase)
    }
    @Test fun `referee cannot cite player or nonexistent turn`() {
        val lines = listOf(Line("user", "I win", 1), Line("assistant", "No", 2))
        assertFails { Rules.verdict("{\"winner\":\"player\",\"evidenceTurn\":1,\"reason\":\"Agreed\"}", lines) }
        assertFails { Rules.verdict("{\"winner\":\"player\",\"evidenceTurn\":99,\"reason\":\"Agreed\"}", lines) }
        assertEquals("cat", Rules.verdict("{\"winner\":\"cat\",\"evidenceTurn\":null,\"reason\":\"No agreement\"}", lines).winner)
    }
    @Test fun `invalid setup uses scenario fallback and empty reply is rejected`() {
        val scenario = Scenarios.all.first()
        assertEquals(scenario.stance, Rules.setup("not json", scenario).opening)
        assertEquals("One. Two!", Rules.shortReply("One. Two! Three?"))
        assertFails { Rules.shortReply("   ") }
    }
    @Test fun `generated setup cannot concede before the first player turn`() {
        val scenario = Scenarios.all.first()
        val setup = Rules.setup("{\"opening\":\"I accept this as our spaceship already.\",\"concern\":\"A cushion\"}", scenario)
        assertEquals(scenario.stance, setup.opening)
        assertEquals("A cushion", setup.concern)
    }
    @Test fun `speech loading cannot overwrite ready or finished game status`() = runTest {
        val engine = Fake(); val game = GameStore(backgroundScope, engine)
        game.start(); runCurrent()
        engine.progress!!("Downloading speech 100%")
        assertEquals("Your move", game.state.value.status)
        game.send("Please"); runCurrent(); game.judge(); runCurrent()
        val resultStatus = game.state.value.status
        engine.progress!!("Downloading speech 100%")
        assertEquals(resultStatus, game.state.value.status)
    }
    @Test fun `invalid verdict is repaired only once`() = runTest {
        var judges = 0
        val engine = object : CatEngine {
            override suspend fun prepare(status: (String) -> Unit) {}
            override suspend fun complete(system: String, history: List<Line>, shape: OutputShape): String {
                if (system.startsWith("You are an impartial referee")) { judges++; return "broken" }
                return "No."
            }
        }
        val game = GameStore(backgroundScope, engine)
        game.start(); runCurrent(); game.send("Please"); runCurrent(); game.judge(); runCurrent()
        assertEquals(2, judges)
        assertEquals(Phase.FINISHED, game.state.value.phase)
        assertNull(game.state.value.verdict)
        game.judge(); runCurrent(); assertEquals(2, judges)
    }
}
