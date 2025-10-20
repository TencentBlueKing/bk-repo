package com.tencent.bkrepo.repository.service.experience

import com.tencent.bkrepo.common.api.constant.HttpHeaders.CONTENT_ENCODING
import com.tencent.bkrepo.common.api.constant.HttpHeaders.CONTENT_LENGTH
import com.tencent.bkrepo.common.api.constant.HttpHeaders.CONTENT_TYPE
import com.tencent.bkrepo.common.api.constant.HttpHeaders.TRANSFER_ENCODING
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.config.CIExperienceProperties
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceHeader
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceChangeLogRequest
import io.micrometer.observation.ObservationRegistry
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CIExperienceService(
    private val properties: CIExperienceProperties,
    private val registry: ObservationRegistry
) {
    private val okHttpClient = HttpClientBuilderFactory.create(registry = registry).build()

    fun getAppExperiences(user: String, request: AppExperienceHeader) {
        val url = "${properties.ciExperienceServer}/ms/artifactory/api/open/experiences/v3/list"
        executeGetRequest(
            url = url,
            user = user,
            headers = request,
            operationName = "getAppExperiences"
        )
    }

    fun getAppExperienceDetail(
        user: String,
        experienceHashId: String,
        request: AppExperienceHeader
    ) {
        val url = "${properties.ciExperienceServer}/ms/artifactory/api/open/experiences/${experienceHashId}/detail"
        executeGetRequest(
            url = url,
            user = user,
            headers = request,
            operationName = "getAppExperienceDetail"
        )
    }

    fun getAppExperienceChangeLog(
        user: String,
        experienceHashId: String,
        request: AppExperienceChangeLogRequest
    ) {
        val baseUrl = "${properties.ciExperienceServer}/ms/artifactory/api/open/experiences/$experienceHashId/changeLog"
        val httpUrl = baseUrl.toHttpUrlOrNull()?.newBuilder()!!

        request.name?.let { httpUrl.addQueryParameter("name", it) }
        request.version?.let { httpUrl.addQueryParameter("version", it) }
        request.remark?.let { httpUrl.addQueryParameter("remark", it) }
        request.creator?.let { httpUrl.addQueryParameter("creator", it) }
        request.createDateBegin?.let { httpUrl.addQueryParameter("createDateBegin", it.toString()) }
        request.createDateEnd?.let { httpUrl.addQueryParameter("createDateEnd", it.toString()) }
        request.endDateBegin?.let { httpUrl.addQueryParameter("endDateBegin", it.toString()) }
        request.endDateEnd?.let { httpUrl.addQueryParameter("endDateEnd", it.toString()) }
        request.showAll?.let { httpUrl.addQueryParameter("showAll", it.toString()) }
        httpUrl.addQueryParameter("page", request.page.toString())
        httpUrl.addQueryParameter("pageSize", request.pageSize.toString())

        val url = httpUrl.build().toString()
        val headers = AppExperienceHeader(
            organization = request.organizationName,
            platform = null,
            version = null,
        )
        executeGetRequest(
            url = url,
            user = user,
            headers = headers,
            operationName = "getAppExperienceChangeLog"
        )
    }

    fun getAppExperienceInstallPackages(
        user: String,
        experienceHashId: String,
        request: AppExperienceHeader
    ) {
        val url = properties.ciExperienceServer +
            "/ms/artifactory/api/open/experiences/$experienceHashId/installPackages"
        executeGetRequest(
            url = url,
            user = user,
            headers = request,
            operationName = "getAppExperienceInstallPackages"
        )
    }

    fun getAppExperienceDownloadUrl(
        user: String,
        experienceHashId: String,
        request: AppExperienceHeader
    ) {
        val url = properties.ciExperienceServer +
            "/ms/artifactory/api/open/experiences/$experienceHashId/downloadUrl"
        executePostRequest(
            url = url,
            user = user,
            headers = request,
            operationName = "getAppExperienceDownloadUrl"
        )
    }

    /**
     * 构建通用请求头
     */
    private fun Request.Builder.withHeaders(user: String, headers: AppExperienceHeader): Request.Builder {
        this.addHeader(DEVOPS_UID, user)
            .addHeader(DEVOPS_BK_TOKEN, properties.ciToken)
        headers.platform?.let { addHeader(DEVOPS_PLATFORM, it) }
        headers.organization?.let { addHeader(DEVOPS_ORGANIZATION, it) }
        headers.version?.let { addHeader(DEVOPS_VERSION, it) }
        if (properties.gray.isNotEmpty()) {
            addHeader(DEVOPS_GRAY, properties.gray)
        }
        return this
    }

    /**
     * 执行请求GET请求
     */
    private fun executeGetRequest(
        url: String,
        user: String,
        headers: AppExperienceHeader,
        operationName: String
    ) {
        val request = Request.Builder()
            .url(url)
            .withHeaders(user, headers)
            .get()
            .build()

        executeRequest(request, operationName, url)
    }

    /**
     * 执行请求POST请求
     */
    private fun executePostRequest(
        url: String,
        user: String,
        headers: AppExperienceHeader,
        operationName: String
    ) {
        val requestBody = "".toRequestBody(MediaTypes.APPLICATION_JSON.toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .withHeaders(user, headers)
            .post(requestBody)
            .build()

        executeRequest(request, operationName, url)
    }

    /**
     * 执行请求的通用方法
     */
    private fun executeRequest(
        request: Request,
        operationName: String,
        url: String
    ) {
        logger.info("$operationName, requestUrl: [$url]")
        okHttpClient.newCall(request).execute().use { response ->
            HttpContextHolder.getResponse().status = response.code
            response.headers[TRANSFER_ENCODING]?.let {
                HttpContextHolder.getResponse().setHeader(TRANSFER_ENCODING, it)
            }
            response.headers[CONTENT_ENCODING]?.let { HttpContextHolder.getResponse().setHeader(CONTENT_ENCODING, it) }
            response.headers[CONTENT_TYPE]?.let { HttpContextHolder.getResponse().setHeader(CONTENT_TYPE, it) }
            response.headers[CONTENT_LENGTH]?.let { HttpContextHolder.getResponse().setHeader(CONTENT_LENGTH, it) }
            response.body?.byteStream()?.copyTo(HttpContextHolder.getResponse().outputStream)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CIExperienceService::class.java)
        const val DEVOPS_BK_TOKEN = "X-DEVOPS-BK-TOKEN"
        const val DEVOPS_UID = "X-DEVOPS-UID"
        const val DEVOPS_PLATFORM = "X-DEVOPS-PLATFORM"
        const val DEVOPS_VERSION = "X-DEVOPS-APP-VERSION"
        const val DEVOPS_ORGANIZATION = "X-DEVOPS-ORGANIZATION-NAME"
        const val DEVOPS_GRAY = "X-GATEWAY-TAG"
    }
}
