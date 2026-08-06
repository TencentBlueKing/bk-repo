package com.tencent.bkrepo.oci.artifact.interceptor

import com.tencent.bkrepo.oci.util.OciNameAliasCodec
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

class OciNameAliasInterceptor : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val pathWithinHandler = request
            .getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)?.toString()
            ?: return true
        if (!isOciPath(pathWithinHandler)) return true
        @Suppress("UNCHECKED_CAST")
        val pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)
            as? MutableMap<String, String> ?: return true
        val externalProjectId = pathVariables["projectId"] ?: return true
        val externalRepoName = pathVariables["repoName"] ?: return true
        val projectId = OciNameAliasCodec.decodeSegment(externalProjectId)
        val repoName = OciNameAliasCodec.decodeSegment(externalRepoName)
        request.setAttribute(
            OciNameAliasCodec.REQUEST_ATTR_PROJECT_ID_ENCODED,
            externalProjectId != projectId
        )
        request.setAttribute(
            OciNameAliasCodec.REQUEST_ATTR_REPO_NAME_ENCODED,
            externalRepoName != repoName
        )
        pathVariables["projectId"] = projectId
        pathVariables["repoName"] = repoName
        val decodedPath = rewritePath(pathWithinHandler, externalProjectId, externalRepoName, projectId, repoName)
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, decodedPath)
        return true
    }

    private fun rewritePath(
        path: String,
        externalProjectId: String,
        externalRepoName: String,
        projectId: String,
        repoName: String
    ): String {
        val prefix = if (path.startsWith("/oci/v2/")) "/oci/v2/" else "/v2/"
        return path.replaceFirst(
            "$prefix$externalProjectId/$externalRepoName",
            "$prefix$projectId/$repoName"
        )
    }

    private fun isOciPath(path: String): Boolean {
        return path.startsWith("/v2/") || path.startsWith("/oci/v2/")
    }
}
