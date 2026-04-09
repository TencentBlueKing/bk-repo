package com.tencent.bkrepo.common.metadata.service.separation.impl

import com.tencent.bkrepo.common.metadata.config.DataSeparationConfig
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryServiceNoopStub
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.time.Duration

@TestConfiguration
class SeparationTaskServiceImplMongoTestConfig {

    @Bean
    fun dataSeparationConfigForSeparationTaskIt(): DataSeparationConfig =
        DataSeparationConfig(keepDays = Duration.ofDays(30))

    @Bean
    fun repositoryServiceForSeparationTaskIt(): RepositoryService = RepositoryServiceNoopStub
}
