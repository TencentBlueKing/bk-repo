package com.tencent.bkrepo.common.artifact.interceptor

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.core.MethodParameter
import org.springframework.http.HttpInputMessage
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Type

/**
 * 请求体repoName转换Advice
 * 配合RepoNameConversionInterceptor使用，处理请求体中的repoName转换
 */
@RestControllerAdvice
class RepoNameConversionRequestBodyAdvice(
    private val objectMapper: ObjectMapper
) : RequestBodyAdvice {

    override fun supports(
        methodParameter: MethodParameter,
        targetType: Type,
        converterType: Class<out HttpMessageConverter<*>>
    ): Boolean {
        return getCurrentRequest()
            ?.getAttribute(RepoNameConversionInterceptor.NEED_CONVERSION_ATTRIBUTE) == true
    }

    override fun beforeBodyRead(
        inputMessage: HttpInputMessage,
        parameter: MethodParameter,
        targetType: Type,
        converterType: Class<out HttpMessageConverter<*>>
    ): HttpInputMessage = ConvertedHttpInputMessage(inputMessage, objectMapper)

    override fun afterBodyRead(
        body: Any,
        inputMessage: HttpInputMessage,
        parameter: MethodParameter,
        targetType: Type,
        converterType: Class<out HttpMessageConverter<*>>
    ): Any = body

    override fun handleEmptyBody(
        body: Any?,
        inputMessage: HttpInputMessage,
        parameter: MethodParameter,
        targetType: Type,
        converterType: Class<out HttpMessageConverter<*>>
    ): Any? = body

    private fun getCurrentRequest(): HttpServletRequest? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request

    private class ConvertedHttpInputMessage(
        private val original: HttpInputMessage,
        private val objectMapper: ObjectMapper
    ) : HttpInputMessage by original {

        private val convertedBody: ByteArray by lazy {
            try {
                val body = original.body.readBytes()
                if (body.isEmpty()) return@lazy body

                val jsonNode = objectMapper.readTree(body)
                val modified = processNode(jsonNode, objectMapper)

                if (modified) {
                    logger.debug("Converted repoName in request body")
                    objectMapper.writeValueAsBytes(jsonNode)
                } else {
                    body
                }
            } catch (e: Exception) {
                logger.warn("Failed to convert repoName in request body", e)
                // 解析失败时返回原始请求内容
                original.body.readBytes()
            }
        }

        override fun getBody(): InputStream = ByteArrayInputStream(convertedBody)

        companion object {
            private val logger = LoggerFactory.getLogger(ConvertedHttpInputMessage::class.java)

            /**
             * 递归处理JSON节点中的repoName字段
             * @return 是否发生了修改
             */
            private fun processNode(node: JsonNode, mapper: ObjectMapper): Boolean {
                var modified = false
                when {
                    node.isObject -> {
                        val objNode = node as ObjectNode
                        // 处理查询条件格式: {"field":"repoName","value":"xxx","operation":"EQ"}
                        // 或 {"field":"repoName","value":["a","b"],"operation":"IN"}
                        if (node.has("field") &&
                            objNode.get("field").asText() == "repoName" &&
                            node.has("value")) {
                            val valueNode = objNode.get("value")
                            when {
                                valueNode.isTextual -> {
                                    // 单值字符串场景
                                    objNode.put("value", RepoNameConversionInterceptor.CREATIVE)
                                    modified = true
                                }

                                valueNode.isArray -> {
                                    // 数组场景，在原有元素中添加CREATIVE
                                    val arrayNode = valueNode as com.fasterxml.jackson.databind.node.ArrayNode
                                    arrayNode.add(RepoNameConversionInterceptor.CREATIVE)
                                    modified = true
                                }
                            }
                        } else if (node.has("repoName")) {
                            // 处理直接格式: {"repoName":"xxx"}
                            objNode.put("repoName", RepoNameConversionInterceptor.CREATIVE)
                            modified = true
                        }
                        objNode.fields().forEach { (_, value) ->
                            if (processNode(value, mapper)) modified = true
                        }
                    }

                    node.isArray -> node.forEach {
                        if (processNode(it, mapper)) modified = true
                    }
                }
                return modified
            }
        }
    }
}
