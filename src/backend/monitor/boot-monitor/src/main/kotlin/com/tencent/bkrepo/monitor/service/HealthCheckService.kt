package com.tencent.bkrepo.monitor.service

import com.tencent.bkrepo.monitor.config.MonitorProperties
import com.tencent.bkrepo.monitor.metrics.HealthInfo
import com.tencent.bkrepo.monitor.notify.MessageNotifier
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Status
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class HealthCheckService(
    healthSourceService: HealthSourceService,
    messageNotifier: MessageNotifier,
    monitorProperties: MonitorProperties
) {
    val clusterName = monitorProperties.clusterName

    init {
        healthSourceService.getMergedSource()
            .filter { it.status.status != Status.UP }
            .flatMap { messageNotifier.notifyMessage(createContent(it)) }
            .doOnError { logger.error("Couldn't notify message.", it) }
            .subscribe()
    }

    private fun createContent(healthInfo: HealthInfo): Any {
        return with(healthInfo) {
            MESSAGE_TEMPLATE.format(application, instance, clusterName, name, status.status.code, status.details, LocalDateTime.now())
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HealthCheckService::class.java)
        val MESSAGE_TEMPLATE =
            """
            <font color="warning">【提醒】</font>服务实例[%s-%s]健康检查失败
             > 集群: %s
             > 组件: %s
             > 状态: %s
             > 详情: %s
             > 时间: %s
            """.trimIndent()
    }
}
