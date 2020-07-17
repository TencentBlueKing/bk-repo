package com.tencent.bkrepo.replication.job

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest


@DataMongoTest
internal class  RealTimeJobTest {

    @Test
    @DisplayName("测试container执行状态")
    fun testContainerStatus(){
        val job = RealTimeJob()
        job.run()
        job.getContainerStatus()
    }
}