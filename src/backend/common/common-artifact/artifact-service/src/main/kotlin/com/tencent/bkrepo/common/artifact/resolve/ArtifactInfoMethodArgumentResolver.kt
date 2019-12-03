package com.tencent.bkrepo.common.artifact.resolve

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.config.PROJECT_ID
import com.tencent.bkrepo.common.artifact.config.REPO_NAME
import com.tencent.bkrepo.repository.util.NodeUtils
import org.slf4j.LoggerFactory
import org.springframework.core.MethodParameter
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST
import kotlin.reflect.KClass

/**
 * 构件位置信息参数解析器
 *
 * @author: carrypan
 * @date: 2019/11/19
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
        val fullPath = request.let {
            AntPathMatcher.DEFAULT_PATH_SEPARATOR + AntPathMatcher().extractPathWithinPattern(
                request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as String,
                request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE) as String
            )
        }
        return try {
            val resolver = resolverMap.getResolver(parameter.parameterType.kotlin as KClass<out ArtifactInfo>)
            resolver.resolve(projectId, repoName, NodeUtils.formatFullPath(fullPath), request)
        } catch (exception: Exception) {
            logger.warn("Occur exception when resolve argument: $exception")
            val response = nativeWebRequest.getNativeResponse(HttpServletResponse::class.java)!!
            response.status = SC_BAD_REQUEST
            null
        }

    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactInfoMethodArgumentResolver::class.java)
    }
}
