package com.tencent.bkrepo.job.migrate.pojo

import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.buildTask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

@DisplayName("迁移上下文测试")
class MigrationContextTest {

    @Test
    fun testWaitTransferring() {
        val task = buildTask()
        val context = MigrationContext(task, null, null)
        val executor = Executors.newFixedThreadPool(8)
        val count = 8L
        val executedCount = AtomicLong(0L)
        for (i in 0 until count) {
            context.incTransferringCount()
            executor.execute {
                Thread.sleep(1000L)
                context.decTransferringCount()
                executedCount.incrementAndGet()
            }
        }
        assertNotEquals(count, executedCount.get())
        assertNotEquals(0L, context.transferring())

        // 等待执行结束
        context.waitAllTransferFinished()
        assertEquals(count, executedCount.get())
        assertEquals(0L, context.transferring())
    }
}
