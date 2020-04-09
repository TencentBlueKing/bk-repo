package com.tencent.bkrepo.common.artifact.util.response

class Range(val total: Long, start: Long? = 0, end: Long? = total - 1) {
    val start: Long
    val end: Long

    init {
        if (start == null) {
            // -y
            this.start = total - end!!
            this.end = total - 1
        } else if (end == null || end > total - 1) {
            // x-
            this.start = start
            this.end = total - 1
        } else {
            // x-y
            this.start = start
            this.end = end.coerceAtMost(total - 1)
        }
    }

    fun validate(): Boolean {
        return start <= end
    }

    fun getLength(): Long {
        return end - start + 1
    }
}
