package com.tencent.bkrepo.common.service.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * webmvc config
 *
 * @author: carrypan
 * @date: 2019-10-08
 */
@Configuration
class WebMvcConfig : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(WildcardParamMethodArgumentResolver())
        resolvers.add(OctetStreamFileItemMethodArgumentResolver())
    }
}
