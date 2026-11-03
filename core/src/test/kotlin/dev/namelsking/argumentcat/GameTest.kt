package dev.namelsking.argumentcat

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GameTest {
    private class Fake : CatEngine {
        var calls = 0
        var conceded = false
        var fail = false
        var malformed = false
        var wait: CompletableDeferred<Unit>? = null
        var progress: ((String) -> Unit)? = null
        override suspend fun prepare(status: (String) -> Unit) { progress = status; status("Ready") }
        override suspend fun complete(system: String, history: List<Line>): String {
            calls++
            wait?.await()
            if (fail) error("Offline error")
            if (malformed) return "broken"
            val reply = if (conceded) "You are right. Scissors can cut thin metal such as foil." else "That has not convinced me."
            return """{"conceded":$conceded,"reply":"$reply"}"""
        }
    }
    @Test fun `model deadline is a recoverable failure while cancellation still propagates`() = runTest {
        assertFailsWith<IllegalStateException> { modelDeadline(10, "Reply") { delay(20); "late" } }
        assertTrue(currentCoroutineContext().isActive)
        assertFailsWith<CancellationException> { modelDeadline(100, "Reply") { throw CancellationException("Stopped") } }
    }
    @Test fun `last turn ends an unconceded round and cannot extend it`() = runTest {
        val engine = Fake(); val game = GameStore(backgroundScope, engine)
        game.configure(Scenarios.all.first(), 3); game.start(); runCurrent()
        repeat(3) { game.send("Please agree."); runCurrent() }
        assertEquals(Phase.FINISHED, game.state.value.phase)
        assertEquals(3, game.state.value.completed)
        assertEquals("cat", game.state.value.verdict?.winner)
        assertEquals(3, engine.calls)
        game.send("One more"); game.judge(); runCurrent()
        assertEquals(3, engine.calls)
    }
    @Test fun `a concession wins immediately and speaks the actual answer without a second judge`() = runTest {
        val engine = Fake(); engine.conceded = true
        val spoken = mutableListOf<String>(); val game = GameStore(backgroundScope, engine, spoken::add)
        game.start(); runCurrent(); game.send("Scissors cut aluminum foil."); runCurrent()
        assertEquals(Phase.FINISHED, game.state.value.phase)
        assertEquals(1, game.state.value.completed)
        assertEquals("player", game.state.value.verdict?.winner)
        assertEquals(1, game.state.value.verdict?.evidenceTurn)
        assertEquals(1, engine.calls)
        assertEquals(game.state.value.lines.last().text, spoken.last())
        assertTrue(spoken.last().startsWith("You are right."))
        game.judge(); game.send("Again"); runCurrent()
        assertEquals("player", game.state.value.verdict?.winner)
        assertEquals(1, engine.calls)
    }
    @Test fun `ending early uses the existing concession state without another model call`() = runTest {
        val engine = Fake(); val game = GameStore(backgroundScope, engine)
        game.start(); runCurrent(); game.judge()
        assertEquals(Phase.INPUT, game.state.value.phase)
        game.send("Please"); runCurrent(); game.judge()
        assertEquals(Phase.FINISHED, game.state.value.phase)
        assertEquals("cat", game.state.value.verdict?.winner)
        assertEquals(1, engine.calls)
    }
    @Test fun `duplicate send is ignored and restart discards old result`() = runTest {
        val engine = Fake(); val game = GameStore(backgroundScope, engine)
        game.start(); runCurrent(); engine.wait = CompletableDeferred()
        game.send("First"); game.send("Duplicate"); runCurrent()
        assertEquals(1, engine.calls)
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
    @Test fun `malformed answers cannot create a win or spend a turn`() = runTest {
        val engine = Fake(); val game = GameStore(backgroundScope, engine)
        game.start(); runCurrent(); engine.malformed = true
        game.send("I win"); runCurrent()
        assertNull(game.state.value.verdict)
        assertEquals(0, game.state.value.completed)
        assertEquals(Phase.INPUT, game.state.value.phase)
        assertNotNull(game.state.value.error)
        assertFails { Rules.reply("""{"reply":"I agree."}""") }
        assertFails { Rules.reply("""{"reply":"","conceded":true}""") }
    }
    @Test fun `empty replies are rejected and long replies stay short`() {
        assertEquals("One. Two!", Rules.shortReply("One. Two! Three?"))
        assertFails { Rules.shortReply("   ") }
    }
    @Test fun `starting preserves the claim and uses no generated hidden condition`() = runTest {
        val engine = Fake(); val game = GameStore(backgroundScope, engine)
        val scenario = Scenarios.all.first()
        game.start(); runCurrent()
        assertEquals(scenario.stance, game.state.value.lines.single().text)
        assertEquals(scenario.concern, game.state.value.setup!!.concern)
        assertEquals(0, engine.calls)
    }
    @Test fun `speech loading cannot overwrite ready or finished game status`() = runTest {
        val engine = Fake(); val game = GameStore(backgroundScope, engine)
        game.start(); runCurrent(); engine.progress!!("Downloading speech 100%")
        assertEquals("Your move", game.state.value.status)
        engine.conceded = true; game.send("Foil"); runCurrent()
        val resultStatus = game.state.value.status
        engine.progress!!("Downloading speech 100%")
        assertEquals(resultStatus, game.state.value.status)
    }
}
