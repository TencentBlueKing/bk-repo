package com.tencent.bkrepo.common.artifact.resolve.path

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.constant.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.repository.util.NodeUtils
import javax.servlet.http.HttpServletRequest
import kotlin.reflect.KClass
import org.springframework.core.MethodParameter
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.HandlerMapping

/**
 * 构件位置信息参数解析器
 */
@Suppress("UNCHECKED_CAST")
class ArtifactInfoMethodArgumentResolver : HandlerMethodArgumentResolver {

    private val resolverMap: ResolverMap = ResolverScannerRegistrar.resolverMap

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return ArtifactInfo::class.java.isAssignableFrom(parameter.parameterType) && parameter.hasParameterAnnotation(ArtifactPathVariable::class.java)
    }

    override fun resolveArgument(parameter: MethodParameter, container: ModelAndViewContainer?, nativeWebRequest: NativeWebRequest, factory: WebDataBinderFactory?): Any? {
        val attributes = nativeWebRequest.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, 0) as Map<*, *>
        val projectId = attributes[PROJECT_ID].toString()
        val repoName = attributes[REPO_NAME].toString()

        val request = nativeWebRequest.getNativeRequest(HttpServletRequest::class.java)!!
        val artifactUri = request.let {
            AntPathMatcher.DEFAULT_PATH_SEPARATOR + AntPathMatcher().extractPathWithinPattern(
                request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as String,
                request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE) as String
            )
        }
        val resolver = resolverMap.getResolver(parameter.parameterType.kotlin as KClass<out ArtifactInfo>)
        val artifactInfo = resolver.resolve(projectId, repoName, NodeUtils.formatFullPath(artifactUri), request)
        request.setAttribute(ARTIFACT_INFO_KEY, artifactInfo)
        return artifactInfo
    }
}
