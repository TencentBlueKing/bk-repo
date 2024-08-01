package com.tencent.bkrepo.fs.server.config.feign

import feign.Contract
import org.springframework.cloud.openfeign.AnnotatedParameterProcessor
import org.springframework.cloud.openfeign.FeignClientProperties
import org.springframework.cloud.openfeign.FeignFormatterRegistrar
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.ConversionService
import org.springframework.format.support.DefaultFormattingConversionService
import org.springframework.format.support.FormattingConversionService

/**
 * 同步feign的全局配置
 * */
@Configuration
class FeignGlobalConfiguration {
    @Bean
    fun feignContract(
        parameterProcessors: List<AnnotatedParameterProcessor>,
        feignClientProperties: FeignClientProperties?,
        feignConversionService: ConversionService,
    ): Contract {
        val decodeSlash = feignClientProperties?.isDecodeSlash ?: true
        return OldSpringMvcContract(parameterProcessors, feignConversionService, decodeSlash)
    }

    @Bean
    fun feignConversionService(feignFormatterRegistrars: List<FeignFormatterRegistrar>): FormattingConversionService {
        val conversionService: FormattingConversionService = DefaultFormattingConversionService()
        for (feignFormatterRegistrar in feignFormatterRegistrars) {
            feignFormatterRegistrar.registerFormatters(conversionService)
        }
        return conversionService
    }
}
