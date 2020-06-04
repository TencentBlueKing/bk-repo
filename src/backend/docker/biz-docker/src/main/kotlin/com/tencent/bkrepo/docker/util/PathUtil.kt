package com.tencent.bkrepo.docker.util

import com.tencent.bkrepo.docker.constant.EMPTYSTR
import javax.servlet.http.HttpServletRequest
import org.springframework.web.servlet.HandlerMapping

class PathUtil {
    companion object {
        fun prefix(projectId: String, repoName: String): String {
            return "/v2/$projectId/$repoName/"
        }

        fun userPrefix(projectId: String, repoName: String): String {
            return "/api/manifest/$projectId/$repoName/"
        }

        fun layerPrefix(projectId: String, repoName: String): String {
            return "/api/layer/$projectId/$repoName/"
        }

        fun artifactName(request: HttpServletRequest, pattern: String, projectId: String, repoName: String): String {
            var restOfTheUrl = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString()
            var name = restOfTheUrl.replaceAfterLast(pattern, EMPTYSTR).removeSuffix(pattern)
                .removePrefix(prefix(projectId, repoName))
            return name
        }

        fun userArtifactName(
            request: HttpServletRequest,
            projectId: String,
            repoName: String,
            tag: String
        ): String {
            var restOfTheUrl = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString()
            var name = restOfTheUrl.removePrefix(userPrefix(projectId, repoName)).removeSuffix("/$tag")
            return name
        }

        fun layerArtifactName(
            request: HttpServletRequest,
            projectId: String,
            repoName: String,
            id: String
        ): String {
            var restOfTheUrl = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString()
            var name = restOfTheUrl.removePrefix(layerPrefix(projectId, repoName)).removeSuffix("/$id")
            return name
        }

        fun tagArtifactName(
            request: HttpServletRequest,
            projectId: String,
            repoName: String
        ): String {
            var restOfTheUrl = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString()
            var name = restOfTheUrl.removePrefix("/user/repo/tag/$projectId/$repoName/")
            return name
        }
    }
}
