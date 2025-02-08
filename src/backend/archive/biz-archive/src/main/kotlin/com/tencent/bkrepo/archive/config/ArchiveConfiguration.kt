package com.tencent.bkrepo.archive.config

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.archive.core.provider.FileStorageFileProvider
import com.tencent.bkrepo.archive.core.provider.PriorityFileProvider
import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.common.security.http.core.HttpAuthSecurity
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Paths
import java.util.concurrent.PriorityBlockingQueue

@EnableConfigurationProperties(ArchiveProperties::class)
@Configuration
class ArchiveConfiguration {

    @Bean
    fun httpAuthSecurity(): HttpAuthSecurity {
        return HttpAuthSecurity().withPrefix("/archive")
    }

    @Bean
    fun fileProvider(archiveProperties: ArchiveProperties): PriorityFileProvider {
        with(archiveProperties.download) {
            val threadPool = ArchiveUtils.newFixedAndCachedThreadPool(
                ioThreads,
                ThreadFactoryBuilder().setNameFormat("archive-io-%d").build(),
                PriorityBlockingQueue(),
            )
            return FileStorageFileProvider(
                fileDir = Paths.get(path),
                highWaterMark = highWaterMark.toBytes(),
                lowWaterMark = lowWaterMark.toBytes(),
                executor = threadPool,
                checkInterval = healthCheckInterval,
            )
        }
    }
}
