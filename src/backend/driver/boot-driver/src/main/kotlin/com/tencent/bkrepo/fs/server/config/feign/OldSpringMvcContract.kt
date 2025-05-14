package com.tencent.bkrepo.fs.server.config.feign

import feign.MethodMetadata
import feign.Util.emptyToNull
import org.springframework.cloud.openfeign.AnnotatedParameterProcessor
import org.springframework.cloud.openfeign.support.SpringMvcContract
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation
import org.springframework.core.convert.ConversionService
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 新版本的Spring Cloud的@FeignClient出于安全考虑（不是十分理解），不允许使用@RequestMapping，
 * 但这对现有的使用方式有很大的影响，需要修改所有的FeignClient，所以这里我们恢复之前的逻辑，允许在
 * FeignClient上使用RequestMapping定义公共的路由前缀。
 * */
class OldSpringMvcContract(
    annotatedParameterProcessors: List<AnnotatedParameterProcessor>,
    conversionService: ConversionService,
    private val decodeSlash: Boolean,
) : SpringMvcContract(annotatedParameterProcessors, conversionService, decodeSlash) {
    private val resourceLoader = DefaultResourceLoader()

    /**
     * 旧版本的SpringMvcContract处理逻辑
     * */
    override fun processAnnotationOnClass(data: MethodMetadata, clz: Class<*>) {
        if (clz.interfaces.isNotEmpty()) {
            return
        }
        val classAnnotation = findMergedAnnotation(clz, RequestMapping::class.java) ?: return
        // Prepend path from class annotation if specified
        if (classAnnotation.value.isNotEmpty()) {
            var pathValue = emptyToNull(classAnnotation.value[0])
            pathValue = resolve(pathValue)
            if (!pathValue.startsWith("/")) {
                pathValue = "/$pathValue"
            }
            data.template().uri(pathValue)
            if (data.template().decodeSlash() != decodeSlash) {
                data.template().decodeSlash(decodeSlash)
            }
        }
    }
    private fun resolve(value: String): String {
        return if (StringUtils.hasText(value) && resourceLoader is ConfigurableApplicationContext) {
            (resourceLoader as ConfigurableApplicationContext).environment.resolvePlaceholders(value)
        } else {
            value
        }
    }
}
