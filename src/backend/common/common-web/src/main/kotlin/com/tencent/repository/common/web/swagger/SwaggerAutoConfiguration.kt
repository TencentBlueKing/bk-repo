package com.tencent.repository.common.web.swagger

import com.google.common.base.Predicates
import org.springframework.context.annotation.Bean
import springfox.documentation.swagger2.annotations.EnableSwagger2
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket

@EnableSwagger2
@ConditionalOnProperty(value = ["swagger.enabled"], matchIfMissing = true )
class SwaggerAutoConfiguration {

    @Bean
    fun api(): Docket {
        val excludePath = DEFAULT_EXCLUDE_PATH.map { PathSelectors.ant(it) }
        return Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.any())
                .paths(Predicates.not(Predicates.or(excludePath)))
                .build()
    }

    companion object {
        val DEFAULT_EXCLUDE_PATH = listOf("/error", "/actuator/**")
    }

}