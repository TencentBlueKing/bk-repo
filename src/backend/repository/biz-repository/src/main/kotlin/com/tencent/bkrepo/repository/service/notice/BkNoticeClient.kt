/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.repository.service.notice

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.bk.sdk.notice.model.resp.AnnouncementDTO
import com.tencent.bkrepo.common.api.constant.TENANT_ID
import com.tencent.bkrepo.repository.config.BkNoticeProperties
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * 蓝鲸通知中心 API 响应结构
 */
data class BkNoticeResponse(
    @JsonProperty("result")
    val result: Boolean = false,
    @JsonProperty("code")
    val code: Int = 0,
    @JsonProperty("data")
    val data: List<BkAnnouncement>? = null,
    @JsonProperty("message")
    val message: String? = null
)

/**
 * 公告数据结构
 */
data class BkAnnouncement(
    @JsonProperty("id")
    val id: Long? = null,
    @JsonProperty("title")
    val title: String? = null,
    @JsonProperty("content")
    val content: String? = null,
    @JsonProperty("content_list")
    val contentList: List<ContentItem>? = null,
    @JsonProperty("announce_type")
    val announceType: String? = null,
    @JsonProperty("start_time")
    val startTime: String? = null,
    @JsonProperty("end_time")
    val endTime: String? = null
)

/**
 * 多语言内容项
 */
data class ContentItem(
    @JsonProperty("content")
    val content: String? = null,
    @JsonProperty("language")
    val language: String? = null
)



/**
 * 蓝鲸通知中心客户端，支持多租户
 */
class BkNoticeClient(
    private val properties: BkNoticeProperties,
    private val tenantId: String? = null
) {
    private val objectMapper = ObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 获取当前公告列表
     *
     * @param lang 语言，如 zh-cn, en
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 公告列表
     */
    fun getCurrentAnnouncements(
        lang: String,
        offset: Int? = null,
        limit: Int? = null
    ): List<AnnouncementDTO> {
        val url = buildUrl(lang, offset, limit)
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("X-Bkapi-Authorization", buildAuthHeader())

        // 添加租户 header
        if (!tenantId.isNullOrBlank()) {
            requestBuilder.header(TENANT_ID, tenantId)
        }

        val request = requestBuilder.build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.warn("Failed to get announcements, code: ${response.code}, msg: ${response.message}")
                    return emptyList()
                }
                
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    logger.warn("Empty response body when getting announcements")
                    return emptyList()
                }
                
                logger.debug("Get announcements response: $body")
                parseResponse(body)
            }
        } catch (e: Exception) {
            logger.error("Error getting announcements from url: $url", e)
            emptyList()
        }
    }
    
    /**
     * 解析响应数据
     */
    private fun parseResponse(body: String): List<AnnouncementDTO> {
        return try {
            val response = objectMapper.readValue(body, BkNoticeResponse::class.java)
            
            // 检查返回结果
            if (!response.result) {
                logger.warn("Failed to get announcements from API, code: ${response.code}, msg: ${response.message}")
                return emptyList()
            }
            
            // 将 BkAnnouncement 转换为 AnnouncementDTO
            response.data?.map { item ->
                val dto = AnnouncementDTO()
                dto.id = item.id
                dto.title = item.title
                dto.content = item.content
                dto.startTime = item.startTime
                dto.endTime = item.endTime
                dto.announceType = item.announceType
                
                // 转换 contentList
                item.contentList?.let { contentList ->
                    val dtoContentList = contentList.map { contentItem ->
                        val contentWithLanguage = AnnouncementDTO.ContentWithLanguage()
                        contentWithLanguage.content = contentItem.content
                        contentWithLanguage.language = contentItem.language
                        contentWithLanguage
                    }
                    dto.contentList = dtoContentList
                }
                
                dto
            } ?: emptyList()
        } catch (e: Exception) {
            logger.error("Failed to parse response body: $body", e)
            emptyList()
        }
    }

    private fun buildUrl(lang: String, offset: Int?, limit: Int?): String {
        val baseUrl = properties.apiBaseUrl.trimEnd('/')
        val params = mutableListOf<String>()

        params.add("platform=${properties.appCode}")
        params.add("language=$lang")

        offset?.let { params.add("offset=$it") }
        limit?.let { params.add("limit=$it") }

        return "$baseUrl/apigw/v1/announcement/get_current_announcements/?" + params.joinToString("&")
    }

    private fun buildAuthHeader(): String {
        return "{\"bk_app_code\":\"${properties.appCode}\",\"bk_app_secret\":\"${properties.appSecret}\"}"
    }


    companion object {
        private val logger = LoggerFactory.getLogger(BkNoticeClient::class.java)
    }
}
