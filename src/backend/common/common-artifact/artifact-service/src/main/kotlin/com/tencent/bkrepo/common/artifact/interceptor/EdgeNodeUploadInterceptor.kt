package com.tencent.bkrepo.common.artifact.interceptor

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.RoleType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * edge节点上传拦截器
 * */
class EdgeNodeUploadInterceptor(private val clusterProperties: ClusterProperties) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (clusterProperties.role == RoleType.EDGE && isUploadRequest(request)) {
            response.status = HttpStatus.BAD_REQUEST.value
            val ret = ResponseBuilder.fail(HttpStatus.BAD_REQUEST.value, "Upload on edge node is not support.",)
            val retStr = JsonUtils.objectMapper.writeValueAsString(ret)
            response.writer.write(retStr)
            return false
        }
        return super.preHandle(request, response, handler)
    }

    private fun isUploadRequest(request: HttpServletRequest): Boolean {
        if (request.method == HttpMethod.GET.name || request.method == HttpMethod.HEAD.name) {
            return false
        }
        return true
    }
}
