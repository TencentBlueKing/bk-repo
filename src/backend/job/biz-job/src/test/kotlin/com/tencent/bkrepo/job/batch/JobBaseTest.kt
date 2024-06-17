/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.common.job.JobAutoConfiguration
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.StorageAutoConfiguration
import com.tencent.bkrepo.job.config.JobConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
import org.springframework.cloud.sleuth.Tracer
import org.springframework.cloud.sleuth.otel.bridge.OtelTracer
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

@Import(
    JobAutoConfiguration::class,
    TaskExecutionAutoConfiguration::class,
    JobConfig::class,
    TaskSchedulingAutoConfiguration::class,
    ClusterProperties::class,
    RedisAutoConfiguration::class,
    StorageAutoConfiguration::class,
)
@TestPropertySource(
    locations = [
        "classpath:bootstrap-ut.properties",
        "classpath:job-ut.properties"
    ]
)
@ComponentScan(basePackages = ["com.tencent.bkrepo.job"])
@SpringBootConfiguration
@EnableAutoConfiguration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobBaseTest {
    @BeforeAll
    fun commonMock() {
        val tracer = mockk<OtelTracer>()
        mockkObject(SpringContextUtils.Companion)
        every { SpringContextUtils.getBean<Tracer>() } returns tracer
        every { tracer.currentSpan() } returns null
        every { SpringContextUtils.publishEvent(any()) } returns Unit
    }
}
