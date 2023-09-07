package com.tencent.bkrepo.git.interceptor

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.configuration.proxy.ProxyConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.service.util.HttpProxyUtil
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ProxyInterceptor : HandlerInterceptor {
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
        HttpProxyUtil.proxy(request, response, proxyUrl, gitRepoKey)
        return false
    }
}
