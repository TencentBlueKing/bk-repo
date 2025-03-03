/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.analyst.configuration

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClients
import com.tencent.bkrepo.analysis.executor.api.ExecutorClient
import com.tencent.bkrepo.analyst.dispatcher.SubtaskDispatcherFactory
import com.tencent.bkrepo.analyst.dispatcher.SubtaskPoller
import com.tencent.bkrepo.analyst.event.AnalystScanEventConsumer
import com.tencent.bkrepo.analyst.service.ExecutionClusterService
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.mongo.MongoProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.SpringDataMongoDB
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.function.Consumer

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    ScannerProperties::class,
    ReportExportProperties::class,
)
@LoadBalancerClients(defaultConfiguration = [AnalysisLoadBalancerConfiguration::class])
@Suppress("LongParameterList")
class ScannerConfiguration {
    @Bean
    fun poller(
        subtaskDispatcherFactory: SubtaskDispatcherFactory,
        executionClusterService: ExecutionClusterService,
        scannerService: ScannerService,
        executorClient: ObjectProvider<ExecutorClient>,
        executor: ThreadPoolTaskExecutor,
    ): SubtaskPoller {
        return SubtaskPoller(
            subtaskDispatcherFactory,
            executionClusterService,
            scannerService,
            executorClient,
            executor
        )
    }

//    @Bean
//    @ConditionalOnNotAssembly // 仅在非单体包部署时创建，避免循环依赖问题
//    fun operateLogService(operateLogClient: OperateLogClient): OperateLogService {
//        return OperateLogServiceImpl(operateLogClient)
//    }
//
//    @Bean
//    @ConditionalOnNotAssembly // 仅在非单体包部署时创建，避免循环依赖问题
//    fun projectUsageStatisticsService(
//        client: ObjectProvider<ProjectUsageStatisticsClient>
//    ): ProjectUsageStatisticsService {
//        return ProjectUsageStatisticsServiceImpl(client)
//    }


    @Bean("scanEventConsumer")
    fun scanEventConsumer(
        analystScanEventConsumer: AnalystScanEventConsumer
    ): Consumer<ArtifactEvent> {
        return Consumer {
            analystScanEventConsumer.accept(it)
        }
    }

    @Bean
    @ConfigurationProperties("spring.data.mongodb.analyst")
    fun analystMongoProperties(): MongoProperties {
        return MongoProperties()
    }

    @Bean
    @DependsOn("mongoTemplate")
    fun analystMongoTemplate(
        mongoTemplate: MongoTemplate,
        @Qualifier("analystMongoProperties") analystMongoProperties: MongoProperties,
        converter: MappingMongoConverter?
    ): MongoTemplate {
        // 没有设置独立数据库时，使用统一的数据库
        return if (analystMongoProperties.determineUri() == MongoProperties.DEFAULT_URI) {
            mongoTemplate
        } else {
            val connectionString = ConnectionString(analystMongoProperties.determineUri())
            val settings =
                MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .uuidRepresentation(analystMongoProperties.uuidRepresentation)
                    .build()

            val databaseFactory: MongoDatabaseFactory =
                SimpleMongoClientDatabaseFactory(
                    MongoClients.create(settings, SpringDataMongoDB.driverInformation()),
                    analystMongoProperties.database ?: connectionString.database.orEmpty()
                )

            MongoTemplate(databaseFactory, converter)
        }
    }
}
