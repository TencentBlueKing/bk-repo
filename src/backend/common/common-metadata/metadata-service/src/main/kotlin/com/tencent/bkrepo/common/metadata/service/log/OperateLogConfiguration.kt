/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.service.log

import com.tencent.bkrepo.common.api.pojo.ClusterArchitecture
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.log.OperateLogDao
import com.tencent.bkrepo.common.metadata.interceptor.ProjectUsageStatisticsInterceptor
import com.tencent.bkrepo.common.metadata.properties.OperateProperties
import com.tencent.bkrepo.common.metadata.properties.ProjectUsageStatisticsProperties
import com.tencent.bkrepo.common.metadata.service.log.impl.CommitEdgeOperateLogServiceImpl
import com.tencent.bkrepo.common.metadata.service.log.impl.OperateLogServiceImpl
import com.tencent.bkrepo.common.metadata.service.project.ProjectUsageStatisticsService
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@Conditional(SyncCondition::class)
class OperateLogConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun operateLogService(
        operateProperties: OperateProperties,
        operateLogDao: OperateLogDao,
        clusterProperties: ClusterProperties
    ): OperateLogService {
        return if (clusterProperties.role == ClusterNodeType.EDGE &&
            clusterProperties.architecture == ClusterArchitecture.COMMIT_EDGE &&
            clusterProperties.commitEdge.oplog.enabled
        ) {
            CommitEdgeOperateLogServiceImpl(operateProperties, operateLogDao, clusterProperties)
        } else {
            OperateLogServiceImpl(operateProperties, operateLogDao)
        }
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(value = ["project-usage-statistics.enableReqCountStatistic"])
    fun projectUsageStatisticsInterceptorRegister(
        properties: ProjectUsageStatisticsProperties,
        service: ProjectUsageStatisticsService,
    ): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                // 不统计服务间调用
                registry.addInterceptor(ProjectUsageStatisticsInterceptor(properties, service))
                    .excludePathPatterns("/service/**", "/replica/**")
            }
        }
    }
}
