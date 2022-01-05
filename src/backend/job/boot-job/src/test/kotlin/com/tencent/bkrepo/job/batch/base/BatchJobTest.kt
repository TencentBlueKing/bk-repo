package com.tencent.bkrepo.job.batch.base

import com.tencent.bkrepo.job.executor.BlockThreadPoolTaskExecutorDecorator
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@Import(
    TaskExecutionAutoConfiguration::class
)
@TestPropertySource(locations = ["classpath:bootstrap.properties"])
class BatchJobTest {

    @Autowired
    private lateinit var executor: BlockThreadPoolTaskExecutorDecorator

    @DisplayName("测试最大任务数")
    @Test
    fun maxAvailable() {

 /*       // 启动一个线程占满配额
        thread {
            job.runAsync((0 until executor.maxAvailable), executor = executor) {
                TimeUnit.MILLISECONDS.sleep(100)
            }
        }
        // 停止100ms，让前面的线程有足够的时间去占满配额
        TimeUnit.MILLISECONDS.sleep(100)
        val putOk = AtomicInteger()
        val putNum = 8
        // 启动多个线程去跑异步任务
        repeat(putNum) {
            thread {
                job.runAsync((100000 + it until 100001 + it), false, executor = executor) {}
                putOk.incrementAndGet()
            }
        }
        // 因为前面的任务还未执行完，没有获取到配额，所以此时任务放进队列的数量应该为0
        Assertions.assertEquals(0, putOk.get())
        TimeUnit.MILLISECONDS.sleep(100)
        // 等待一段时间后，任务全部获取到配额
        Assertions.assertEquals(putNum, putOk.get())*/
    }
}
