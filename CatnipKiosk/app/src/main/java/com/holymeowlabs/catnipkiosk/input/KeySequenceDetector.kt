package com.holymeowlabs.catnipkiosk.input

enum class Dir { UP, DOWN, LEFT, RIGHT }

/**
 * Watches remote D-pad presses for the secret settings sequence. It only
 * observes: callers must still pass every key on to the page.
 */
class KeySequenceDetector(
    private val sequence: List<Dir> = listOf(Dir.UP, Dir.UP, Dir.DOWN, Dir.DOWN, Dir.LEFT, Dir.RIGHT, Dir.LEFT, Dir.RIGHT),
    private val maxGapMs: Long = 2_000,
) {
    private val recent = ArrayDeque<Dir>()
    private var lastAtMs: Long? = null

    /** [key] null means a non-direction key, which resets. Returns true when the sequence completes. */
    fun onKey(key: Dir?, atMs: Long): Boolean {
        val last = lastAtMs
        if (key == null || (last != null && atMs - last > maxGapMs)) recent.clear()
        lastAtMs = atMs
        if (key == null) return false

        // Sliding window, so stray presses before the sequence don't spoil it.
        recent.addLast(key)
        while (recent.size > sequence.size) recent.removeFirst()
        if (recent.toList() != sequence) return false
        recent.clear()
        return true
    }
}
