package com.tencent.bkrepo.media.stream

import com.tencent.bkrepo.common.metrics.constant.PACKET_HANDLER_COUNTER
import com.tencent.bkrepo.common.metrics.constant.PACKET_HANDLER_COUNTER_DESC
import com.tencent.bkrepo.common.metrics.constant.PACKET_HANDLER_SIZE_COUNTER
import com.tencent.bkrepo.common.metrics.constant.PACKET_HANDLER_SIZE_COUNTER_DESC
import com.tencent.bkrepo.common.metrics.constant.PACKET_LOSS_COUNTER
import com.tencent.bkrepo.common.metrics.constant.PACKET_LOSS_COUNTER_DESC
import com.tencent.bkrepo.common.metrics.constant.PACKET_QUEUE_COUNTER
import com.tencent.bkrepo.common.metrics.constant.PACKET_QUEUE_COUNTER_DESC
import com.tencent.bkrepo.common.metrics.constant.PACKET_RECV_COUNTER
import com.tencent.bkrepo.common.metrics.constant.PACKET_RECV_COUNTER_DESC
import com.tencent.bkrepo.common.metrics.constant.RECORDING_COUNTER
import com.tencent.bkrepo.common.metrics.constant.RECORDING_COUNTER_DESC
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component

/**
 * 媒体服务监控指标
 * */
@Component
class MediaMetrics(private val streamManger: StreamManger) : MeterBinder {

    override fun bindTo(registry: MeterRegistry) {
        Companion.registry = registry
        Gauge.builder(RECORDING_COUNTER) { streamManger.streams.values.filter { it.isRecording() }.size }
            .description(RECORDING_COUNTER_DESC)
            .register(registry)
        Gauge.builder(PACKET_QUEUE_COUNTER) {
            val listeners = streamManger.streams.values.filter { it.recordingListener is AsyncStreamListener }
                .map { it.recordingListener as AsyncStreamListener }
            if (listeners.isNotEmpty()) {
                listeners.map { it.packetQueue.size }.reduce(Int::plus)
            } else {
                0
            }
        }.description(PACKET_QUEUE_COUNTER_DESC).register(registry)
    }

    enum class Action {
        PACKET_RECV,
        PACKET_HANDLER,
        PACKET_LOSS,
    }

    companion object {
        lateinit var registry: MeterRegistry

        fun getCounter(action: Action): Counter {
            val builder = when (action) {
                Action.PACKET_HANDLER -> {
                    Counter.builder(PACKET_HANDLER_COUNTER)
                        .description(PACKET_HANDLER_COUNTER_DESC)
                }

                Action.PACKET_LOSS -> {
                    Counter.builder(PACKET_LOSS_COUNTER)
                        .description(PACKET_LOSS_COUNTER_DESC)
                }

                Action.PACKET_RECV -> {
                    Counter.builder(PACKET_RECV_COUNTER)
                        .description(PACKET_RECV_COUNTER_DESC)
                }
            }
            return builder.register(registry)
        }

        fun getSizeCounter(action: Action): Counter {
            val builder = when (action) {
                Action.PACKET_HANDLER -> {
                    Counter.builder(PACKET_HANDLER_SIZE_COUNTER)
                        .description(PACKET_HANDLER_SIZE_COUNTER_DESC)
                }

                else -> error("Not support action $action")
            }
            return builder.register(registry)
        }
    }
}
