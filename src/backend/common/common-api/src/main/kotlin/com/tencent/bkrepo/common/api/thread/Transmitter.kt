package com.tencent.bkrepo.common.api.thread

/**
 * 传播者，负责threadLocal的传播
 *
 * 使用capture->replay->reset
 * */
object Transmitter {
    val holder = object : InheritableThreadLocal<MutableSet<ThreadLocal<Any>>>() {
        override fun initialValue(): MutableSet<ThreadLocal<Any>> {
            return mutableSetOf()
        }
    }

    /**
     * 捕获当前线程所有线程变量
     * */
    fun capture(): Snapshot {
        val threadLocal2Value = mutableMapOf<ThreadLocal<Any>, Any>()
        val threadLocals = holder.get()
        threadLocals.forEach {
            threadLocal2Value[it] = it.get() as Any
        }
        return Snapshot(threadLocal2Value)
    }

    /**
     * 回放之前的线程变量到当前线程
     * */
    fun replay(snapshot: Snapshot) {
        snapshot.threadLocal2Value.forEach {
            val threadLocal = it.key
            val value = it.value
            threadLocal.set(value)
        }
    }

    /**
     * 重置当前线程
     * */
    fun reset(snapshot: Snapshot) {
        snapshot.threadLocal2Value.forEach {
            val threadLocal = it.key as ThreadLocal<*>
            threadLocal.remove()
        }
    }
}
