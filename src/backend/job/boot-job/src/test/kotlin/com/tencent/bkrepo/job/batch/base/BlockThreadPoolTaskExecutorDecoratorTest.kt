package com.tencent.bkrepo.job.batch.base

import com.tencent.bkrepo.job.executor.BlockThreadPoolTaskExecutorDecorator
import com.tencent.bkrepo.job.executor.IdentityTask
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.jupiter.api.assertThrows

class BlockThreadPoolTaskExecutorDecoratorTest {

    @Test
    fun maxAvailableTest() {
        val executor = BlockThreadPoolTaskExecutorDecorator(Executors.newSingleThreadExecutor(), 20)
        repeat(20) {
            executor.execute {
                println("start")
                TimeUnit.MILLISECONDS.sleep(100)
                println("end")
            }
        }
        val add = AtomicInteger()
        thread {
            executor.execute {}
            println("add")
            add.incrementAndGet()
        }
        Assertions.assertEquals(0, add.get())
        TimeUnit.MILLISECONDS.sleep(150)
        Assertions.assertEquals(1, add.get())
    }

    @Test
    fun idTaskTest() {
        val executor = BlockThreadPoolTaskExecutorDecorator(Executors.newSingleThreadExecutor(), 20)
        val id = "id"
        repeat(2) {
            val task = IdentityTask(
                id,
                Runnable {
                    println("$it start ${LocalDateTime.now()}")
                    TimeUnit.MILLISECONDS.sleep(100)
                    println("$it end ${LocalDateTime.now()}")
                }
            )
            executor.executeWithId(task)
        }
        executor.complete(id)
        val num = AtomicInteger()
        thread {
            executor.get(id, 1000)
            num.incrementAndGet()
        }
        Assertions.assertEquals(0, num.get())
        TimeUnit.MILLISECONDS.sleep(300)
        Assertions.assertEquals(1, num.get())
    }

    @Test
    fun deadLock() {
        // 线程池执行一个任务，这个任务会产生新的任务。在任务达到限制数量时，会进行等待。
        // 依次类推，线程池的工作线程被这些生产任务占满，同时这些生产任务又在等待线程池里工作线程结束任务，产生死锁。
        val executor = BlockThreadPoolTaskExecutorDecorator(Executors.newSingleThreadExecutor(), 1)
        val num = AtomicInteger()
        executor.execute {
            // 已经达到限制，此时生产一个任务，即会进行等待自身。导致死锁。
            executor.execute { }
            num.incrementAndGet()
        }
        TimeUnit.MILLISECONDS.sleep(100)
        Assertions.assertEquals(0, num.get())
    }

    @Test
    fun noDeadLock() {
        val executor = BlockThreadPoolTaskExecutorDecorator(Executors.newSingleThreadExecutor(), 1)
        val num = AtomicInteger()
        // 使用executeProduce避免死锁
        val r = Runnable {
            executor.execute { }
            num.incrementAndGet()
        }
        executor.executeProduce(r)
        TimeUnit.MILLISECONDS.sleep(100)
        Assertions.assertEquals(1, num.get())
    }

    @Test
    fun waitTimeoutTest() {
        val executor = BlockThreadPoolTaskExecutorDecorator(Executors.newSingleThreadExecutor(), 1)
        val timeout = 1000L
        val identityTask = IdentityTask(id = "id", runnable = Runnable { TimeUnit.MILLISECONDS.sleep(timeout * 2) })
        executor.executeWithId(identityTask)
        assertThrows<TimeoutException> { executor.completeAndGet(identityTask.id, timeout) }
    }

    @Test
    fun rateLimitTest() {
        val executor = BlockThreadPoolTaskExecutorDecorator(Executors.newFixedThreadPool(100), 100)
        val id = "id"
        val begin = System.currentTimeMillis()
        repeat(100) {
            val identityTask = IdentityTask(id = id, runnable = Runnable { TimeUnit.MILLISECONDS.sleep(100) })
            executor.executeWithId(identityTask, permitsPerSecond = 30.0)
        }
        executor.completeAndGet(id, 5000)
        val end = System.currentTimeMillis()
        val spend = end - begin
        println(spend)
        Assertions.assertTrue(spend > 3000)
    }
}
