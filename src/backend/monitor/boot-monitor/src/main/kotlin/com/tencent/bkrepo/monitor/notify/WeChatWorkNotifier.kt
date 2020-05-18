package com.tencent.bkrepo.monitor.notify

import com.tencent.bkrepo.monitor.config.MonitorProperties
import de.codecentric.boot.admin.server.domain.entities.Instance
import de.codecentric.boot.admin.server.domain.entities.InstanceRepository
import de.codecentric.boot.admin.server.domain.events.InstanceEvent
import de.codecentric.boot.admin.server.domain.events.InstanceStatusChangedEvent
import de.codecentric.boot.admin.server.notify.AbstractStatusChangeNotifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import reactor.core.publisher.Mono
import java.time.LocalDateTime

@Component
@ConfigurationProperties("spring.boot.admin.notify.wechatwork")
@ConditionalOnProperty("spring.boot.admin.notify.wechatwork.key")
class WeChatWorkNotifier(
    repository: InstanceRepository,
    monitorProperties: MonitorProperties
) : AbstractStatusChangeNotifier(repository), MessageNotifier {

    private val restTemplate = RestTemplate()

    var apiUrl = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key="
    var key: String? = null
    var chatId: String? = null
    var visibleToUser: String? = null
    val clusterName = monitorProperties.clusterName

    init {
        ignoreChanges = emptyArray()
    }

    override fun notifyMessage(content: Any): Mono<Void> {
        val message = mutableMapOf<String, Any>()
        message["msgtype"] = "markdown"
        message["markdown"] = mapOf("content" to content)
        chatId?.let { message["chatid"] = chatId!! }
        visibleToUser?.let { message["visible_to_user"] = visibleToUser!! }
        return Mono.fromRunnable { restTemplate.postForObject(buildUrl(), message, String::class.java) }
    }

    override fun doNotify(event: InstanceEvent, instance: Instance): Mono<Void> {
        val content = createContent(event, instance)
        notifyMessage(content)
        return notifyMessage(content)
    }

    private fun createContent(event: InstanceEvent, instance: Instance): String {
        val application = instance.registration.name
        val instanceId = instance.id
        val from = getLastStatus(event.instance)
        val to = (event as InstanceStatusChangedEvent).statusInfo.status
        val currentDate = LocalDateTime.now()
        return MESSAGE_TEMPLATE.format(application, instanceId, clusterName, from, to, currentDate)
    }

    private fun buildUrl(): String {
        return apiUrl + key
    }

    companion object {
        val MESSAGE_TEMPLATE = """
            <font color="warning">【提醒】</font>服务实例[%s-%s]状态发生变化
             > 集群: %s
             > 状态: %s -> %s
             > 时间: %s
        """.trimIndent()
    }
}
