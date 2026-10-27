package octo.adapters.base

import octo.core.agent.SessionStats

/** Adds one turn's token counts to the session totals; a count the turn left out stays as it was. */
fun SessionStats.plusTokens(turn: SessionStats) =
    copy(
        inputTokens = sum(inputTokens, turn.inputTokens),
        outputTokens = sum(outputTokens, turn.outputTokens),
        cacheReadTokens = sum(cacheReadTokens, turn.cacheReadTokens),
        cacheWriteTokens = sum(cacheWriteTokens, turn.cacheWriteTokens),
    )

private fun sum(
    a: Long?,
    b: Long?,
): Long? = if (a == null || b == null) a ?: b else a + b
