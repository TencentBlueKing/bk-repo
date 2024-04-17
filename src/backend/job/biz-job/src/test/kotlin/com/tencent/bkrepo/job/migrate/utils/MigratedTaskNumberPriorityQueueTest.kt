package com.tencent.bkrepo.job.migrate.utils

import com.tencent.bkrepo.common.api.collection.concurrent.ConcurrentHashSet
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.random.Random

class MigratedTaskNumberPriorityQueueTest {
    @Test
    fun test() {
        val q = MigratedTaskNumberPriorityQueue()
        val executor = Executors.newFixedThreadPool(8)
        val migratedNumbers = ConcurrentHashSet<Long>()
        val futures = ArrayList<Future<*>>()
        for (i in 1 .. 100L) {
            val future = executor.submit {
                try {
                    // 模拟数据迁移
                    Thread.sleep(Random.nextLong(1, 5) * 500)
                } finally {
                    migratedNumbers.add(i)
                    q.offer(i)
                    if (i % 4 == 0L) {
                        val leftMax = q.updateLeftMax()
                        println("i[$i], leftMax[$leftMax], queueSize[${q.size()}] \n migrated: $migratedNumbers")
                        // 确认leftMax之前所有的任务号都已迁移完
                        for (migrated in 1 .. leftMax) {
                            assertTrue(migrated in migratedNumbers)
                        }
                    }
                }
            }
            futures.add(future)
        }
        futures.forEach { it.get() }
    }
}
