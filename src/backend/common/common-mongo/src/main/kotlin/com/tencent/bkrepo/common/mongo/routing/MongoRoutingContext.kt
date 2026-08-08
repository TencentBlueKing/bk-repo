package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.api.thread.TransmittableThreadLocal
import java.util.concurrent.ConcurrentHashMap

/**
 * 多实例路由 ThreadLocal 上下文，按规则名隔离。
 * 使用项目内置的 [TransmittableThreadLocal]，配合 [com.tencent.bkrepo.common.api.thread.TransmitterExecutorWrapper]
 * 包装的线程池，路由键可自动跨线程池边界传播，避免工作线程路由上下文静默丢失。
 */
object MongoRoutingContext {

    private val store = ConcurrentHashMap<String, TransmittableThreadLocal<String?>>()

    fun set(ruleName: String, key: String) {
        store.getOrPut(ruleName) { TransmittableThreadLocal() }.set(key)
    }

    fun get(ruleName: String): String? = store[ruleName]?.get()

    fun clear(ruleName: String) = store[ruleName]?.remove()

    fun <T> withRoutingKey(ruleName: String, key: String, block: () -> T): T {
        set(ruleName, key)
        return try {
            block()
        } finally {
            clear(ruleName)
        }
    }
}
