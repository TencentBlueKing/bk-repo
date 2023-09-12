package com.tencent.bkrepo.git.interceptor.devx

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.git.config.GitProperties
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

/**
 * 云研发源ip拦截器，只允许项目的云桌面ip通过
 * */
class DevxSrcIpInterceptor : HandlerInterceptor {
    @Autowired
    lateinit var properties: GitProperties
    private val httpClient = OkHttpClient.Builder().build()
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (!properties.devx.enabled) {
            return true
        }
        request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) ?: return false
        val repo = ArtifactContextHolder.getRepoDetail()!!
        val srcIp = HttpContextHolder.getClientAddress()
        if (!inWhiteList(srcIp, repo.projectId)) {
            logger.info("Illegal src ip[$srcIp] in project[${repo.projectId}].")
            throw PermissionException()
        }
        logger.info("Allow ip[$srcIp] to access ${repo.projectId}.")
        return true
    }

    private fun inWhiteList(ip: String, projectId: String): Boolean {
        val devxProperties = properties.devx
        val apiAuth = ApiAuth(devxProperties.appCode, devxProperties.appSecret)
        val token = apiAuth.toJsonString().replace(System.lineSeparator(), "")
        val workspaceUrl = devxProperties.workspaceUrl
        val request = Request.Builder()
            .url("$workspaceUrl?project_id=$projectId")
            .header("X-Bkapi-Authorization", token)
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful || response.body == null) {
            val errorMsg = response.body?.bytes()?.let { String(it) }
            logger.error("${response.code} $errorMsg")
            return false
        }
        val whiteList = response.body!!.byteStream().readJsonString<QueryResponse>().data.map {
            it.inner_ip.substringAfter('.')
        }.toSet()
        return whiteList.contains(ip)
    }

    data class ApiAuth(
        val bk_app_code: String,
        val bk_app_secret: String,
    )

    data class QueryResponse(
        val status: Int,
        val data: List<DevxWorkSpace>,
    )

    data class DevxWorkSpace(
        val workspace_name: String,
        val project_id: String,
        val creator: String,
        val region_id: String,
        val inner_ip: String,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(DevxSrcIpInterceptor::class.java)
    }
}
