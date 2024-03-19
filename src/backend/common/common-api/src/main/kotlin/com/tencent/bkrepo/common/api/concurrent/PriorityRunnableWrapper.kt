package com.tencent.bkrepo.common.api.concurrent

class PriorityRunnableWrapper(
    val priority: Int,
    val runnable: Runnable,
) : Runnable, Comparable<PriorityRunnableWrapper> {
    override fun run() {
        runnable.run()
    }

    override fun compareTo(other: PriorityRunnableWrapper): Int {
        return priority.compareTo(other.priority)
    }
}
