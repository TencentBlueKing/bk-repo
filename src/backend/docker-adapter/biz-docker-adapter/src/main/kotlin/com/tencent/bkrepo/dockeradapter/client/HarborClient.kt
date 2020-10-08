package com.tencent.bkrepo.dockeradapter.client;

import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.devops.harbor.configuration.SystemConfig
import com.tencent.devops.harbor.pojo.harbor.HarborProject
import com.tencent.devops.harbor.pojo.harbor.HarborRepo
import com.tencent.devops.harbor.pojo.harbor.HarborTag
import com.tencent.devops.harbor.utils.JsonUtils
import okhttp3.Credentials
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@Component
class HarborClient @Autowired constructor(
    private val systemConfig: SystemConfig) {
    companion object {
        private val logger = LoggerFactory.getLogger(HarborClient::class.java)
    }

    fun getProjectByName(projectName: String): HarborProject? {
        val url = "${systemConfig.registryUrl}/api/projects?name=$projectName&page_size=100"
        logger.info("request url: $url")

        val request = Request.Builder().url(url).header("Authorization", makeCredential()).get().build()
        val response = doRequest(request)
        if (response == "null") {
            return null
        }
        val projects = JsonUtils.objectMapper.readValue<List<HarborProject>>(response)
        logger.info("projects: $projects")
        if (projects.isNotEmpty()) {
            projects.forEach {
                if (it.name == projectName) {
                    it.createTime = DateTime(it.createTime).toString("yyyy-MM-dd HH:mm:ss")
                    it.updateTime = DateTime(it.updateTime).toString("yyyy-MM-dd HH:mm:ss")
                    return it
                }
            }
        }
        return null
    }

    fun createProject(projectName: String) {
        val url = "${systemConfig.registryUrl}/api/projects"
        logger.info("request url: $url")

        val reqData = mapOf(
            "project_name" to projectName,
            "public" to 0)
        val requestBody = JsonUtils.objectMapper.writeValueAsString(reqData)
        logger.info("request body: $requestBody")

        val reqBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), requestBody)
        val request = Request.Builder().url(url).header("Authorization", makeCredential()).post(reqBody).build()
        doRequest(request)
    }

    fun createUser(userName: String, password: String) {
        val url = "${systemConfig.registryUrl}/api/users"
        logger.info("request url: $url")

        val reqData = mapOf(
            "username" to userName,
            "email" to "$userName@ee.com",
            "password" to password,
            "role_id" to 2,
            "realname" to userName)
        val requestBody = JsonUtils.objectMapper.writeValueAsString(reqData)
        logger.info("request body: $requestBody")

        val reqBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), JsonUtils.objectMapper.writeValueAsString(reqData))
        val request = Request.Builder().url(url).header("Authorization", makeCredential()).post(reqBody).build()
        doRequest(request)
    }

    fun addProjectMember(userName: String, projectId: Int) {
        val url = "${systemConfig.registryUrl}/api/projects/$projectId/members"
        logger.info("request url: $url")

        val reqData = mapOf(
            "role_id" to 2,
            "member_user" to mapOf("username" to userName))
        val requestBody = JsonUtils.objectMapper.writeValueAsString(reqData)
        logger.info("request body: $requestBody")

        val reqBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), requestBody)
        val request = Request.Builder().url(url).header("Authorization", makeCredential()).post(reqBody).build()
        doRequest(request)
    }

    fun listImage(projectId: Int, searchKey: String, page: Int, pageSize: Int): List<HarborRepo> {
        val queryParamsMap = mapOf(
            "project_id" to projectId.toString(),
            "q" to searchKey,
            "page" to page.toString(),
            "page_size" to pageSize.toString()
        )
        val url = "${systemConfig.registryUrl}/api/repositories?${getQueryStr(queryParamsMap)}"
        logger.info("request url: $url")

        val request = Request.Builder().url(url).header("Authorization", makeCredential()).get().build()
        val response = doRequest(request)
        return JsonUtils.objectMapper.readValue(response)
    }

    fun listTag(repoName: String): List<HarborTag> {
        val url = "${systemConfig.registryUrl}/api/repositories/${URLEncoder.encode(repoName, "utf8")}/tags"
        logger.info("request url: $url")

        val request = Request.Builder().url(url).header("Authorization", makeCredential()).get().build()
        val response = doRequest(request)
        return JsonUtils.objectMapper.readValue(response)
    }

    fun getTag(repoName: String, tag: String): HarborTag? {
        val url = "${systemConfig.registryUrl}/api/repositories/${URLEncoder.encode(repoName, "utf8")}/tags/$tag"
        logger.info("request url: $url")

        val request = Request.Builder().url(url).header("Authorization", makeCredential()).get().build()
        val httpResult = doRequest2(request)
        return if (httpResult.status == HttpStatus.NOT_FOUND.ordinal) {
            null
        } else {
            JsonUtils.objectMapper.readValue(httpResult.body)
        }
    }

    private fun getQueryStr(paramsMap: Map<String, String>): String {
        return if (paramsMap.isEmpty()) {
            ""
        } else {
            val paramList = mutableListOf<String>()
            for ((k, v) in paramsMap) {
                paramList.add("$k=${URLEncoder.encode(v, "utf8")}")
            }
            paramList.joinToString("&")
        }
    }

    @SuppressWarnings
    private fun doRequest(request: Request): String {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(5L, TimeUnit.SECONDS)
            .readTimeout(60L, TimeUnit.SECONDS)
            .writeTimeout(60L, TimeUnit.SECONDS)
            .sslSocketFactory(CertUtils.createSSLSocketFactory())
            .hostnameVerifier(TrustAllHostnameVerifier())
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val responseContent = response.body()!!.string()
                if (!response.isSuccessful) {
                    logger.warn("http request failed: code ${response.code()}, responseContent: $responseContent")
                    throw RuntimeException("http request failed: status code ${response.code()}")
                }
                return responseContent
            }

        } catch (e: Exception) {
            logger.error("http request error", e)
            throw RuntimeException("http request error")
        }
    }

    private fun makeCredential(): String =
        Credentials.basic(systemConfig.registryUsername!!, systemConfig.registryPassword!!)

    @SuppressWarnings
    private fun doRequest2(request: Request): HttpResult {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(5L, TimeUnit.SECONDS)
            .readTimeout(60L, TimeUnit.SECONDS)
            .writeTimeout(60L, TimeUnit.SECONDS)
            .sslSocketFactory(CertUtils.createSSLSocketFactory())
            .hostnameVerifier(TrustAllHostnameVerifier())
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val responseContent = response.body()!!.string()
                return HttpResult(responseContent, response.code())
            }
        } catch (e: Exception) {
            logger.error("http request error", e)
            throw RuntimeException("http request error")
        }
    }
}