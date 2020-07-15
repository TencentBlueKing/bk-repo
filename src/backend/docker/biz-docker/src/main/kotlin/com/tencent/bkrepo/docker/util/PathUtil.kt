package com.tencent.bkrepo.docker.util

import com.tencent.bkrepo.docker.constant.EMPTYSTR
import com.tencent.bkrepo.docker.constant.USER_API_PREFIX
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest

/**
 * docker path utility
 * @author: owenlxu
 * @date: 2019-11-15
 */
object PathUtil {

    fun artifactName(request: HttpServletRequest, pattern: String, projectId: String, repoName: String): String {
        val restOfTheUrl = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString()
        return restOfTheUrl.replaceAfterLast(pattern, EMPTYSTR).removeSuffix(pattern)
            .removePrefix(prefix(projectId, repoName))
    }

    fun userArtifactName(request: HttpServletRequest, projectId: String, repoName: String, tag: String): String {
        val restOfTheUrl = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString()
        return restOfTheUrl.removePrefix(userPrefix(projectId, repoName)).removeSuffix("/$tag")
    }

    fun layerArtifactName(request: HttpServletRequest, projectId: String, repoName: String, id: String): String {
        val restOfTheUrl = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString()
        return restOfTheUrl.removePrefix(layerPrefix(projectId, repoName)).removeSuffix("/$id")
    }

    fun tagArtifactName(request: HttpServletRequest, projectId: String, repoName: String): String {
        val restOfTheUrl = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString()
        return restOfTheUrl.removePrefix(tagPrefix(projectId, repoName))
    }

    private fun prefix(projectId: String, repoName: String): String {
        return "/v2/$projectId/$repoName/"
    }

    private fun userPrefix(projectId: String, repoName: String): String {
        return "$USER_API_PREFIX/manifest/$projectId/$repoName/"
    }

    private fun layerPrefix(projectId: String, repoName: String): String {
        return "$USER_API_PREFIX/layer/$projectId/$repoName/"
    }

    private fun tagPrefix(projectId: String, repoName: String): String {
        return "$USER_API_PREFIX/repo/tag/$projectId/$repoName/"
    }
}
