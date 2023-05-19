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

import com.tencent.bkrepo.analysis.executor.api.ExecutorClient
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.dispatcher.DockerDispatcher
import com.tencent.bkrepo.analyst.dispatcher.KubernetesDispatcher
import com.tencent.bkrepo.analyst.dispatcher.SubtaskDispatcher
import com.tencent.bkrepo.analyst.dispatcher.SubtaskPoller
import com.tencent.bkrepo.analyst.service.ScanService
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.analyst.service.TemporaryScanTokenService
import com.tencent.bkrepo.analyst.service.impl.OperateLogServiceImpl
import com.tencent.bkrepo.analyst.statemachine.TaskStateMachineConfiguration.Companion.STATE_MACHINE_ID_SUB_SCAN_TASK
import com.tencent.bkrepo.common.operate.api.OperateLogService
import com.tencent.bkrepo.common.service.condition.ConditionalOnNotAssembly
import com.tencent.bkrepo.repository.api.OperateLogClient
import com.tencent.bkrepo.statemachine.StateMachine
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.RedisTemplate

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    ScannerProperties::class,
    ReportExportProperties::class,
    KubernetesDispatcherProperties::class,
    DockerDispatcherProperties::class
)
@LoadBalancerClients(defaultConfiguration = [AnalysisLoadBalancerConfiguration::class])
class ScannerConfiguration {
    @Bean
    @ConditionalOnProperty("scanner.dispatcher.k8s.enabled", havingValue = "true")
    fun k8sDispatcher(
        scannerProperties: ScannerProperties,
        kubernetesDispatcherProperties: KubernetesDispatcherProperties
    ): SubtaskDispatcher {
        return KubernetesDispatcher(scannerProperties, kubernetesDispatcherProperties)
    }

    @Bean
    @ConditionalOnProperty("scanner.dispatcher.docker.enabled", havingValue = "true")
    fun dockerDispatcher(
        subScanTaskDao: SubScanTaskDao,
        scannerProperties: ScannerProperties,
        dockerDispatcherProperties: DockerDispatcherProperties,
        redisTemplate: ObjectProvider<RedisTemplate<String, String>>
    ): SubtaskDispatcher {
        return DockerDispatcher(scannerProperties, dockerDispatcherProperties, subScanTaskDao, redisTemplate)
    }

    @Bean
    @ConditionalOnBean(SubtaskDispatcher::class)
    fun poller(
        dispatcher: SubtaskDispatcher,
        scanService: ScanService,
        scannerService: ScannerService,
        temporaryScanTokenService: TemporaryScanTokenService,
        @Qualifier(STATE_MACHINE_ID_SUB_SCAN_TASK)
        subtaskStateMachine: StateMachine,
        executorClient: ObjectProvider<ExecutorClient>
    ): SubtaskPoller {
        return SubtaskPoller(
            dispatcher, scanService, scannerService, temporaryScanTokenService, subtaskStateMachine, executorClient
        )
    }

    @Bean
    @ConditionalOnNotAssembly // 仅在非单体包部署时创建，避免循环依赖问题
    fun operateLogService(operateLogClient: OperateLogClient): OperateLogService {
        return OperateLogServiceImpl(operateLogClient)
    }
}
