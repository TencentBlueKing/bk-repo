package com.tencent.bkrepo.common.metadata.metrics

import com.tencent.bkrepo.common.metadata.util.NodeDeleteHelper
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component

/**
 * common-metadata 指标统一注册入口，集中管理元数据相关的监控指标
 */
@Component
class MetadataMetrics : MeterBinder {

    override fun bindTo(registry: MeterRegistry) {
        registerNodeDeleteMetrics(registry)
    }

    /**
     * 注册节点删除相关指标
     */
    private fun registerNodeDeleteMetrics(registry: MeterRegistry) {
        Gauge.builder(NODE_DELETE_RUNNING_COUNT, NodeDeleteHelper.runningDeleteCount) { it.get().toDouble() }
            .description(NODE_DELETE_RUNNING_COUNT_DESC)
            .register(registry)
    }

    companion object {
        /**
         * 当前正在执行的 deleteNodes 操作数量
         */
        const val NODE_DELETE_RUNNING_COUNT = "node.delete.running.count"
        const val NODE_DELETE_RUNNING_COUNT_DESC = "Number of deleteNodes operations currently running"
    }
}
