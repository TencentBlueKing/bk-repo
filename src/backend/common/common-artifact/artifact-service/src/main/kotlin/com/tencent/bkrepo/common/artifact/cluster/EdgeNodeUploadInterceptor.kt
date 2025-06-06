package com.tencent.bkrepo.common.artifact.cluster

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.pojo.ClusterArchitecture
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * edge节点上传拦截器
 * */
class EdgeNodeUploadInterceptor(private val clusterProperties: ClusterProperties) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        // proxy组网edge节点只允许非具体仓库服务和get、head请求通过
        if (isProxyEdge() && isRegistryUploadRequest(request)) {
            response.status = HttpStatus.BAD_REQUEST.value
            val ret = ResponseBuilder.fail(HttpStatus.BAD_REQUEST.value, "Deployment on edge node is not support.")
            val retStr = JsonUtils.objectMapper.writeValueAsString(ret)
            response.writer.write(retStr)
            return false
        }
        return super.preHandle(request, response, handler)
    }

    private fun isProxyEdge() = clusterProperties.role == ClusterNodeType.EDGE &&
        clusterProperties.architecture == ClusterArchitecture.PROXY

    /**
     * 判断是否为依赖源的上传请求。
     * */
    private fun isRegistryUploadRequest(request: HttpServletRequest): Boolean {
        val repositoryType = ArtifactContextHolder.getCurrentArtifactConfigurer().getRepositoryType()
        if (request.method == HttpMethod.GET.name() ||
            request.method == HttpMethod.HEAD.name() ||
            repositoryType == RepositoryType.NONE
        ) {
            return false
        }
        return true
    }
}
