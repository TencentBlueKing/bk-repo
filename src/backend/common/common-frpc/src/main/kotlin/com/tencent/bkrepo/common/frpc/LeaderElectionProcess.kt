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
import com.tencent.bkrepo.common.frpc.event.EventType
import com.tencent.bkrepo.common.frpc.event.LeaderEvent
import com.tencent.bkrepo.common.frpc.event.RequestVoteEvent
import com.tencent.bkrepo.common.frpc.event.VoteEvent
import com.tencent.bkrepo.common.frpc.event.call.AckCall
import com.tencent.bkrepo.common.frpc.event.call.SimpleEventCall
import com.tencent.bkrepo.common.frpc.event.handler.EventHandler
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.random.Random

/**
 * 负责Leader选举
 *
 * 具体过程
 * 1. 启动最早的服务，发起投票请求。
 * 2. 等待其他服务的投票结果。
 * 3. 当发起者全票通过时，发起者晋升为leader。
 * 5. 发布leader事件，其他服务接受leader。
 * 6. 定时发布leader心跳和检测leader，以维护leader信息。
 * */
class LeaderElectionProcess(
    eventBus: EventBus,
    val serviceRegistry: ServiceRegistry<StorageServiceInstance>,
) : SimpleEventCall<Event>(eventBus), EventHandler {

    private val requestVoteCall = GcRequestVoteCall(eventBus, REQUEST_TIMEOUT)
    var leader: Long? = null
    private val localService = serviceRegistry.getLocalService()
    val localServiceId = localService.id
    init {
        // 第一次延迟选举，等待服务注册
        executor.scheduleAtFixedRate(
            this::maintainLeader,
            MAINTAIN_LEADER_PERIOD,
            MAINTAIN_LEADER_PERIOD,
            TimeUnit.MILLISECONDS
        )
    }
    override fun supportEvent(event: Event): Boolean {
        return event is LeaderEvent || event is RequestVoteEvent
    }

    override fun handler(event: Event) {
        if (event is RequestVoteEvent) {
            // 投票给启动时间最早的服务
            val voteService = getEarliestStartService()!!
            val voteEvent = VoteEvent(voteService.id, localServiceId, event.id)
            call(voteEvent)
            return
        }
        if (event is LeaderEvent && leader == null) {
            leader = event.leaderId
            logger.info("Accept leader $leader")
        }
    }

    fun await(timeout: Long): Boolean {
        var took = 0L
        while (leader == null && took < timeout) {
            Thread.sleep(WAIT_INTERVAL)
            took += WAIT_INTERVAL
        }
        return took < timeout
    }

    private fun maintainLeader() {
        refreshLeader()
        if (leader == localServiceId) {
            call(LeaderEvent(localServiceId))
            return
        }
        // 开始竞选，只有启动时间最早的服务，才有资格发起选举
        val service = getEarliestStartService()
        if (leader == null && service?.id == localServiceId) {
            // 随机一段时间，减少碰撞
            Thread.sleep(Random.nextLong(3_000))
            // 已经选出了leader
            if (leader != null) {
                return
            }
            logger.info("Start process leader election.")
            try {
                val services = serviceRegistry.getServices().map { it.id }.toList()
                // 发起投票请求
                requestVoteCall.call(services, RequestVoteEvent())
                // 投票结果
                val candidate = requestVoteCall.voteResult.map { it.voteServiceId }.toSet()
                // 只有全票通过且唯一的候选者，投票才结束，否则重新发起
                if (candidate.size == 1) {
                    // 完成
                    leader = candidate.first()
                    // 发布leader信息
                    call(LeaderEvent(leader!!))
                    logger.info("Elected a leader $leader")
                } else {
                    logger.warn("Election failed,multiple candidate were chosen.")
                }
            } catch (e: TimeoutException) {
                logger.info("Vote timeout.", e)
            }
        }
    }

    private fun getEarliestStartService(): StorageServiceInstance? {
        return serviceRegistry.getServices().minByOrNull { it.id }
    }

    private fun refreshLeader() {
        val services = serviceRegistry.getServices().map { it.id }
        // leader自身不会与自身失联
        if (leader == localServiceId) {
            return
        }
        if (leader != null && !services.contains(leader)) {
            logger.info("Leader[$leader] disconnected.")
            leader = null
        }
    }

    private class GcRequestVoteCall(eventBus: EventBus, timeout: Long) : AckCall<RequestVoteEvent, VoteEvent>(
        eventBus,
        timeout
    ) {
        init {
            eventBus.register(this)
        }
        var requiredServices: List<Long> = listOf()
        var voteResult: List<VoteEvent> = listOf()
        fun call(requiredServices: List<Long>, event: RequestVoteEvent) {
            this.requiredServices = requiredServices.sorted()
            super.call(event)
        }

        override fun supportEvent(event: Event): Boolean {
            return event.type == EventType.VOTE.name
        }

        override fun isComplete(acks: List<VoteEvent>): Boolean {
            voteResult = acks
            val okServices = acks.map { it.serviceId }
            return okServices.sorted() == requiredServices
        }
    }
    companion object {
        private val logger = LoggerFactory.getLogger(LeaderElectionProcess::class.java)
        private val threadFactory = ThreadFactoryBuilder().setDaemon(true)
            .setNameFormat("leader-election")
            .build()
        private val executor = Executors.newSingleThreadScheduledExecutor(threadFactory)
        private const val MAINTAIN_LEADER_PERIOD = 3000L
        private const val REQUEST_TIMEOUT = 10_000L
        private const val WAIT_INTERVAL = 1000L
    }
}
