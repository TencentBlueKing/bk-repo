package com.tencent.bkrepo.archive.job

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class BufferedResourceManagerTest {
    private lateinit var queue: BlockingQueue<Int>
    private lateinit var manager: BufferedResourceManager<Int>

    @BeforeEach
    fun setUp() {
        queue = LinkedBlockingQueue()
        manager = object : BufferedResourceManager<Int>(queue, 2) {
            override fun process0(resource: Int): Mono<TaskResult> =
                Mono.delay(Duration.ofMillis(200)).map {
                    println("Receive $resource")
                    TaskResult.OK
                }
        }
    }

    @Test
    fun `process should return OK when buffer has permits`() {
        val resource = 1
        assertEquals(TaskResult.OK, manager.process(resource).block())
        assertNull(queue.poll())
    }

    @Test
    fun `process should return QUEUED when no permits`() {
        val resource = 1
        repeat(2) { manager.process(resource).subscribe() }
        assertEquals(TaskResult.QUEUED, manager.process(resource).block())
        assertEquals(1, queue.size)
    }

    @Test
    fun `remainingCapacity should return the correct value`() {
        assertEquals(2, manager.remainingCapacity())

        val resource = 1
        manager.process(resource).subscribe()
        assertEquals(1, manager.remainingCapacity())

        manager.process(resource).subscribe()
        assertEquals(0, manager.remainingCapacity())
    }

    @Test
    fun `queue should consume continue when has permits`() {
        repeat(2) { queue.offer(it) }
        val resource = 100
        assertEquals(TaskResult.OK, manager.process(resource).block())
        Thread.sleep(1000)
        assertEquals(0, queue.size)
    }

    @Test
    fun `queue should consume continue when no permits`() {
        repeat(2) { queue.offer(it) }
        val resource = 100
        manager.process(resource).subscribe()
        manager.process(resource).subscribe()
        assertEquals(TaskResult.QUEUED, manager.process(resource).block())
        Thread.sleep(1000)
        assertEquals(0, queue.size)
    }

    @Test
    fun `cache ex when manager stop`() {
        manager.stop()
        val resource = 1
        assertThrows<RuntimeException>("Resource manager is closed") { manager.process(resource).block() }
    }
}
