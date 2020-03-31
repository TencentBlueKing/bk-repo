package com.tencent.bkrepo.common.stream.binder.memory.queue

import org.springframework.messaging.Message
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

object MemoryListenerContainer {
    private val listeners = ConcurrentHashMap<String, MutableSet<Consumer<Message<*>>>>()

    fun findListener(dest: String): Set<Consumer<Message<*>>> {
        return listeners[dest] ?: setOf()
    }

    fun registerListener(dest: String, consumer: Consumer<Message<*>>) {
        val set = listeners.getOrPut(dest) {
            mutableSetOf()
        }
        set.add(consumer)
    }

    fun unregisterListener(dest: String) {
        listeners.remove(dest)
    }
}
