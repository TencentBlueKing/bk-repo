package com.tencent.bkrepo.common.service.log

import io.undertow.Undertow
import io.undertow.UndertowOptions
import io.undertow.server.handlers.accesslog.AccessLogHandler
import io.undertow.server.handlers.accesslog.AccessLogReceiver
import org.springframework.boot.web.embedded.undertow.UndertowBuilderCustomizer
import org.springframework.boot.web.embedded.undertow.UndertowDeploymentInfoCustomizer
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.boot.web.server.WebServerFactoryCustomizer

@Configuration
class AccessLogWebServerCustomizer : WebServerFactoryCustomizer<UndertowServletWebServerFactory> {

    override fun customize(factory: UndertowServletWebServerFactory) {
        val pattern = "%h %l %u %t \"%r\" %s %b \"%{i,Referer}\" \"%{i,User-Agent}\" %D ms"

        if (logRequestProcessingTiming(pattern)) {
            factory.addBuilderCustomizers(
                UndertowBuilderCustomizer { builder ->
                    builder.setServerOption(UndertowOptions.RECORD_REQUEST_START_TIME, true)
                }
            )
        }

        factory.addDeploymentInfoCustomizers(
            UndertowDeploymentInfoCustomizer { deploymentInfo ->
                deploymentInfo.addInitialHandlerChainWrapper { handler ->
                    val accessLogReceiver = Slf4jAccessLogReceiver()
                    AccessLogHandler(handler, accessLogReceiver, pattern, Undertow::class.java.classLoader)
                }
            }
        )
    }

    private fun logRequestProcessingTiming(pattern: String): Boolean {
        return pattern.contains("%D") || pattern.contains("%T")
    }

    class Slf4jAccessLogReceiver : AccessLogReceiver {
        override fun logMessage(message: String) {
            LoggerHolder.accessLogger.info(message)
        }
    }
}
