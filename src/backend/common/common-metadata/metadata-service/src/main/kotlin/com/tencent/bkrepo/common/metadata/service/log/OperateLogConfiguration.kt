package com.tencent.bkrepo.common.metadata.service.log

import com.tencent.bkrepo.common.api.pojo.ClusterArchitecture
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.metadata.aop.LogOperateAspect
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.log.OperateLogDao
import com.tencent.bkrepo.common.metadata.interceptor.ProjectUsageStatisticsInterceptor
import com.tencent.bkrepo.common.metadata.properties.OperateProperties
import com.tencent.bkrepo.common.metadata.properties.ProjectUsageStatisticsProperties
import com.tencent.bkrepo.common.metadata.service.log.impl.CommitEdgeOperateLogServiceImpl
import com.tencent.bkrepo.common.metadata.service.log.impl.OperateLogServiceImpl
import com.tencent.bkrepo.common.metadata.service.project.ProjectUsageStatisticsService
import com.tencent.bkrepo.common.security.manager.PermissionManager
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
        permissionManager: PermissionManager,
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
