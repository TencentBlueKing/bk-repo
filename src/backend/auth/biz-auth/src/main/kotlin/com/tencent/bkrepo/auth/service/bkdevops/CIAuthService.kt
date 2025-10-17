/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.auth.service.bkdevops

import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.auth.condition.DevopsAuthCondition
import com.tencent.bkrepo.auth.config.DevopsAuthConfig
import com.tencent.bkrepo.auth.dao.UserDao
import com.tencent.bkrepo.auth.pojo.BkciAuthCheckResponse
import com.tencent.bkrepo.auth.pojo.BkciAuthListResponse
import com.tencent.bkrepo.auth.pojo.BkciRoleListResponse
import com.tencent.bkrepo.auth.pojo.BkciRoleResult
import com.tencent.bkrepo.auth.pojo.enums.BkAuthPermission
import com.tencent.bkrepo.auth.pojo.enums.BkAuthResourceType
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.util.HttpUtils
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.TENANT_ID
import com.tencent.bkrepo.common.api.util.JsonUtils.objectMapper
import com.tencent.bkrepo.common.artifact.properties.EnableMultiTenantProperties
import com.tencent.bkrepo.common.api.util.okhttp.HttpClientBuilderFactory
import io.micrometer.observation.ObservationRegistry
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
@Conditional(DevopsAuthCondition::class)
class CIAuthService @Autowired constructor(
    private val devopsAuthConfig: DevopsAuthConfig,
    private val userDao: UserDao,
    private val enableMultiTenant: EnableMultiTenantProperties,
    registry: ObservationRegistry
) {

    private val okHttpClient = HttpClientBuilderFactory.create(registry = registry)
        .connectTimeout(HTTP_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(HTTP_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(HTTP_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()

    private val resourcePermissionCache = CacheBuilder.newBuilder()
        .maximumSize(CACHE_MAX_SIZE)
        .expireAfterWrite(CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS)
        .build<String, Boolean>()

    fun Request.addTenantHeaderIfNeeded(userId: String): Request {
        val userInfo = userDao.findFirstByUserId(userId)
        logger.debug("addTenantHeaderIfNeeded [$userId, $userInfo]")
        if (!enableMultiTenant.enabled || userInfo == null) {
            return this
        }
        if (userInfo.tenantId == null) {
            return this
        }
        return newBuilder().header(TENANT_ID, userInfo.tenantId!!).build()
    }

    fun isProjectMember(user: String, projectCode: String): Boolean {
        val cacheKey = CacheKeyBuilder.projectMember(user, projectCode)
        return getCachedOrCompute(cacheKey) {
            val url = AuthApiUrls.isProjectUser(devopsAuthConfig.getBkciAuthServer(), projectCode, user)
            executeAuthRequest<BkciAuthCheckResponse>(
                url = url,
                user = user,
                projectCode = projectCode,
                logPrefix = "validateProjectUsers"
            )?.let { it.status == 0 && it.data } ?: false
        }
    }

    fun isProjectManager(user: String, projectCode: String): Boolean {
        val cacheKey = CacheKeyBuilder.projectManager(user, projectCode)
        return getCachedOrCompute(cacheKey) {
            val url = AuthApiUrls.checkProjectManager(devopsAuthConfig.getBkciAuthServer(), projectCode, user)
            executeAuthRequest<BkciAuthCheckResponse>(
                url = url,
                user = user,
                projectCode = projectCode,
                logPrefix = "validateProjectManager"
            )?.data ?: false
        }
    }

    fun isProjectSuperAdmin(
        user: String,
        projectCode: String,
        resourceType: BkAuthResourceType,
        action: String?
    ): Boolean {
        if (!devopsAuthConfig.enableSuperAdmin) return false
        if (action != PermissionAction.READ.toString()) return false

        val cacheKey = CacheKeyBuilder.superAdmin(user, projectCode)
        return getCachedOrCompute(cacheKey) {
            val url = AuthApiUrls.checkSuperAdmin(
                devopsAuthConfig.getBkciAuthServer(),
                projectCode,
                resourceType.value,
                BkAuthPermission.DOWNLOAD.value
            )
            executeAuthRequest<BkciAuthCheckResponse>(
                url = url,
                user = user,
                projectCode = projectCode,
                logPrefix = "validateProjectSuperAdmin"
            )?.let { it.status == 0 && it.data } ?: false
        }
    }

    fun validateUserResourcePermission(
        user: String,
        projectCode: String,
        action: BkAuthPermission,
        resourceCode: String,
        resourceType: BkAuthResourceType
    ): Boolean {
        val cacheKey = CacheKeyBuilder.resourcePermission(
            user, projectCode, resourceType.value, resourceCode, action.value
        )
        return getCachedOrCompute(cacheKey) {
            val url = AuthApiUrls.validateResourcePermission(
                devopsAuthConfig.getBkciAuthServer(),
                projectCode,
                action.value,
                resourceCode,
                resourceType.value
            )
            executeAuthRequest<BkciAuthCheckResponse>(
                url = url,
                user = user,
                projectCode = projectCode,
                logPrefix = "validateUserResourcePermission"
            )?.data ?: false
        }
    }

    fun getUserResourceByPermission(
        user: String, projectCode: String, action: BkAuthPermission, resourceType: BkAuthResourceType
    ): List<String> {
        val url = AuthApiUrls.getUserResourceByPermission(
            devopsAuthConfig.getBkciAuthServer(),
            projectCode,
            action.value,
            resourceType.value
        )
        return executeAuthRequest<BkciAuthListResponse>(
            url = url,
            user = user,
            projectCode = projectCode,
            logPrefix = "getUserResourceByPermission"
        )?.data ?: emptyList()
    }

    fun getProjectListByUser(user: String): List<String> {
        val url = AuthApiUrls.getProjectListByUser(devopsAuthConfig.getBkciAuthServer(), user)
        return executeAuthRequest<BkciAuthListResponse>(
            url = url,
            user = user,
            logPrefix = "getProjectListByUser"
        )?.data ?: emptyList()
    }

    fun getRoleAndUserByProject(projectCode: String): List<BkciRoleResult> {
        val url = AuthApiUrls.getRoleAndUserByProject(devopsAuthConfig.getBkciAuthServer(), projectCode)
        return executeAuthRequest<BkciRoleListResponse>(
            url = url,
            logPrefix = "getRoleAndUserByProject"
        )?.data ?: emptyList()
    }

    /**
     * 执行认证请求的通用方法
     */
    private inline fun <reified T> executeAuthRequest(
        url: String,
        user: String? = null,
        projectCode: String? = null,
        logPrefix: String
    ): T? {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header(DEVOPS_BK_TOKEN, devopsAuthConfig.getBkciAuthToken())
                .get()
            
            user?.let { requestBuilder.header(DEVOPS_UID, it) }
            projectCode?.let { requestBuilder.header(DEVOPS_PROJECT_ID, it) }
            
            val request = requestBuilder.build().let { 
                if (user != null) it.addTenantHeaderIfNeeded(user) else it 
            }
            
            val apiResponse = HttpUtils.doRequest(okHttpClient, request, HTTP_RETRY_COUNT, allowHttpStatusSet)
            logger.debug("$logPrefix, requestUrl: [$url], result: [${apiResponse.content}]")
            
            objectMapper.readValue<T>(apiResponse.content)
        } catch (exception: InvalidFormatException) {
            logger.info("$logPrefix url is $url, error: ", exception)
            null
        } catch (exception: Exception) {
            logger.error("$logPrefix error: ", exception)
            null
        }
    }

    /**
     * 从缓存获取或计算结果
     */
    private fun getCachedOrCompute(cacheKey: String, compute: () -> Boolean): Boolean {
        resourcePermissionCache.getIfPresent(cacheKey)?.let {
            logger.debug("match in cache: $cacheKey|$it")
            return it
        }
        
        val result = compute()
        resourcePermissionCache.put(cacheKey, result)
        return result
    }

    /**
     * 缓存键构建器
     */
    private object CacheKeyBuilder {
        fun projectMember(user: String, projectCode: String) = "$user::$projectCode"
        fun projectManager(user: String, projectCode: String) = "manager::$user::$projectCode"
        fun superAdmin(user: String, projectCode: String) = "superAdmin::$user::$projectCode"
        fun resourcePermission(
            user: String,
            projectCode: String,
            resourceType: String,
            resourceCode: String,
            action: String
        ) = "$user::$projectCode::$resourceType::$resourceCode::$action"
    }

    /**
     * 认证API URL构建器
     */
    private object AuthApiUrls {
        private const val AUTH_BASE_PATH = "/auth/api/open/service/auth"
        
        fun isProjectUser(baseUrl: String, projectCode: String, user: String) =
            "$baseUrl$AUTH_BASE_PATH/projects/$projectCode/users/$user/isProjectUsers"
        
        fun checkProjectManager(baseUrl: String, projectCode: String, user: String) =
            "$baseUrl$AUTH_BASE_PATH/projects/$projectCode/users/$user/checkProjectManager"
        
        fun checkSuperAdmin(baseUrl: String, projectCode: String, resourceType: String, action: String) =
            "$baseUrl$AUTH_BASE_PATH/local/manager/projects/$projectCode?resourceType=$resourceType&action=$action"
        
        fun validateResourcePermission(
            baseUrl: String,
            projectCode: String,
            action: String,
            resourceCode: String,
            resourceType: String
        ) = "$baseUrl$AUTH_BASE_PATH/permission/projects/$projectCode/relation/validate?" +
            "action=$action&resourceCode=$resourceCode&resourceType=$resourceType"
        
        fun getUserResourceByPermission(
            baseUrl: String,
            projectCode: String,
            action: String,
            resourceType: String
        ) = "$baseUrl$AUTH_BASE_PATH/permission/projects/$projectCode/action/instance?" +
            "action=$action&resourceType=$resourceType"
        
        fun getProjectListByUser(baseUrl: String, user: String) =
            "$baseUrl$AUTH_BASE_PATH/projects/users/$user"
        
        fun getRoleAndUserByProject(baseUrl: String, projectCode: String) =
            "$baseUrl$AUTH_BASE_PATH/projects/$projectCode/users"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CIAuthService::class.java)
        
        // HTTP请求相关常量
        private val allowHttpStatusSet = setOf(
            HttpStatus.FORBIDDEN.value,
            HttpStatus.BAD_REQUEST.value,
            HttpStatus.NOT_FOUND.value
        )
        private const val HTTP_CONNECT_TIMEOUT_SECONDS = 3L
        private const val HTTP_READ_TIMEOUT_SECONDS = 5L
        private const val HTTP_WRITE_TIMEOUT_SECONDS = 5L
        private const val HTTP_RETRY_COUNT = 2
        
        // 缓存相关常量
        private const val CACHE_MAX_SIZE = 20000L
        private const val CACHE_EXPIRE_SECONDS = 60L
        
        // HTTP头常量
        const val DEVOPS_BK_TOKEN = "X-DEVOPS-BK-TOKEN"
        const val DEVOPS_UID = "X-DEVOPS-UID"
        const val DEVOPS_PROJECT_ID = "X-DEVOPS-PROJECT-ID"
    }
}
