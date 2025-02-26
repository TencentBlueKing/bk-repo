/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.generic.artifact.interceptor

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.metadata.interceptor.DownloadInterceptor
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.metadata.util.ProjectServiceHelper.PROJECT_NAME_PATTERN
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.generic.config.GenericProperties
import com.tencent.bkrepo.generic.service.UserShareService
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class UserShareInterceptor(
    private val genericProperties: GenericProperties,
    private val userShareService: UserShareService,
    private val permissionManager: PermissionManager
) : DownloadInterceptor<Unit, NodeDetail>(emptyMap())  {

    override fun matcher(artifact: NodeDetail, rule: Unit): Boolean {
        val properties = genericProperties.userShareInterceptor
        if (!properties.enabled) return true
        if (properties.repoName != artifact.repoName) return true
        if (!artifact.fullPath.matches(Regex(properties.pathRegex))) return true
        if (isProjectAdmin(artifact.projectId)) return true
        val referer = HttpContextHolder.getRequestOrNull()?.getHeader("referer") ?: run {
            logger.info("not found request header referer")
            return false
        }
        val shareUrlPath = referer.toHttpUrlOrNull()?.toUrl()?.path ?: run {
            logger.info("referer[$referer] convert to url failed")
            return false
        }
        if (!Regex(SHARE_URL_PATH_FORMAT).matches(shareUrlPath)) {
            logger.info("referer[$referer] is not a valid share url")
            return false
        }
        val shareId = referer.split("/").last()
        val shareRecord = try {
            userShareService.findById(shareId)
        } catch (e: NotFoundException) {
            logger.info("user share[$shareId] not found")
            return false
        }
        if (shareRecord.projectId != artifact.projectId) {
            logger.info("user share[$shareId] projectId not match")
            return false
        }
        if (shareRecord.repoName != artifact.repoName) {
            logger.info("user share[$shareId] repoName not match")
            return false
        }
        if (!artifact.fullPath.startsWith(shareRecord.path)) {
            logger.info("user share[$shareId] fullPath not match")
            return false
        }
        return true
    }

    override fun parseRule() {
        return
    }

    private fun isProjectAdmin(projectId: String): Boolean {
        return try {
            permissionManager.checkProjectPermission(PermissionAction.MANAGE, projectId)
            true
        } catch (e: PermissionException) {
            false
        }

    }

    companion object {
        private const val SHARE_URL_PATH_FORMAT = "/ui/$PROJECT_NAME_PATTERN/share/[a-f0-9]{24}\$"
        private val logger = LoggerFactory.getLogger(UserShareInterceptor::class.java)
    }
}
