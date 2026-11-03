package dev.namelsking.argumentcat

import kotlinx.coroutines.withTimeoutOrNull

// An SDK deadline is a recoverable failure; user/lifecycle cancellation still propagates.
suspend fun <T : Any> modelDeadline(milliseconds: Long, label: String, block: suspend () -> T): T =
    withTimeoutOrNull(milliseconds) { block() } ?: error("$label took too long. Please try again.")
