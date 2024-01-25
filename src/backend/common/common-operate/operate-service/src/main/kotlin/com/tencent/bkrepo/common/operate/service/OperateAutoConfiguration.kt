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

package com.tencent.bkrepo.common.operate.service

import com.tencent.bkrepo.common.api.pojo.ClusterArchitecture
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.operate.api.OperateLogService
import com.tencent.bkrepo.common.operate.api.ProjectUsageStatisticsService
import com.tencent.bkrepo.common.operate.service.aop.LogOperateAspect
import com.tencent.bkrepo.common.operate.service.config.OperateProperties
import com.tencent.bkrepo.common.operate.service.config.ProjectUsageStatisticsProperties
import com.tencent.bkrepo.common.operate.service.dao.OperateLogDao
import com.tencent.bkrepo.common.operate.service.dao.ProjectUsageStatisticsDao
import com.tencent.bkrepo.common.operate.service.interceptor.ProjectUsageStatisticsInterceptor
import com.tencent.bkrepo.common.operate.service.service.CommitEdgeOperateLogServiceImpl
import com.tencent.bkrepo.common.operate.service.service.OperateLogServiceImpl
import com.tencent.bkrepo.common.operate.service.service.ProjectUsageStatisticsServiceImpl
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableConfigurationProperties(OperateProperties::class, ProjectUsageStatisticsProperties::class)
@ConditionalOnWebApplication
@Import(OperateLogDao::class, ProjectUsageStatisticsDao::class)
class OperateAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun operateLogService(
        operateProperties: OperateProperties,
        operateLogDao: OperateLogDao,
        permissionManager: PermissionManager,
        clusterProperties: ClusterProperties
    ): OperateLogService {
        return if (clusterProperties.role == ClusterNodeType.EDGE &&
            clusterProperties.architecture == ClusterArchitecture.COMMIT_EDGE
        ) {
            CommitEdgeOperateLogServiceImpl(operateProperties, operateLogDao, permissionManager, clusterProperties)
        } else {
            OperateLogServiceImpl(operateProperties, operateLogDao, permissionManager)
        }
    }

    @Bean
    @ConditionalOnMissingBean
    fun projectUsageStatisticsService(
        properties: ProjectUsageStatisticsProperties,
        projectUsageStatisticsDao: ProjectUsageStatisticsDao,
    ): ProjectUsageStatisticsService {
        return ProjectUsageStatisticsServiceImpl(properties, projectUsageStatisticsDao)
    }

    @Bean
    fun operateLogAspect(operateLogService: OperateLogService): LogOperateAspect {
        return LogOperateAspect(operateLogService)
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
