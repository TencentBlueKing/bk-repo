package com.tencent.bkrepo.common.storage

import com.tencent.bkrepo.common.storage.innercos.InnerCosFileStorage
import com.tencent.bkrepo.common.storage.innercos.InnerCosProperties
import com.tencent.bkrepo.common.storage.local.LocalFileStorage
import com.tencent.bkrepo.common.storage.local.LocalStorageProperties
import com.tencent.bkrepo.common.storage.strategy.HashLocateStrategy
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 存储自动配置
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
@Configuration
@EnableConfigurationProperties(InnerCosProperties::class, LocalStorageProperties::class)
class StorageAutoConfiguration {

    @Autowired
    lateinit var locateStrategy: LocateStrategy

    @Bean
    fun locateStrategy() = HashLocateStrategy()

    @Bean
    @ConditionalOnProperty(prefix = "storage.innercos", name = ["enabled"], havingValue = "true")
    fun innerCosFileStorage(innerCosProperties: InnerCosProperties) = InnerCosFileStorage(locateStrategy, innerCosProperties.credentials)

    @Bean
    @ConditionalOnProperty(prefix = "storage.local", name = ["enabled"], havingValue = "true")
    fun localFileStorage(localStorageProperties: LocalStorageProperties) = LocalFileStorage(locateStrategy, localStorageProperties.credentials)
}
