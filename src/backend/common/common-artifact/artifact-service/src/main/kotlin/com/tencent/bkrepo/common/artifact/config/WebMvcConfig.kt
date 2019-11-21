package com.tencent.bkrepo.common.artifact.config

import com.tencent.bkrepo.common.artifact.resolver.ArtifactDataMethodArgumentResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * webmvc config
 *
 * @author: carrypan
 * @date: 2019-11-21
 */
@Configuration
class WebMvcConfig : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(ArtifactDataMethodArgumentResolver())
    }
}
