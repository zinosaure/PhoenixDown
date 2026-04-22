package com.swordfish.lemuroid.app.shared.logs

object InAppLogStore {
    private const val MAX_LINES = 4000
    private val lock = Any()
    private val lines = ArrayDeque<String>(MAX_LINES)

    fun append(line: String) {
        synchronized(lock) {
            if (lines.size >= MAX_LINES) {
                lines.removeFirst()
            }
            lines.addLast(line)
        }
    }

    fun dump(): String {
        synchronized(lock) {
            return lines.joinToString(separator = "\n")
        }
    }

    fun clear() {
        synchronized(lock) {
            lines.clear()
        }
    }
}
