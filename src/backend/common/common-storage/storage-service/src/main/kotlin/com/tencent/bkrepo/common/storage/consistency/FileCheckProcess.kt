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

package com.tencent.bkrepo.common.storage.consistency

import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.frpc.event.Event
import com.tencent.bkrepo.common.frpc.event.EventBus
import com.tencent.bkrepo.common.frpc.event.EventProcess
import com.tencent.bkrepo.common.frpc.event.call.AckCall
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 文件检查流程
 *
 * 通过eventBus进行通信，检测文件是否在指定节点可读
 * */
class FileCheckProcess(
    eventBus: EventBus, // 事件总线
    timeout: Long, // 检查超时时间
    val checkSelf: Boolean = false // 是否需要检查本机
) : AckCall<FileCheckEvent, FileCheckAckEvent>(eventBus, timeout), EventProcess<FileCheckEvent> {
    /**
     * 检查请求上下文map
     * */
    private val checkContextMap = ConcurrentHashMap<String, FileCheckRequestContext>()

    /**
     * 本机地址
     * */
    private val localAddress = InetAddress.getLocalHost().hostAddress

    /**
     * 检测文件在指定节点可读
     * @param hosts 节点的主机ip
     * @param path 文件绝对路径
     * */
    fun check(hosts: List<String>, path: String) {
        // 请求主机为空，直接返回
        if (hosts.isEmpty()) {
            return
        }
        val event = FileCheckEvent(path = path)
        val context = initRequestContext(hosts.sorted())
        checkContextMap[event.id] = context
        try {
            super.call(event)
        } catch (e: TimeoutException) {
            val lose = context.loseAckHosts().toJsonString()
            logger.error("Lose acks in these hosts $lose.")
            throw e
        } finally {
            checkContextMap.remove(event.id)
        }
    }

    override fun ack(event: FileCheckAckEvent) {
        super.ack(event)
        with(event) {
            checkContextMap[id]?.let {
                val latency = System.currentTimeMillis() - it.startTime
                logger.info("Receive ack from $pub ,latency $latency ms")
            }
        }
    }

    override fun isComplete(acks: List<FileCheckAckEvent>): Boolean {
        if (acks.isEmpty()) {
            return false
        }
        val ackHosts = acks.map { it.pub }.toList()
        val id = acks.first().id
        val context = checkContextMap[id]
        require(context != null)
        context.ackHosts = ackHosts
        return context.hosts == ackHosts.sorted()
    }

    override fun supportEvent(event: Event): Boolean {
        return FileEventType.values().map { it.name }.contains(event.type)
    }

    override fun handler(event: Event) {
        logger.info("Accept event $event.")
        if (event is FileCheckAckEvent) {
            super.handler(event)
        }
        if (event is FileCheckEvent) {
            if (checkSelf || event.pub != localAddress) {
                val context = initFileCheckContext(event)
                checkFile(context)
            }
        }
    }

    /**
     * 检查文件是否存在
     *
     * 检查文件是否存在，存在则发布ack事件，
     * 否则间隔一段时间再次检查，直到超出最大检查次数
     * @param context 检查文件上下文
     * */
    private fun checkFile(context: FileCheckContext) {
        with(context) {
            if (++checkCounter > MAX_CHECK_TIMES) {
                logger.warn("After max check times[$MAX_CHECK_TIMES] has not found file[$path].")
                return
            }
            logger.info("Check[$checkCounter] whether the file[$path] exists.")
            if (Files.exists(Paths.get(path))) {
                logger.info("The file[$path] has been detected.")
                eventBus.publish(FileCheckAckEvent(eventId, path))
            } else {
                executor.schedule({ checkFile(context) }, CHECK_INTERVAL, TimeUnit.MILLISECONDS)
            }
        }
    }

    /**
     * 初始化请求上下文
     * */
    private fun initRequestContext(hosts: List<String>): FileCheckRequestContext {
        return FileCheckRequestContext(hosts = hosts, ackHosts = null)
    }

    /**
     * 初始化检查文件上下文
     * */
    private fun initFileCheckContext(event: FileCheckEvent): FileCheckContext {
        with(event) {
            return FileCheckContext(
                eventId = id,
                path = path,
                checkCounter = 0
            )
        }
    }

    /**
     * 文件检查请求上下文
     * */
    private data class FileCheckRequestContext(
        val hosts: List<String>, // 请求的host
        var ackHosts: List<String>?, // 收到响应的host
        val startTime: Long = System.currentTimeMillis() // 请求开始时间
    ) {
        fun loseAckHosts(): List<String> {
            val unAcks = hosts.toMutableList()
            ackHosts?.let { unAcks.removeAll(it) }
            return unAcks
        }
    }

    /**
     * 文件检查上下文
     * */
    private data class FileCheckContext(
        val eventId: String, // 事件id
        val path: String, // 文件路径
        var checkCounter: Int // 检查计数器
    )

    companion object {
        private val logger = LoggerFactory.getLogger(FileCheckProcess::class.java)
        private val executor = Executors.newSingleThreadScheduledExecutor()
        private const val MAX_CHECK_TIMES = 5
        private const val CHECK_INTERVAL = 1000L
    }
}
