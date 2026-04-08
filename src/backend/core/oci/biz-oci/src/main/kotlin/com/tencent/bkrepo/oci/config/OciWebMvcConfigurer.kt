package com.tencent.bkrepo.oci.config

import com.tencent.bkrepo.oci.artifact.interceptor.OciNameAliasInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class OciWebMvcConfigurer : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(OciNameAliasInterceptor()).order(-110)
    }
}
