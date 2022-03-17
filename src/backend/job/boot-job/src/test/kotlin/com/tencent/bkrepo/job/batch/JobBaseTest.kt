package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.common.job.JobAutoConfiguration
import com.tencent.bkrepo.job.config.JobConfig
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

@Import(
    JobAutoConfiguration::class,
    TaskExecutionAutoConfiguration::class,
    JobConfig::class
)
@TestPropertySource(
    locations = [
        "classpath:bootstrap-ut.properties",
        "classpath:bootstrap.properties",
        "classpath:job-ut.properties"
    ]
)
open class JobBaseTest
