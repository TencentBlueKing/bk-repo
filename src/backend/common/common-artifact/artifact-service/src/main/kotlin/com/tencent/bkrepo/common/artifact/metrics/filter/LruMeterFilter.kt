package com.tencent.bkrepo.common.artifact.metrics.filter

import io.micrometer.core.instrument.Meter.Id
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.config.MeterFilterReply

/**
 * 达到最大限制时，根据LRU策略逐出meter
 * */
class LruMeterFilter(
    private val meterNamePrefix: String,
    private val registry: MeterRegistry,
    private val capacity: Int,
    pinnedChecker: PinnedChecker? = null,
) : MeterFilter {

    private val lruSet = LRUSet(pinnedChecker)
    override fun accept(id: Id): MeterFilterReply {
        if (matchName(id)) {
            synchronized(lruSet) {
                lruSet.add(id)
            }
        }
        return MeterFilterReply.NEUTRAL
    }

    fun access(value: Id) {
        synchronized(lruSet) {
            lruSet[value]
        }
    }

    inner class LRUSet(
        private val pinnedChecker: PinnedChecker? = null
    ) : LinkedHashMap<Id, Any>(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, true) {

        private val dummy = Any()
        fun add(value: Id) {
            super.put(value, dummy)
        }

        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Id, Any>): Boolean {
            if (capacity == -1) {
                return false
            }
            if (pinnedChecker?.shouldPinned(eldest.key) == true) {
                // 访问一次，避免下次还是淘汰该key
                // 由于未淘汰，会导致lruSet大小最大为容量与保留的缓存数量之和
                this[eldest.key]
                return false
            }
            val removed = size > capacity
            if (removed) {
                registry.remove(eldest.key)
            }
            return removed
        }
    }

    interface PinnedChecker {
        /**
         * 判断给定指标ID是否要保留在缓存中不被淘汰
         * @param id 需要检查的指标ID
         * @return 如果该指标应被保留返回true，否则返回false
         */
        fun shouldPinned(id: Id): Boolean
    }

    private fun matchName(id: Id): Boolean {
        return id.name.startsWith(meterNamePrefix)
    }

    companion object {
        private const val DEFAULT_INITIAL_CAPACITY = 16
        private const val DEFAULT_LOAD_FACTOR = 0.75f
    }
}
