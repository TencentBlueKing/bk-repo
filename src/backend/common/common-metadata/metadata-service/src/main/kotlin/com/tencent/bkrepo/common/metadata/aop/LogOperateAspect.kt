/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.aop

import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.metadata.annotation.LogOperate
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.pojo.log.OperateLog
import com.tencent.bkrepo.common.metadata.util.DesensitizedUtils.convertMethodArgsToMap
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 记录被[LogOperate]注解的类或方法操作日志
 */
@Component
@Aspect
@Conditional(SyncCondition::class)
class LogOperateAspect(private val operateLogService: OperateLogService) {
    @Volatile
    private var operateLogBuffer: MutableSet<OperateLog> = ConcurrentHashMap.newKeySet(LOG_BUFFER_SIZE)

    /**
     * 用于控制在创建新的[operateLogBuffer]期间，不允许往[operateLogBuffer]中写内容
     * 避免替换新的[operateLogBuffer]后依然有线程持有旧的引用并往其中写内容
     */
    private val bufferRefreshLock = ReentrantReadWriteLock()

    @Around(
        "@within(com.tencent.bkrepo.common.metadata.annotation.LogOperate) " +
            "|| @annotation(com.tencent.bkrepo.common.metadata.annotation.LogOperate)"
    )
    @Suppress("TooGenericExceptionCaught")
    fun around(joinPoint: ProceedingJoinPoint): Any? {
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method
        val annotation = method.getAnnotation(LogOperate::class.java)

        val args = joinPoint.args
        val methodName = "${method.declaringClass.name}#${method.name}"

        val operateType = annotation.type.ifEmpty { methodName }
        val desensitize = annotation.desensitize
        val userId = SecurityUtils.getUserId()
        val clientAddr = HttpContextHolder.getClientAddress()
        try {
            val descriptions = convertMethodArgsToMap(method, args, desensitize)
            saveOperateLog(operateType, userId, clientAddr, descriptions)
        } catch (e: Exception) {
            logger.error("save audit log failed, user[$userId] invoke method[$methodName]", e)
        }
        return joinPoint.proceed()
    }

    /**
     * 保存操作日志到[operateLogBuffer]中，[operateLogBuffer]会定时或者在容量已满时保存到数据库
     */
    @Suppress("UNCHECKED_CAST")
    fun saveOperateLog(type: String, userId: String, clientAddr: String, descriptions: Map<String, Any?>) {
        val operateLog = OperateLog(
            type = type,
            projectId = "",
            repoName = "",
            resourceKey = "",
            userId = userId,
            clientAddress = clientAddr,
            description = descriptions.filterValues { it != null } as Map<String, Any>
        )
        if (operateLogBuffer.size >= LOG_BUFFER_SIZE) {
            flush(false)
        }
        // 允许多线程同时写，buffer size少量超过限制的大小
        // 此处加读写锁控制[operateLogBuffer]的刷新与元素添加两个操作不可同时执行
        bufferRefreshLock.read { operateLogBuffer.add(operateLog) }

        if (logger.isDebugEnabled) {
            logger.debug("save operate log[$operateLog]")
        }
    }

    /**
     * 将[operateLogBuffer]的数据保存到数据库
     *
     * @param force 是否[operateLogBuffer]未满时也强制保存到数据库
     */
    @Synchronized
    fun flush(force: Boolean = true) {
        if (operateLogBuffer.isEmpty() || !force && operateLogBuffer.size < LOG_BUFFER_SIZE) {
            return
        }
        val current = operateLogBuffer
        bufferRefreshLock.write { operateLogBuffer = ConcurrentHashMap.newKeySet(LOG_BUFFER_SIZE) }
        operateLogService.saveAsync(current)
        if (logger.isDebugEnabled) {
            logger.debug("save ${current.size} operate logs success.")
        }
    }

    @Scheduled(fixedRate = FLUSH_LOG_RATE)
    fun forceFlush() {
        flush()
    }

    companion object {
        private const val FLUSH_LOG_RATE = 60L * 1000L
        private const val LOG_BUFFER_SIZE = 1000
        private val logger = LoggerFactory.getLogger(LogOperateAspect::class.java)
    }
}
