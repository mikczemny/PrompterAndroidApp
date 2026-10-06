package com.mikczemny.prompter.ui

/** Binary search over token line tops; equal values belong to the same line. */
private fun lowerLineBound(lineTops: FloatArray, y: Float): Int {
    var low = 0
    var high = lineTops.size
    while (low < high) {
        val mid = (low + high) / 2
        if (lineTops[mid] < y) low = mid + 1 else high = mid
    }
    return low
}

private fun FloatArray.ready(): Boolean = isNotEmpty() && first().isFinite() && last().isFinite()

/** The first token on the rendered line at or immediately above the reading band. */
internal fun tokenAtReadingLine(lineTops: FloatArray, y: Float): Int {
    if (!lineTops.ready() || !y.isFinite()) return -1
    val next = lowerLineBound(lineTops, y)
    val token = when {
        next == lineTops.size -> lineTops.lastIndex
        lineTops[next] == y -> next
        else -> (next - 1).coerceAtLeast(0)
    }
    return lowerLineBound(lineTops, lineTops[token])
}

/** Move by a rendered line, skipping blank lines and keeping the matcher in sync. */
internal fun lineJumpTarget(lineTops: FloatArray, currentIndex: Int, direction: Int): Int {
    if (!lineTops.ready()) return -1
    if (direction < 0 && currentIndex < 0) return -1
    val index = currentIndex.coerceIn(0, lineTops.lastIndex)
    val firstOnLine = lowerLineBound(lineTops, lineTops[index])
    return if (direction < 0) {
        if (firstOnLine == 0) -1
        else lowerLineBound(lineTops, lineTops[firstOnLine - 1])
    } else {
        // Line tops are pixel coordinates. An epsilon excludes every token on
        // the current line without depending on font size or word counts.
        lowerLineBound(lineTops, lineTops[index] + 0.5f).coerceAtMost(lineTops.lastIndex)
    }
}

internal fun pageJumpTarget(
    lineTops: FloatArray,
    currentIndex: Int,
    direction: Int,
    viewportHeight: Float,
): Int {
    if (!lineTops.ready() || !viewportHeight.isFinite() || viewportHeight <= 0f) return -1
    val index = currentIndex.coerceIn(0, lineTops.lastIndex)
    val target = lineTops[index] + (if (direction < 0) -1 else 1) * viewportHeight * 0.8f
    if (target < lineTops.first()) return -1
    val jump = tokenAtReadingLine(lineTops, target)
    // On a very narrow or large-font viewport, one page can be less than one
    // line. A press should still advance to the adjacent line.
    return if (lineTops[jump] == lineTops[index]) lineJumpTarget(lineTops, index, direction) else jump
}

internal data class ReadingInsets(val start: Float, val end: Float)

/**
 * Reserve camera space first, then fit equal percentage margins. Extremely
 * large selfie windows may overlap text, but can never squeeze it to zero.
 */
internal fun readingInsets(
    viewportWidth: Float,
    marginPercent: Float,
    cameraStart: Float,
    cameraEnd: Float,
    minimumTextWidth: Float,
): ReadingInsets {
    if (!viewportWidth.isFinite() || viewportWidth <= 0f) return ReadingInsets(0f, 0f)
    val minWidth = minimumTextWidth.takeIf { it.isFinite() }?.coerceIn(0f, viewportWidth) ?: 0f
    val budget = viewportWidth - minWidth
    val start = cameraStart.takeIf { it.isFinite() }?.coerceIn(0f, viewportWidth) ?: 0f
    val end = cameraEnd.takeIf { it.isFinite() }?.coerceIn(0f, viewportWidth) ?: 0f
    val cameraTotal = start + end
    if (cameraTotal > budget) {
        val scale = if (cameraTotal == 0f) 0f else budget / cameraTotal
        return ReadingInsets(start * scale, end * scale)
    }
    val percent = marginPercent.takeIf { it.isFinite() }?.coerceIn(0f, 40f) ?: 8f
    val margin = viewportWidth * percent / 100f
    val extraStart = (margin - start).coerceAtLeast(0f)
    val extraEnd = (margin - end).coerceAtLeast(0f)
    val extras = extraStart + extraEnd
    val scale = if (extras == 0f) 0f else ((budget - cameraTotal) / extras).coerceAtMost(1f)
    return ReadingInsets(start + extraStart * scale, end + extraEnd * scale)
}
