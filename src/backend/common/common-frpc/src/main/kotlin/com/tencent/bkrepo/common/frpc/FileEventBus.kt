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

import com.tencent.bkrepo.common.frpc.event.Event
import com.tencent.bkrepo.common.frpc.event.EventBus
import com.tencent.bkrepo.common.frpc.event.EventType
import com.tencent.bkrepo.common.frpc.event.GcPrepareEvent
import com.tencent.bkrepo.common.frpc.event.GcRecoverEvent
import com.tencent.bkrepo.common.frpc.event.handler.EventHandler
import org.apache.commons.io.input.Tailer
import org.apache.commons.io.input.TailerListenerAdapter
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.system.measureTimeMillis

/**
 * 文件事件总线
 *
 * 通过对文件进行尾部写入与读取，实现事件的发布与消费
 * */
class FileEventBus(
    /**
     * 事件log文件的存放目录路径
     * */
    logDirPath: String,
    /**
     * 读取文件的延迟，影响事件的消费速度
     * */
    delayMillis: Long,
    /**
     * 消息转换器
     * */
    val eventMessageConverter: EventMessageConverter<String>,
    private val gcTimeout: Long
) : EventBus {

    /**
     * event日志文件
     * */
    val logFile: File

    /**
     * 文件跟踪器
     * */
    private val tailer: Tailer

    /**
     * 事件处理器
     * */
    private val handlers = mutableListOf<EventHandler>()
    private val gcHandler = GcHandler()

    init {
        val logFilePath = Paths.get(logDirPath, EVENT_LOG_FILE_NAME)
        // 创建父目录
        if (!Files.exists(logFilePath.parent)) {
            Files.createDirectories(logFilePath.parent)
        }
        // 创建log文件
        if (!Files.exists(logFilePath)) {
            Files.createFile(logFilePath)
        }
        logFile = logFilePath.toFile()
        tailer = Tailer(logFile, EventConsumer(), delayMillis, true, false, DEFAULT_BUFFER_SIZE)
        thread(start = true, isDaemon = true, name = "file-event-consumer") {
            tailer.run()
        }
        register(gcHandler)
    }

    override fun publish(event: Event): Boolean {
        if (isNotGcEvent(event) && gcHandler.inGc) {
            // stop the world ，超时就丢弃掉事件
            logger.info("Await gc.")
            val wait = gcHandler.await(gcTimeout, TimeUnit.MILLISECONDS)
            if (!wait) {
                logger.info("Gc timeout,discard event.")
                return false
            }
        }
        // write event
        val message = eventMessageConverter.toMessage(event)
        logFile.appendText(message + "\r\n")
        if (logger.isTraceEnabled) {
            logger.trace("Publish>> $message")
        }
        return true
    }

    override fun register(eventHandler: EventHandler) {
        logger.info("Add event handler ${eventHandler::class.java.simpleName}")
        handlers.add(eventHandler)
    }

    fun stop() {
        tailer.stop()
    }

    private fun isNotGcEvent(event: Event): Boolean {
        return !setOf(
            EventType.GC_PREPARE_ACK.name,
            EventType.GC_PREPARE.name,
            EventType.GC_RECOVER.name
        ).contains(event.type)
    }

    inner class EventConsumer : TailerListenerAdapter() {
        override fun handle(line: String) {
            val event: Event
            if (logger.isTraceEnabled) {
                logger.trace("Receive<< $line")
            }
            try {
                event = eventMessageConverter.fromMessage(line)!!
                // 找到对应的handler
                val handlers = handlers.filter { it.supportEvent(event) }
                if (handlers.isEmpty()) {
                    logger.warn("No handler support ${event.type} event.")
                } else {
                    handlers.forEach { it.handler(event) }
                }
            } catch (e: Exception) {
                logger.error("Handler event error", e)
            }
        }

        override fun fileRotated() {
            logger.info("File rotated,current size ${logFile.length()}.")
        }
    }

    private class GcHandler : EventHandler {
        var inGc: Boolean = false

        private val lock = ReentrantLock()
        private val condition = lock.newCondition()
        fun await(timeout: Long, unit: TimeUnit): Boolean {
            lock.withLock {
                val ret: Boolean
                measureTimeMillis {
                    ret = condition.await(timeout, unit)
                }.apply {
                    logger.info("Gc activity took $this ms.")
                }
                return ret
            }
        }
        override fun supportEvent(event: Event): Boolean {
            return event is GcPrepareEvent || event is GcRecoverEvent
        }

        override fun handler(event: Event) {
            if (event is GcPrepareEvent) {
                inGc = true
                logger.info("Start gc.")
            }
            if (event is GcRecoverEvent) {
                logger.info("Finish gc.")
                inGc = false
                lock.withLock {
                    condition.signalAll()
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileEventBus::class.java)
        private const val EVENT_LOG_FILE_NAME = "event.log"
    }
}
