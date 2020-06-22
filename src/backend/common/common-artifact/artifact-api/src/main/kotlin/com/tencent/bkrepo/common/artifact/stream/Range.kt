package com.tencent.bkrepo.common.artifact.stream

import kotlin.math.min

class Range(startPosition: Long, endPosition: Long, val total: Long) {
    val start: Long = if (startPosition < 0) 0 else startPosition
    val end: Long = if (endPosition < 0) total - 1 else min(endPosition, total - 1)
    val length: Long = end - start + 1

    init {
        require(total >= 0) { "Invalid total size: $total" }
        require(length >= 0) { "Invalid range length $length" }
    }

    fun isPartialContent(): Boolean {
        return length != total
    }

    fun isEmpty(): Boolean {
        return length == 0L
    }

    override fun toString(): String {
        return "${start}-${end}/${total}"
    }

    companion object {
        fun ofFull(total: Long) = Range(0, total - 1, total)
    }
}
