package com.tencent.bkrepo.common.mongo

import com.tencent.bkrepo.common.mongo.api.properties.MongoConnectionPoolProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import java.util.concurrent.TimeUnit

@Configuration
@PropertySource("classpath:common-mongo.properties")
@EnableConfigurationProperties(MongoConnectionPoolProperties::class)
class MongoReactiveAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun mongoClientCustomizer(
        mongoConnectionPoolProperties: MongoConnectionPoolProperties
    ): MongoClientSettingsBuilderCustomizer {
        return MongoClientSettingsBuilderCustomizer { clientSettingsBuilder ->
            clientSettingsBuilder.applyToConnectionPoolSettings {
                if (mongoConnectionPoolProperties.maxConnectionIdleTimeMS != 0L) {
                    it.maxConnectionIdleTime(
                        mongoConnectionPoolProperties.maxConnectionIdleTimeMS,
                        TimeUnit.MILLISECONDS
                    )
                }
                if (mongoConnectionPoolProperties.maxConnectionLifeTimeMS != 0L) {
                    it.maxConnectionLifeTime(
                        mongoConnectionPoolProperties.maxConnectionLifeTimeMS,
                        TimeUnit.MILLISECONDS
                    )
                }
            }
        }
    }
}
