/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.frpc

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.frpc.event.Event
import com.tencent.bkrepo.common.frpc.event.EventBus
import com.tencent.bkrepo.common.frpc.event.EventProcess
import com.tencent.bkrepo.common.frpc.event.GcPrepareAckEvent
import com.tencent.bkrepo.common.frpc.event.GcPrepareEvent
import com.tencent.bkrepo.common.frpc.event.GcRecoverEvent
import com.tencent.bkrepo.common.frpc.event.call.AckCall
import com.tencent.bkrepo.common.frpc.event.call.SimpleEventCall
import org.apache.commons.io.input.ReversedLinesFileReader
import org.slf4j.LoggerFactory
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * GC流程
 *
 * 负责对event.log进行清理。
 * 具体过程
 * 1. leader检测日志文件是否过大
 * 2. leader发布gc预处理事件
 * 3. 其他服务接受到gc预处理事件，停止发布事件，如果本地所有事件已经消费，返回预处理ack。
 * 4. leader等待其他服务进行预处理回复，如果超时等待，则回到第二步。
 * 5. 收到所有服务的应答后，进行文件清理。
 * 6. 清理完文件，发布gc恢复事件
 * 7. 其他服务接受到恢复事件，恢复发布事件。
 * */
class GcProcess(
    eventBus: FileEventBus,
    private val eventMessageConverter: EventMessageConverter<String>,
    private val maxLogSize: Long,
    private val gcTimeout: Long,
    private val serviceRegistry: ServiceRegistry<StorageServiceInstance>
) : SimpleEventCall<Event>(eventBus), EventProcess<Event> {
    private var phase: GcPhase? = null
    private val gcPrepareCall = GcPrepareCall(eventBus, gcTimeout)
    private val logFile = eventBus.logFile
    val leaderElectionProcess = LeaderElectionProcess(eventBus, serviceRegistry)
    private val localServiceId = serviceRegistry.getLocalService().id

    init {
        eventBus.register(leaderElectionProcess)
        executor.scheduleAtFixedRate(this::gc, 0, GC_PERIOD, TimeUnit.MILLISECONDS)
    }

    fun isInGc(): Boolean {
        return phase != null
    }

    fun gc() {
        val leader = leaderElectionProcess.leader
        // 只有leader才可以发起gc
        if (leader != null && leader == localServiceId) {
            if (logFile.length() > maxLogSize) {
                try {
                    startGc()
                } catch (e: Exception) {
                    logger.error("File event bus gc error.", e)
                }
            }
        }
    }

    override fun supportEvent(event: Event): Boolean {
        return event is GcPrepareEvent || event is GcRecoverEvent
    }

    override fun handler(event: Event) {
        if (event is GcPrepareEvent) {
            phase = GcPhase.GC_PREPARE
            // 返回自己处理的最后一个非gc事件
            // 从尾部开始逐行查看，确认当前事件除gc相关事件的是最后一行
            // 因为消息是读取最尾部的行，所以如果GcPrepareEvent是最后一个事件，且被处理了，那么在它之前的事件都被处理了。
            val lastEvent = getLastEventExcludeGc()
            require(lastEvent != null)
            if (lastEvent.id != event.id) {
                // 说明还有内容需要处理，不返回，等待新的GcPrepareEvent
                logger.info("There's still unconsumed event[${lastEvent.id}].")
            } else {
                // 返回准备好了
                logger.info("Ready for gc.")
                call(GcPrepareAckEvent(localServiceId, event.id))
            }
        }

        if (event is GcRecoverEvent) {
            logger.info("Gc successful,continue work.")
            phase = null
        }
    }

    fun getFile(): File {
        return logFile
    }

    private fun clear() {
        logger.info("Clear event log file.")
        RandomAccessFile(logFile, "rw").use {
            it.setLength(0)
        }
    }
    private fun startGc() {
        // 发起预gc 需要使用ack call
        var retry = 0
        while (true) {
            try {
                doGc()
                return
            } catch (e: TimeoutException) {
                if (++retry > MAX_RETRY_TIMES) {
                    throw e
                }
            }
        }
    }

    private fun doGc() {
        val start = System.currentTimeMillis()
        val preLogFileSize = logFile.length()
        logger.info("File[$logFile] size[$preLogFileSize] exceed max size[$maxLogSize],Start gc.")
        phase = GcPhase.GC_PREPARE
        val services = serviceRegistry.getServices().map { it.id }.toList()
        gcPrepareCall.call(services, GcPrepareEvent())
        phase = GcPhase.GC
        // gc
        clear()
        val curLogFileSize = logFile.length()
        phase = GcPhase.RECOVER
        // 发布gc recover事件
        call(GcRecoverEvent())
        phase = null
        val elapse = System.currentTimeMillis() - start
        logger.info("Success gc [$preLogFileSize->$curLogFileSize], elapse: $elapse ms.")
    }

    /**
     * 从尾部开始读取行，即每个事件，返回非GcPrepareOkEvent的第一个事件
     * */
    private fun getLastEventExcludeGc(): Event? {
        val reversedLinesFileReader = ReversedLinesFileReader(logFile, StandardCharsets.UTF_8)
        var line = reversedLinesFileReader.readLine()
        while (line != null) {
            val event = eventMessageConverter.fromMessage(line)
            if (event !is GcPrepareAckEvent) {
                return event
            }
            line = reversedLinesFileReader.readLine()
        }
        return null
    }

    enum class GcPhase {
        GC_PREPARE,
        GC,
        RECOVER
    }

    private class GcPrepareCall(eventBus: EventBus, timeout: Long) : AckCall<GcPrepareEvent, GcPrepareAckEvent>(
        eventBus,
        timeout
    ) {
        init {
            eventBus.register(this)
        }
        var requiredServices: List<Long> = listOf()
        fun call(requiredServices: List<Long>, event: GcPrepareEvent) {
            this.requiredServices = requiredServices.sorted()
            super.call(event)
        }

        override fun supportEvent(event: Event): Boolean {
            return event is GcPrepareAckEvent
        }

        override fun isComplete(acks: List<GcPrepareAckEvent>): Boolean {
            val okServices = acks.map { it.serviceId }
            return okServices.sorted() == requiredServices
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GcProcess::class.java)
        private val threadFactory = ThreadFactoryBuilder().setDaemon(true)
            .setNameFormat("file-event-bus-gc")
            .build()
        private val executor = Executors.newSingleThreadScheduledExecutor(threadFactory)
        private const val MAX_RETRY_TIMES = 3
        private const val GC_PERIOD = 3000L
    }
}
