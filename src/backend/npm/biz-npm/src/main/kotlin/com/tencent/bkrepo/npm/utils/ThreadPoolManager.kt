package com.tencent.bkrepo.npm.utils

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object ThreadPoolManager {
    /**
     * 根据cpu的数量动态的配置核心线程数和最大线程数
     */
    private val CPU_COUNT = Runtime.getRuntime().availableProcessors()
    /**
     * 核心线程数 = CPU核心数 + 1
     */
    private val CORE_POOL_SIZE = CPU_COUNT + 1
    /**
     * 线程池最大线程数 = CPU核心数 * 2 + 1
     */
    private val MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1
    /**
     * 非核心线程闲置时超时1s
     */
    private const val KEEP_ALIVE = 1L

    private val executor: ThreadPoolExecutor by lazy {
        ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE,
            KEEP_ALIVE, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(30),
            Executors.defaultThreadFactory(), ThreadPoolExecutor.AbortPolicy()
        )
    }

    /**
     * 开启一个无返回结果的线程
     * @param r
     */
    fun execute(r: Runnable) {
        // 把一个任务丢到了线程池中
        executor.execute(r)
    }

    /**
     * 开启一个有返回结果的线程
     * @param r
     * @return
     */
    fun <T> submit(r: Callable<T>): Future<T> {
        // 把一个任务丢到了线程池中
        return executor.submit(r)
    }

    /**
     *
     * 执行一组有返回值的任务
     * @param callableList 任务列表
     * @param timeout 任务超时时间，单位毫秒
     * @param <T>
     * @return
     */
    fun <T> execute(callableList: List<Callable<T>>, timeout: Long = 20L): List<T> {
        if (callableList.isEmpty()) {
            return emptyList()
        }
        val resultList = mutableListOf<T>()
        val futureList = mutableListOf<Future<T>>()
        callableList.forEach { callable ->
            val future: Future<T> = executor.submit(callable)
            futureList.add(future)
        }
        futureList.forEach { future ->
            try {
                val result: T = future.get(timeout, TimeUnit.HOURS)
                result?.let { resultList.add(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return resultList
    }

    /**
     * 把任务移除等待队列
     * @param r
     */
    fun cancel(r: Runnable?) {
        r?.let { executor.queue.remove(r) }
    }
}
