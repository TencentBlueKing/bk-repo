package com.tencent.bkrepo.npm.utils

import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.npm.service.DataMigrationService
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object ThreadPoolManager {
    private var asyncExecutor: ThreadPoolTaskExecutor

    init {
        asyncExecutor = SpringContextUtils.getBean(ThreadPoolTaskExecutor::class.java, "npmTaskAsyncExecutor")
    }

    /**
     *
     * 执行一组有返回值的任务
     * @param callableList 任务列表
     * @param timeout 任务超时时间，单位毫秒
     * @param <T>
     * @return
     */
    fun <T> submit(callableList: List<Callable<T>>, timeout: Long = 10L): List<T> {
        if (callableList.isEmpty()) {
            return emptyList()
        }
        val resultList = mutableListOf<T>()
        val futureList = mutableListOf<Future<T>>()
        callableList.forEach { callable ->
            val future: Future<T> = asyncExecutor.submit(callable)
            futureList.add(future)
        }
        futureList.forEach { future ->
            try {
                val result: T = future.get(timeout, TimeUnit.MINUTES)
                result?.let { resultList.add(it) }
            } catch (exception: TimeoutException) {
                DataMigrationService.logger.error("async tack result timeout: ${exception.message}")
            } catch (ex: InterruptedException) {
                DataMigrationService.logger.error("get async task result error : ${ex.message}")
            } catch (ex: ExecutionException) {
                DataMigrationService.logger.error("get async task result error : ${ex.message}")
            }
        }
        return resultList
    }
}
