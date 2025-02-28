/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.websocket.service

import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.user.CreateUserRequest
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.http.jwt.JwtAuthProperties
import com.tencent.bkrepo.common.security.interceptor.devx.ApiAuth
import com.tencent.bkrepo.common.security.interceptor.devx.DevXProperties
import com.tencent.bkrepo.common.security.interceptor.devx.DevXWorkSpace
import com.tencent.bkrepo.common.security.util.JwtUtils
import com.tencent.bkrepo.common.service.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.fs.server.constant.JWT_CLAIMS_PERMIT
import com.tencent.bkrepo.fs.server.constant.JWT_CLAIMS_REPOSITORY
import com.tencent.bkrepo.websocket.dispatch.TransferDispatch
import com.tencent.bkrepo.websocket.dispatch.push.CopyPDUTransferPush
import com.tencent.bkrepo.websocket.dispatch.push.PastePDUTransferPush
import com.tencent.bkrepo.websocket.pojo.fs.CopyPDU
import com.tencent.bkrepo.websocket.pojo.fs.PastePDU
import com.tencent.devops.api.pojo.Response
import okhttp3.Request
import okio.IOException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ClipboardService(
    private val transferDispatch: TransferDispatch,
    private val devXProperties: DevXProperties,
    private val jwtAuthProperties: JwtAuthProperties,
    private val permissionManager: PermissionManager,
    private val serviceUserClient: ServiceUserClient
) {

    private val httpClient = HttpClientBuilderFactory.create().build()
    private val signingKey = JwtUtils.createSigningKey(jwtAuthProperties.secretKey)

    fun copy(userId: String, copyPDU: CopyPDU) {
        logger.info("userId: $userId, CopyPDU: $copyPDU")
        val token = generateToken(userId, copyPDU)
        copyPDU.token = token
        val copyPDUTransferPush = CopyPDUTransferPush(copyPDU)
        transferDispatch.dispatch(copyPDUTransferPush)
    }

    fun paste(pastePDU: PastePDU) {
        logger.info("PastePDU: $pastePDU")
        val pastePDUTransferPush = PastePDUTransferPush(pastePDU)
        transferDispatch.dispatch(pastePDUTransferPush)
    }

    /**
     * 云桌面拥有者上传文件时，不再生成token
     * 云桌面分享人上传文件时，生成分享人的token
     */
    private fun generateToken(userId: String, copyPDU: CopyPDU): String? {
        if (userId != copyPDU.userId) {
            throw PermissionException("can't send copy pdu with userId[${copyPDU.userId}]")
        }
        if (devXProperties.groupWorkspaceUrl.isEmpty() || devXProperties.personalWorkspaceUrl.isEmpty()) {
            return null
        }
        return if (!copyPDU.envHashId.isNullOrEmpty()) {
            checkEnvWorkspace(copyPDU)
        } else if (copyPDU.projectId.startsWith(StringPool.UNDERSCORE)) {
            checkWorkspace(devXProperties.personalWorkspaceUrl, copyPDU)
        } else {
            checkWorkspace(devXProperties.groupWorkspaceUrl, copyPDU)
        }
    }

    /**
     * 如果是分享人，用户第一次上传文件时，系统内还没有用户，会鉴权失败
     */
    private fun createUser(userId: String) {
        val request = CreateUserRequest(
            userId = userId,
            name = userId,
        )
        serviceUserClient.createUser(request)
    }

    private fun checkEnvWorkspace(copyPDU: CopyPDU): String? {
        val userId = copyPDU.userId
        val url = String.format(devXProperties.workspaceEnvUsePermissionUrlFormat, copyPDU.projectId, copyPDU.envHashId)
        val request = Request.Builder().url(url)
            .header("X-DEVOPS-BK-TOKEN", devXProperties.authToken)
            .header("X-Devops-Uid", userId)
            .get()
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.error(
                        "request url failed: " +
                            "${request.url}, ${response.code}, ${response.headers["X-Devops-RID"]}"
                    )
                    return null
                }

                val hasPermission = response.body!!.string().readJsonString<Response<Boolean>>().data!!
                if (!hasPermission) {
                    throw PermissionException("user[$userId] is not the member of [${copyPDU.envHashId}]")
                }
                createUser(userId)
                return createToken(copyPDU.projectId, userId)
            }
        } catch (e: IOException) {
            logger.error("Error while processing request: ${e.message}")
            return null
        }
    }

    private fun checkWorkspace(url: String, copyPDU: CopyPDU): String? {
        val userId = copyPDU.userId
        val apiAuth = ApiAuth(devXProperties.appCode, devXProperties.appSecret)
        val token = apiAuth.toJsonString().replace(System.lineSeparator(), "")
        val request = Request.Builder().url(
            "$url?projectId=${copyPDU.projectId}&workspaceName=${copyPDU.workspaceName}"
        )
            .header("X-Bkapi-Authorization", token)
            .header("X-Devops-Uid", userId)
            .get()
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.error(
                        "request url failed: " +
                            "${request.url}, ${response.code}, ${response.headers["X-Devops-RID"]}"
                    )
                    return null
                }
                val workspace = response.body!!.string().readJsonString<Response<DevXWorkSpace>>().data
                    ?: throw PermissionException("can't find workspace by name[${copyPDU.workspaceName}]")
                logger.debug("workspace: {}", workspace)
                if (workspace.realOwner != userId && !workspace.viewers.contains(userId)) {
                    throw PermissionException("user[$userId] is not the owner or viewer of [${copyPDU.workspaceName}]")
                }
                return if (workspace.realOwner == userId) {
                    null
                } else {
                    createUser(userId)
                    createToken(copyPDU.projectId, userId)
                }
            }
        } catch (e: IOException) {
            logger.error("Error while processing request: ${e.message}")
            return null
        }
    }

    private fun createToken(projectId: String, userId: String): String {
        val claims = mutableMapOf(JWT_CLAIMS_REPOSITORY to "$projectId/$REPO_NAME")
        permissionManager.checkRepoPermission(
            action = PermissionAction.DOWNLOAD,
            projectId = projectId,
            repoName = REPO_NAME,
            userId = userId
        )
        claims[JWT_CLAIMS_PERMIT] = PermissionAction.READ.name
        return JwtUtils.generateToken(signingKey, jwtAuthProperties.expiration, userId, claims)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClipboardService::class.java)
        private const val REPO_NAME = "lsync"
    }
}
