package com.tencent.bkrepo.common.artifact.exception.response

import com.tencent.bkrepo.common.api.pojo.Response
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

@ControllerAdvice
class ArtifactExceptionResponseAdvice : ResponseBodyAdvice<Any> {

    @Autowired
    private lateinit var exceptionResponseTranslator: ExceptionResponseTranslator

    override fun supports(returnType: MethodParameter, converterType: Class<out HttpMessageConverter<*>>): Boolean {
        return Response::class.java.isAssignableFrom(returnType.parameterType)
    }

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse
    ): Any? {
        return (body as? Response<*>)?.let {
            if (it.isNotOk()) exceptionResponseTranslator.translate(it, request, response) else body
        }
    }
}
