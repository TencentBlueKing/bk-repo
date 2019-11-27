package com.tencent.bkrepo.common.artifact.resolve

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.config.PROJECT_ID
import com.tencent.bkrepo.common.artifact.config.REPO_NAME
import com.tencent.bkrepo.repository.util.NodeUtils
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
class ArtifactInfoMethodArgumentResolver(private val artifactCoordinateResolver: ArtifactCoordinateResolver) : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.parameterType.isAssignableFrom(ArtifactInfo::class.java) && parameter.hasParameterAnnotation(ArtifactPathVariable::class.java)
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
        } ?: AntPathMatcher.DEFAULT_PATH_SEPARATOR

        val formattedFullPath = NodeUtils.formatFullPath(fullPath)
        val artifactCoordinate = artifactCoordinateResolver.resolve(formattedFullPath)

        return ArtifactInfo(projectId, repoName, artifactCoordinate)
    }

}
