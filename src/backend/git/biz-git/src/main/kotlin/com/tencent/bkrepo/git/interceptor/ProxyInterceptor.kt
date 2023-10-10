package com.tencent.bkrepo.git.interceptor

import com.fasterxml.jackson.databind.node.ObjectNode
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.configuration.proxy.ProxyConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.service.util.proxy.DefaultProxyCallHandler
import com.tencent.bkrepo.common.service.util.proxy.HttpProxyUtil
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import okhttp3.Response
import org.slf4j.LoggerFactory

class ProxyInterceptor : HandlerInterceptor {
    private val lfsProxyHandler = LfsProxyHandler()
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) ?: return false
        val repo = ArtifactContextHolder.getRepoDetail()!!
        // 只有PROXY类型的仓库才进行拦截
        if (repo.category != RepositoryCategory.PROXY) {
            return true
        }
        val projectId = repo.projectId
        val repoName = repo.name
        val gitRepoKey = "/$projectId/$repoName.git"
        val configuration = repo.configuration as ProxyConfiguration
        val proxyUrl = configuration.proxy.url
        if (request.requestURI.endsWith(LFS_BATCH_URI)) {
            HttpProxyUtil.proxy(request, response, proxyUrl, gitRepoKey, lfsProxyHandler)
        } else {
            HttpProxyUtil.proxy(request, response, proxyUrl, gitRepoKey)
        }

        return false
    }

    private class LfsProxyHandler : DefaultProxyCallHandler() {
        override fun after(proxyRequest: HttpServletRequest, proxyResponse: HttpServletResponse, response: Response) {
            if (!response.isSuccessful) {
                return super.after(proxyRequest, proxyResponse, response)
            }
            // 转发状态码
            proxyResponse.status = response.code
            // 转发头
            response.headers.forEach { (key, value) -> proxyResponse.addHeader(key, value) }
            val originBody = response.body?.bytes()?.let { String(it) }.orEmpty()
            val urlPrefix = proxyRequest.requestURL.toString().substringBefore(LFS_BATCH_URI)
            val newBody = findHrefAndReplace(originBody, urlPrefix)
            newBody.byteInputStream().copyTo(proxyResponse.outputStream)
        }

        private fun findHrefAndReplace(body: String, replace: String): String {
            val rootNode = JsonUtils.objectMapper.readTree(body)
            rootNode.path("objects")
                .forEach {
                    val actionDetailNode = it.path("actions").get("download")
                        ?: it.path("actions").get("upload") ?: return body
                    if (actionDetailNode.has("href")) {
                        val originHref = actionDetailNode.get("href").asText()
                        val subAfter = originHref.removePrefix(originHref.substringBefore(LFS_CONTENT_URI))
                        val newHref = replace + subAfter
                        logger.info("Replace href $originHref -> $newHref")
                        (actionDetailNode as ObjectNode).put("href", newHref)
                    } else {
                        error("Not found href: $body.")
                    }
                }
            return rootNode.toJsonString()
        }

        companion object {
            private val logger = LoggerFactory.getLogger(LfsProxyHandler::class.java)
        }
    }

    companion object {
        private const val LFS_BATCH_URI = "/info/lfs/objects/batch"
        private const val LFS_CONTENT_URI = "/content/lfs/objects"
    }
}
