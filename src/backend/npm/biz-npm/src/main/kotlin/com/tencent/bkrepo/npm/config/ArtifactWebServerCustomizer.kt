package com.tencent.bkrepo.npm.config

import io.undertow.Undertow
import io.undertow.UndertowOptions
import org.springframework.boot.web.embedded.undertow.UndertowBuilderCustomizer
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Configuration

@Configuration
class ArtifactWebServerCustomizer : WebServerFactoryCustomizer<UndertowServletWebServerFactory> {
    override fun customize(factory: UndertowServletWebServerFactory) {
        factory.addBuilderCustomizers(
            UndertowBuilderCustomizer { builder: Undertow.Builder ->
                builder.setServerOption(
                    UndertowOptions.ALLOW_ENCODED_SLASH,
                    true
                )
            }
        )
    }
}
