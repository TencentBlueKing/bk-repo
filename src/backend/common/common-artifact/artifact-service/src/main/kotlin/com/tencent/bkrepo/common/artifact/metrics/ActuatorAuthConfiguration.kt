package com.tencent.bkrepo.common.artifact.metrics

import org.springframework.boot.actuate.autoconfigure.endpoint.web.CorsEndpointProperties
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties
import org.springframework.boot.actuate.endpoint.ExposableEndpoint
import org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver
import org.springframework.boot.actuate.endpoint.web.EndpointMapping
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier
import org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpointsSupplier
import org.springframework.boot.actuate.endpoint.web.annotation.ServletEndpointsSupplier
import org.springframework.boot.actuate.endpoint.web.servlet.WebMvcEndpointHandlerMapping
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.ArrayList

@Configuration
class ActuatorAuthConfiguration {

    @Bean
    fun webEndpointServletHandlerMapping(
        webEndpointsSupplier: WebEndpointsSupplier,
        servletEndpointsSupplier: ServletEndpointsSupplier,
        controllerEndpointsSupplier: ControllerEndpointsSupplier,
        endpointMediaTypes: EndpointMediaTypes?,
        corsProperties: CorsEndpointProperties,
        webEndpointProperties: WebEndpointProperties
    ): WebMvcEndpointHandlerMapping? {
        val allEndpoints: MutableList<ExposableEndpoint<*>> = ArrayList()
        val webEndpoints = webEndpointsSupplier.endpoints
        allEndpoints.addAll(webEndpoints)
        allEndpoints.addAll(servletEndpointsSupplier.endpoints)
        allEndpoints.addAll(controllerEndpointsSupplier.endpoints)
        val endpointMapping = EndpointMapping(webEndpointProperties.basePath)
        val webMvcEndpointHandlerMapping = WebMvcEndpointHandlerMapping(
            endpointMapping, webEndpoints,
            endpointMediaTypes, corsProperties.toCorsConfiguration(),
            EndpointLinksResolver(allEndpoints, webEndpointProperties.basePath)
        )
        webMvcEndpointHandlerMapping.setInterceptors(actuatorAuthInterceptor())
        return webMvcEndpointHandlerMapping
    }

    @Bean
    fun actuatorAuthInterceptor() = ActuatorAuthInterceptor()
}
