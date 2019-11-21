package com.tencent.bkrepo.common.artifact.resolve

import com.tencent.bkrepo.common.artifact.api.ArtifactCoordinate
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import javax.servlet.http.HttpServletRequest
import org.springframework.core.MethodParameter
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.HandlerMapping

/**
 * 构件位置信息参数解析器
 *
 * @author: carrypan
 * @date: 2019/11/19
 */
class ArtifactInfoMethodArgumentResolver(private val artifactPathResolver: ArtifactPathResolver) : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.parameterType.isAssignableFrom(ArtifactCoordinate::class.java) && parameter.hasParameterAnnotation(
            ArtifactInfo::class.java)
    }

    override fun resolveArgument(parameter: MethodParameter, container: ModelAndViewContainer?, nativeWebRequest: NativeWebRequest, factory: WebDataBinderFactory?): Any? {
        val nameValueMap = nativeWebRequest.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, 0) as Map<*, *>
        val projectId = nameValueMap[PROJECT_ID].toString()
        val repoName = nameValueMap[REPO_NAME].toString()

        val request = nativeWebRequest.getNativeRequest(HttpServletRequest::class.java)
        val fullPath = request?.let {
            AntPathMatcher.DEFAULT_PATH_SEPARATOR + AntPathMatcher().extractPathWithinPattern(
                request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as String,
                request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE) as String
            )
        } ?: ROOT_PATH

        val artifactPath = artifactPathResolver.resolve(fullPath)

        return ArtifactCoordinate(projectId, repoName, artifactPath)
    }

    companion object {
        private const val PROJECT_ID = "projectId"
        private const val REPO_NAME = "repoName"
        private const val ROOT_PATH = "/"
    }
}
