package com.tencent.bkrepo.repository.service.experience

import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.util.JsonUtils.objectMapper
import com.tencent.bkrepo.repository.config.CIExperienceProperties
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceList
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceRequest
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceDetail
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceInstallPackage
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceChangeLogRequest
import com.tencent.bkrepo.repository.pojo.experience.PaginationExperienceChangeLog
import com.tencent.bkrepo.repository.pojo.experience.PaginationExperienceInstallPackages
import com.tencent.bkrepo.repository.pojo.experience.DevopsResponse
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.util.UriComponentsBuilder
import java.util.concurrent.TimeUnit

@Service
class CIExperienceService(
    private val properties: CIExperienceProperties
) {
    private val okHttpClient = okhttp3.OkHttpClient.Builder().connectTimeout(3L, TimeUnit.SECONDS)
        .readTimeout(5L, TimeUnit.SECONDS)
        .writeTimeout(5L, TimeUnit.SECONDS).build()

    fun getAppExperiences(user: String, request: AppExperienceRequest): AppExperienceList {
        val url = "${properties.ciExperienceServer}/ms/artifactory/api/open/experiences/v3/list"
        return try {
            val requestBuilder = Request.Builder().url(url)
                .addHeader(DEVOPS_UID, user)
                .addHeader(DEVOPS_BK_TOKEN, properties.ciToken)
            request.platform?.let { requestBuilder.addHeader(DEVOPS_PLATFORM, it) }
            request.organization?.let { requestBuilder.addHeader(DEVOPS_ORGANIZATION, it) }
            val httpRequest = requestBuilder.get().build()
            logger.debug("getAppExperiences, requestUrl: [$url]")
            val body = HttpUtils.doRequest(okHttpClient, httpRequest, 2, allowHttpStatusSet)
            val resp = objectMapper.readValue<DevopsResponse<AppExperienceList>>(body)
            return AppExperienceList(
                privateExperiences = resp.data?.privateExperiences ?: emptyList(),
                publicExperiences = resp.data?.publicExperiences ?: emptyList(),
                redPointCount = resp.data?.redPointCount ?: 0
            )
        } catch (exception: InvalidFormatException) {
            logger.info("getAppExperiences  url is $url, error: ", exception)
            EMPTY_EXPERIENCE_LIST
        } catch (exception: Exception) {
            logger.error("getAppExperiences error: ", exception)
            EMPTY_EXPERIENCE_LIST
        }
    }

    fun getAppExperienceDetail(
        user: String,
        experienceHashId: String,
        request: AppExperienceRequest
    ): AppExperienceDetail? {
        val url = "${properties.ciExperienceServer}/ms/artifactory/api/open/experiences/${experienceHashId}/detail"
        return try {
            val requestBuilder = Request.Builder().url(url)
                .addHeader(DEVOPS_UID, user)
                .addHeader(DEVOPS_BK_TOKEN, properties.ciToken)
            request.platform?.let { requestBuilder.addHeader(DEVOPS_PLATFORM, it) }
            request.organization?.let { requestBuilder.addHeader(DEVOPS_ORGANIZATION, it) }
            request.version?.let { requestBuilder.addHeader(DEVOPS_VERSION, it) }
            val httpRequest = requestBuilder.get().build()
            logger.debug("getAppExperienceDetail, requestUrl: [$url]")
            val body = HttpUtils.doRequest(okHttpClient, httpRequest, 2, allowHttpStatusSet)
            val resp = objectMapper.readValue<DevopsResponse<AppExperienceDetail>>(body)
            if (resp.status == 0) resp.data else null
        } catch (exception: Exception) {
            logger.error("getAppExperienceDetail error: ", exception)
            null
        }
    }

    fun getAppExperienceChangeLog(
        user: String,
        experienceHashId: String,
        request: AppExperienceChangeLogRequest
    ): PaginationExperienceChangeLog {
        // 拼接params
        val url = UriComponentsBuilder
            .fromUriString(
                properties.ciExperienceServer +
                    "/ms/artifactory/api/open/experiences/$experienceHashId/changeLog"
            )
            .queryParams(LinkedMultiValueMap<String, String>().apply {
                objectMapper.convertValue<Map<String, Any?>>(request).forEach { (k, v) ->
                    v?.let { add(k, it.toString()) }
                }
            })
            .toUriString()

        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader(DEVOPS_UID, user)
                .addHeader(DEVOPS_BK_TOKEN, properties.ciToken)
            request.organizationName?.let { requestBuilder.addHeader(DEVOPS_ORGANIZATION, it) }
            val httpRequest = requestBuilder.get().build()
            logger.debug("getAppExperienceChangeLog, requestUrl: [$url]")
            val body = HttpUtils.doRequest(okHttpClient, httpRequest, 2, allowHttpStatusSet)
            val resp = objectMapper.readValue<DevopsResponse<PaginationExperienceChangeLog>>(body)
            return if (resp.status == 0) {
                resp.data ?: PaginationExperienceChangeLog(0, false, emptyList())
            } else {
                PaginationExperienceChangeLog(0, false, emptyList())
            }
        } catch (exception: Exception) {
            logger.error("getAppExperienceChangeLog error: ", exception)
            PaginationExperienceChangeLog(0, false, emptyList())
        }
    }

    fun getAppExperienceInstallPackages(
        user: String,
        experienceHashId: String,
        request: AppExperienceRequest
    ): List<AppExperienceInstallPackage> {
        val url = properties.ciExperienceServer +
            "/ms/artifactory/api/open/experiences/$experienceHashId/installPackages"
        return try {
            val requestBuilder = Request.Builder().url(url)
                .addHeader(DEVOPS_UID, user)
                .addHeader(DEVOPS_BK_TOKEN, properties.ciToken)
            request.version?.let { requestBuilder.addHeader(DEVOPS_VERSION, it) }
            request.platform?.let { requestBuilder.addHeader(DEVOPS_PLATFORM, it) }
            val httpRequest = requestBuilder.get().build()
            logger.debug("getAppExperienceInstallPackages, requestUrl: [$url]")
            val body = HttpUtils.doRequest(okHttpClient, httpRequest, 2, allowHttpStatusSet)
            val resp = objectMapper.readValue<DevopsResponse<PaginationExperienceInstallPackages>>(body)
            if (resp.status == 0) resp.data?.records ?: emptyList() else emptyList()
        } catch (exception: Exception) {
            logger.error("getAppExperienceInstallPackages error: ", exception)
            emptyList()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CIExperienceService::class.java)
        const val DEVOPS_BK_TOKEN = "X-DEVOPS-BK-TOKEN"
        const val DEVOPS_UID = "X-DEVOPS-UID"
        const val DEVOPS_PLATFORM = "X-DEVOPS-PLATFORM"
        const val DEVOPS_VERSION = "X-DEVOPS-APP-VERSION"
        const val DEVOPS_ORGANIZATION = "X-DEVOPS-ORGANIZATION-NAME"
        private val EMPTY_EXPERIENCE_LIST = AppExperienceList(
            privateExperiences = emptyList(),
            publicExperiences = emptyList(),
            redPointCount = 0
        )
        private val allowHttpStatusSet =
            setOf(HttpStatus.FORBIDDEN.value, HttpStatus.BAD_REQUEST.value, HttpStatus.NOT_FOUND.value)

    }
}