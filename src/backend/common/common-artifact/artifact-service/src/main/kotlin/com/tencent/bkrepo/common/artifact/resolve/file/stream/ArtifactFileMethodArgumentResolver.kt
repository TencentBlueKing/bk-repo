package com.tencent.bkrepo.common.artifact.resolve.file.stream

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import javax.servlet.http.HttpServletRequest

/**
 * application/octet-stream流文件上传参数解析器
 *
 * @author: carrypan
 * @date: 2019-10-30
 */
class ArtifactFileMethodArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return ArtifactFile::class.java.isAssignableFrom(parameter.parameterType)
    }

    override fun resolveArgument(parameter: MethodParameter, container: ModelAndViewContainer?, nativeWebRequest: NativeWebRequest, factory: WebDataBinderFactory?): Any? {
        val request = nativeWebRequest.getNativeRequest(HttpServletRequest::class.java)!!
        return resolveOctetStream(request)
    }

    private fun resolveOctetStream(request: HttpServletRequest): ArtifactFile {
        return ArtifactFileFactory.build(request.inputStream)
    }
}
