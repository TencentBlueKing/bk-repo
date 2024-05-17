package com.tencent.bkrepo.archive.core.provider

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class ConcurrentFileProviderTest {
    @Test
    fun concurrentTest() {
        val fp = mockk<FileProvider<Int>>()
        val getCount = AtomicInteger()
        every { fp.get(any()) } answers {
            getCount.incrementAndGet()
            Mono.just(File(""))
        }
        every { fp.key(any()) } returns "key"
        val provider = ConcurrentFileProvider(fp)
        val threads = mutableListOf<Thread>()
        repeat(3) { idx ->
            thread {
                provider.get(idx).subscribe { println("Get $idx") }
            }.apply {
                threads.add(this)
            }
        }
        threads.forEach {
            it.join()
        }
        Assertions.assertEquals(1, getCount.get())
    }
}
