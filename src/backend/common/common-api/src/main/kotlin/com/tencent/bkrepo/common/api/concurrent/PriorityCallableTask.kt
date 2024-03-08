package com.tencent.bkrepo.common.api.concurrent

import java.util.concurrent.Callable

class PriorityCallableTask<T>(
    private val priority: Int,
    private val callable: Callable<T>,
) : PriorityCallable<T, PriorityCallableTask<T>>() {
    override fun getComparable(): PriorityCallableTask<T> {
        return this
    }

    override fun call(): T {
        return callable.call()
    }

    override fun compareTo(other: PriorityCallable<T, PriorityCallableTask<T>>): Int {
        return this.priority.compareTo(other.getComparable().priority)
    }
}
