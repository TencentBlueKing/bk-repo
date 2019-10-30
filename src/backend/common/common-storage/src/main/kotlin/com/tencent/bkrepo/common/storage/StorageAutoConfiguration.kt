package com.tencent.bkrepo.common.storage

import com.tencent.bkrepo.common.storage.cache.FileCache
import com.tencent.bkrepo.common.storage.cache.local.LocalFileCache
import com.tencent.bkrepo.common.storage.cache.local.LocalFileCacheProperties
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.innercos.InnerCosFileStorage
import com.tencent.bkrepo.common.storage.innercos.InnerCosProperties
import com.tencent.bkrepo.common.storage.local.LocalFileStorage
import com.tencent.bkrepo.common.storage.local.LocalStorageProperties
import com.tencent.bkrepo.common.storage.schedule.StorageSchedule
import com.tencent.bkrepo.common.storage.strategy.HashLocateStrategy
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 存储自动配置
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
@Configuration
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(
        InnerCosProperties::class,
        LocalStorageProperties::class,
        LocalFileCacheProperties::class)
class StorageAutoConfiguration {

    @Autowired
    lateinit var locateStrategy: LocateStrategy

    @Bean
    fun locateStrategy() = HashLocateStrategy()

    @Bean("innerCosFileStorage")
    @ConditionalOnProperty(prefix = "storage", name = ["type"], havingValue = "innercos")
    fun innerCosFileStorage(fileCache: FileCache, innerCosProperties: InnerCosProperties): FileStorage {
        logger.info("Initializing FileStorage 'innerCosFileStorage'")
        return InnerCosFileStorage(fileCache, locateStrategy, innerCosProperties)
    }

    @Bean("localFileStorage")
    @ConditionalOnMissingBean(FileStorage::class)
    fun localFileStorage(localStorageProperties: LocalStorageProperties): FileStorage {
        logger.info("Initializing FileStorage 'localFileStorage'")
        return LocalFileStorage(locateStrategy, localStorageProperties)
    }

    @Bean
    @ConditionalOnMissingBean(name = ["fileCache", "localFileStorage"])
    fun fileCache(localFileCacheProperties: LocalFileCacheProperties): FileCache {
        logger.info("Initializing FileCache 'localFileCache'")
        return LocalFileCache(localFileCacheProperties)
    }

    @Bean
    @ConditionalOnBean(FileCache::class)
    fun storageSchedule(fileCache: FileCache): StorageSchedule {
        logger.info("Initializing StorageSchedule 'storageSchedule'")
        return StorageSchedule(fileCache)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StorageAutoConfiguration::class.java)
    }
}
