package com.tencent.bkrepo.archive.job

import com.tencent.bkrepo.archive.job.BaseJobSubscriber.Companion.JOB_CTX
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class JobSubscriberTest {
    @Test
    fun commonJobSubscriberTest() {
        val job = object : BaseJobSubscriber<String>() {
            override fun doOnNext(value: String) {
                Thread.sleep(1000)
                println(value)
            }
        }
        thread {
            Flux.range(0, 3)
                .map { it.toString() }
                .subscribe(job)
        }
        val context: JobContext = job.currentContext().get(JOB_CTX)
        Assertions.assertEquals(0L, context.success.get())
        job.blockLast()
        Assertions.assertEquals(3L, context.success.get())
        Assertions.assertEquals(3L, context.total.get())
        Assertions.assertEquals(0L, context.failed.get())
    }

    @Test
    fun asyncJobSubscriberTest() {
        val threadPoolExecutor = ThreadPoolExecutor(
            3,
            3,
            0,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(3),
        )
        val job = object : AsyncBaseJobSubscriber<String>(threadPoolExecutor) {
            override fun doOnNext(value: String) {
                Thread.sleep(1000)
                println("${Thread.currentThread().name} $value")
            }
        }
        val startAt = System.currentTimeMillis()
        thread {
            Flux.range(0, 3)
                .map { it.toString() }
                .subscribe(job)
        }
        val context: JobContext = job.currentContext().get(JOB_CTX)
        Assertions.assertEquals(0L, context.success.get())
        job.blockLast()
        Assertions.assertEquals(3L, context.success.get())
        Assertions.assertEquals(3L, context.total.get())
        Assertions.assertEquals(0L, context.failed.get())
        Assertions.assertTrue(System.currentTimeMillis() - startAt < 2000)
    }

    @Test
    fun cancelTest() {
        val count = AtomicInteger()
        val job = object : BaseJobSubscriber<String>() {
            override fun doOnNext(value: String) {
                println("${Thread.currentThread().name}: $value")
                count.incrementAndGet()
            }
        }
        Flux.range(0, 10)
            .map { it.toString() }
            .delayElements(Duration.ofSeconds(1))
            .subscribe(job)
        thread {
            job.blockLast()
            println("Job finish")
        }
        Thread.sleep(5000)
        job.dispose()
        if (job.isDisposed) {
            println("Job Disposed")
        }
        Assertions.assertEquals(4, count.get())
    }
}
