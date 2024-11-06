package com.tencent.bkrepo.archive.core

/**
 * 一个具有优先级的包装类
 */
class PriorityWrapper<T>(val priority: Int, val obj: T) : Comparable<PriorityWrapper<T>> {
    override fun compareTo(other: PriorityWrapper<T>): Int {
        return priority.compareTo(other.priority)
    }
}
