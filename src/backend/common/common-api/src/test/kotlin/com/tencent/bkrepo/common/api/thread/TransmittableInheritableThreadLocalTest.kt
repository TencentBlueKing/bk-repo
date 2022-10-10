package com.tencent.bkrepo.common.api.thread

import java.util.concurrent.Executors
import kotlin.concurrent.thread
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TransmittableInheritableThreadLocalTest {
    @Test
    fun transmittableTest() {
        val ttl = TransmittableInheritableThreadLocal<String>()
        val value = "Hello"
        ttl.set(value)
        // 父子线程传播
        thread {
            Assertions.assertEquals(value, ttl.get())
        }

        // 线程池传播
        val executor = Executors.newSingleThreadExecutor()
        val task = TransmitterRunnableWrapper {
            Assertions.assertEquals(value, ttl.get())
        }
        executor.execute(task)

        // 当前线程
        Assertions.assertEquals(value, ttl.get())

        // 普通runnable不进行传播
        executor.execute {
            Assertions.assertEquals(null, ttl.get())
        }

        // 普通runnable传播
        val transmitterExecutor = TransmitterExecutorWrapper(executor)
        transmitterExecutor.execute {
            Assertions.assertEquals(value, ttl.get())
        }
    }
}
