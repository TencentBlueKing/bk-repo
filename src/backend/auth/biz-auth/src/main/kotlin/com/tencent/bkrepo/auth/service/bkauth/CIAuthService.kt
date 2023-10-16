/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.auth.service.bkauth

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.auth.condition.DevopsAuthCondition
import com.tencent.bkrepo.auth.config.DevopsAuthConfig
import com.tencent.bkrepo.auth.pojo.BkciAuthCheckResponse
import com.tencent.bkrepo.auth.pojo.BkciAuthListResponse
import com.tencent.bkrepo.auth.pojo.enums.BkAuthPermission
import com.tencent.bkrepo.auth.pojo.enums.BkAuthResourceType
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.util.HttpUtils
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.util.JsonUtils.objectMapper
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
@Conditional(DevopsAuthCondition::class)
class CIAuthService @Autowired constructor(private val devopsAuthConfig: DevopsAuthConfig) {

    private val okHttpClient = okhttp3.OkHttpClient.Builder().connectTimeout(3L, TimeUnit.SECONDS)
        .readTimeout(5L, TimeUnit.SECONDS)
        .writeTimeout(5L, TimeUnit.SECONDS).build()

    private val resourcePermissionCache = CacheBuilder.newBuilder().maximumSize(20000)
        .expireAfterWrite(60, TimeUnit.SECONDS).build<String, Boolean>()


    fun isProjectMember(user: String, projectCode: String): Boolean {
        val cacheKey = "$user::$projectCode"
        val cacheResult = resourcePermissionCache.getIfPresent(cacheKey)
        cacheResult?.let {
            logger.debug("match in cache: $cacheKey|$cacheResult")
            return cacheResult
        }
        var hasPermission = false

        val url = "${devopsAuthConfig.getBkciAuthServer()}/auth/api/open/service/auth/projects/$projectCode" +
                "/users/$user/isProjectUsers"
        return try {
            val request = Request.Builder().url(url).header(DEVOPS_BK_TOKEN, devopsAuthConfig.getBkciAuthToken())
                .header(DEVOPS_PROJECT_ID, projectCode).get().build()
            val apiResponse = HttpUtils.doRequest(okHttpClient, request, 2, allowHttpStatusSet)
            logger.debug("validateProjectUsers url[$url],result[${apiResponse.code},${apiResponse.content}]")
            if (apiResponse.code == HttpStatus.OK.value) {
                val responseObject = objectMapper.readValue<BkciAuthCheckResponse>(apiResponse.content)
                if (responseObject.status == 0 && responseObject.data) {
                    hasPermission = true
                }
            }
            resourcePermissionCache.put(cacheKey, hasPermission)
            hasPermission
        } catch (exception: Exception) {
            logger.error("validateProjectUsers url is $url, error: ", exception)
            false
        }
    }

    fun isProjectManager(user: String, projectCode: String): Boolean {
        val cacheKey = "manager::$user::$projectCode"
        val cacheResult = resourcePermissionCache.getIfPresent(cacheKey)
        cacheResult?.let {
            logger.debug("match in cache: $cacheKey|$cacheResult")
            return cacheResult
        }

        val url = "${devopsAuthConfig.getBkciAuthServer()}/auth/api/open/service/auth/projects/$projectCode" +
                "/users/$user/checkProjectManager"
        return try {
            val request = Request.Builder().url(url).header(DEVOPS_BK_TOKEN, devopsAuthConfig.getBkciAuthToken())
                .header(DEVOPS_PROJECT_ID, projectCode).get().build()
            val apiResponse = HttpUtils.doRequest(okHttpClient, request, 2, allowHttpStatusSet)
            val responseObject = objectMapper.readValue<BkciAuthCheckResponse>(apiResponse.content)
            logger.debug("validateProjectManager url[$url], result[${apiResponse.content}]")
            resourcePermissionCache.put(cacheKey, responseObject.data)
            responseObject.data
        } catch (exception: Exception) {
            logger.error("validateProjectManager url is $url, error: ", exception)
            false
        }
    }

    fun isProjectSuperAdmin(
        user: String,
        projectCode: String,
        action: BkAuthPermission,
        resourceType: BkAuthResourceType,
        permissionAction: String?
    ): Boolean {

        if (!devopsAuthConfig.enableSuperAdmin) return false

        if (permissionAction != PermissionAction.READ.toString()) return false

        val cacheKey = "superAdmin::$user::$projectCode"
        val cacheResult = resourcePermissionCache.getIfPresent(cacheKey)
        cacheResult?.let {
            logger.debug("match in cache: $cacheKey|$cacheResult")
            return cacheResult
        }
        var hasPermission = false

        val url = "${devopsAuthConfig.getBkciAuthServer()}/auth/api/open/service/auth/local/manager/" +
                "projects/$projectCode?resourceType=${resourceType.value}&action=${action.value}"
        return try {
            val request = Request.Builder().url(url).header(DEVOPS_UID, user).header(DEVOPS_PROJECT_ID, projectCode)
                .header(DEVOPS_BK_TOKEN, devopsAuthConfig.getBkciAuthToken()).get().build()
            val apiResponse = HttpUtils.doRequest(okHttpClient, request, 2, allowHttpStatusSet)
            logger.debug(
                "validateProjectSuperAdmin , requestUrl: [$url]," + " result : [${
                    apiResponse.content.replace("\n", "")
                }]"
            )
            if (apiResponse.code == HttpStatus.OK.value) {
                val responseObject = objectMapper.readValue<BkciAuthCheckResponse>(apiResponse.content)
                if (responseObject.status == 0 && responseObject.data) {
                    hasPermission = true
                }
            }
            if (apiResponse.code == HttpStatus.NOT_FOUND.value) {
                logger.error("validateProjectSuperAdmin get 404 result: [$url]")
                hasPermission = false
            }
            resourcePermissionCache.put(cacheKey, hasPermission)
            hasPermission
        } catch (exception: Exception) {
            logger.error("validateProjectSuperAdmin error: [$exception]")
            false
        }
    }

    fun validateUserResourcePermission(
        user: String,
        projectCode: String,
        action: BkAuthPermission,
        resourceCode: String,
        resourceType: BkAuthResourceType
    ): Boolean {
        val cacheKey = "$user::$projectCode::${resourceType.value}::$resourceCode::${action.value}"
        val cacheResult = resourcePermissionCache.getIfPresent(cacheKey)
        cacheResult?.let {
            logger.debug("match in cache: $cacheKey|$cacheResult")
            return cacheResult
        }

        val url = "${devopsAuthConfig.getBkciAuthServer()}/auth/api/open/service/auth/permission" +
                "/projects/$projectCode/relation/validate?" +
                "action=${action.value}&resourceCode=$resourceCode&resourceType=${resourceType.value}"
        return try {
            val request = Request.Builder().url(url).header(DEVOPS_BK_TOKEN, devopsAuthConfig.getBkciAuthToken())
                .header(DEVOPS_UID, user).header(DEVOPS_PROJECT_ID, projectCode).get().build()
            val apiResponse = HttpUtils.doRequest(okHttpClient, request, 2, allowHttpStatusSet)
            val responseObject = objectMapper.readValue<BkciAuthCheckResponse>(apiResponse.content)
            logger.debug("validateUserResourcePermission,requestUrl: [$url], result : [${apiResponse.content}]")
            resourcePermissionCache.put(cacheKey, responseObject.data)
            responseObject.data
        } catch (exception: Exception) {
            logger.error("validateUserResourcePermission error:", exception)
            false
        }
    }

    fun getUserResourceByPermission(
        user: String, projectCode: String, action: BkAuthPermission, resourceType: BkAuthResourceType
    ): List<String> {
        return try {
            val url = "${devopsAuthConfig.getBkciAuthServer()}/auth/api/open/service/auth/permission/" +
                    "projects/$projectCode/action/instance?" +
                    "action=${action.value}&resourceType=${resourceType.value}"
            val request = Request.Builder().url(url).header(DEVOPS_BK_TOKEN, devopsAuthConfig.getBkciAuthToken())
                .header(DEVOPS_UID, user).header(DEVOPS_PROJECT_ID, projectCode).get().build()
            val apiResponse = HttpUtils.doRequest(okHttpClient, request, 2, allowHttpStatusSet)
            val responseObject = objectMapper.readValue<BkciAuthListResponse>(apiResponse.content)
            logger.debug("getUserResourceByPermission, requestUrl: [$url], result : [${apiResponse.content}]")
            return responseObject.data
        } catch (exception: Exception) {
            logger.error("getUserResourceByPermission error: ", exception)
            emptyList()
        }
    }

    fun getProjectListByUser(user: String): List<String> {
        return try {
            val url = "${devopsAuthConfig.getBkciAuthServer()}/auth/api/open/service/auth/projects/users/$user"
            val request = Request.Builder().url(url).header(DEVOPS_BK_TOKEN, devopsAuthConfig.getBkciAuthToken())
                .header(DEVOPS_GATEWAY_TAG, DEVOPS_PROD_V3).header(DEVOPS_UID, user).get().build()
            val apiResponse = HttpUtils.doRequest(okHttpClient, request, 2, allowHttpStatusSet)
            val responseObject = objectMapper.readValue<BkciAuthListResponse>(apiResponse.content)
            logger.debug("getProjectListByUser, requestUrl: [$url], result : [${apiResponse.content}]")
            return responseObject.data
        } catch (exception: Exception) {
            logger.error("getProjectListByUser error: ", exception)
            emptyList()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CIAuthService::class.java)
        private val allowHttpStatusSet =
            setOf(HttpStatus.FORBIDDEN.value, HttpStatus.BAD_REQUEST.value, HttpStatus.NOT_FOUND.value)
        const val DEVOPS_BK_TOKEN = "X-DEVOPS-BK-TOKEN"
        const val DEVOPS_UID = "X-DEVOPS-UID"
        const val DEVOPS_PROJECT_ID = "X-DEVOPS-PROJECT-ID"
        const val DEVOPS_GATEWAY_TAG = "x-gateway-tag"
        const val DEVOPS_PROD_V3 = "prod-v3"
    }
}
