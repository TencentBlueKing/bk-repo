/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.security.interceptor.devx.DevXProperties
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.generic.config.ItsmProperties
import com.tencent.bkrepo.generic.dao.UserShareApprovalDao
import com.tencent.bkrepo.generic.dao.UserShareRecordDao
import com.tencent.bkrepo.generic.exception.ApprovalNotFoundException
import com.tencent.bkrepo.generic.exception.UserShareNotFoundException
import com.tencent.bkrepo.generic.model.TUserShareApproval
import com.tencent.bkrepo.generic.model.TUserShareRecord
import com.tencent.bkrepo.generic.pojo.share.DevxManagerResponse
import com.tencent.bkrepo.generic.pojo.share.UserShareApprovalInfo
import com.tencent.bkrepo.generic.pojo.share.WorkspaceFile
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.devops.api.pojo.Response
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class UserShareApprovalService(
    private val itsmService: ItsmService,
    private val itsmProperties: ItsmProperties,
    private val userShareApprovalDao: UserShareApprovalDao,
    private val userShareRecordDao: UserShareRecordDao,
    private val nodeService: NodeService,
    private val devXProperties: DevXProperties
) {

    private val httpClient = OkHttpClient.Builder().build()

    fun createApproval(shareId: String): UserShareApprovalInfo {
        val userId = SecurityUtils.getUserId()
        userShareApprovalDao.findByShareId(shareId, userId)?.let {
            return it.convert()
        }
        val shareRecord = userShareRecordDao.findById(shareId)
            ?: throw UserShareNotFoundException(shareId)
        val ticket = itsmService.createTicket(
            fields = buildTicketFields(shareRecord, userId),
            serviceId = itsmProperties.shareFileServiceId,
            approvalStateId = itsmProperties.shareFileApprovalStateId,
            approvalUsers = getRemoteDevManager(shareRecord.projectId)
        )
        val approval = TUserShareApproval(
            shareId = shareId,
            approvalId = ticket.sn,
            approvalTicketUrl = ticket.ticketUrl,
            downloadUserId = userId,
            approved = false,
            createDate = LocalDateTime.now(),
        )
        return try {
            userShareApprovalDao.save(approval).convert()
        } catch (_: DuplicateKeyException) {
            logger.info("user[$userId] create duplicated approval for share[$shareId]")
            userShareApprovalDao.findByShareId(shareId, userId)!!.convert()
        }
    }

    private fun buildTicketFields(
        shareRecord: TUserShareRecord,
        userId: String
    ): List<Map<String, Any>> {
        val nodes = nodeService.listNode(
            artifact = ArtifactInfo(shareRecord.projectId, shareRecord.repoName, shareRecord.path),
            option = NodeListOption()
        )
        if (nodes.isEmpty() && shareRecord.workspaceFiles.isNullOrEmpty()) {
            throw NodeNotFoundException(shareRecord.path)
        }

        // TODO 暂时只允许分享一个文件
        val file = if (shareRecord.workspaceFiles.isNullOrEmpty()) {
            WorkspaceFile(null, nodes.first().fullPath, nodes.first().size, nodes.first().md5 ?: StringPool.UNKNOWN)
        } else {
            shareRecord.workspaceFiles.first()
        }
        val fields = listOf(
            mapOf(
                "key" to "title",
                "value" to "下载云桌面分享文件"
            ),
            mapOf(
                "key" to "shareUser",
                "value" to shareRecord.createBy,
            ),
            mapOf(
                "key" to "workspaceName",
                "value" to (shareRecord.workspaceName ?: StringPool.UNKNOWN),
            ),
            mapOf(
                "key" to "downloadUser",
                "value" to userId,
            ),
            mapOf(
                "key" to "downloadFilePath",
                "value" to file.fullPath
            ),
            mapOf(
                "key" to "downloadFileSize",
                "value" to HumanReadable.size(file.size)
            ),
            mapOf(
                "key" to "downloadFileMd5",
                "value" to file.md5
            )
        )
        return fields
    }

    private fun getApproval(approvalId: String?, shareId: String?): TUserShareApproval {
        val userId = SecurityUtils.getUserId()
        return if (!approvalId.isNullOrEmpty()) {
            userShareApprovalDao.findByApprovalId(approvalId, userId)
                ?: throw ApprovalNotFoundException(approvalId)
        } else if (!shareId.isNullOrEmpty()) {
            userShareApprovalDao.findByShareId(shareId, userId)
                ?: throw ApprovalNotFoundException(shareId)
        } else {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_EMPTY, "approvalId or shareId")
        }
    }

    fun getApprovalInfo(approvalId: String?, shareId: String?): UserShareApprovalInfo {
        return getApproval(approvalId, shareId).convert()
    }

    fun checkApprovalStatus(shareId: String): TUserShareApproval {
        val approval = getApproval(null, shareId)
        if (!approval.approved) {
            throw ErrorCodeException(CommonMessageCode.REQUEST_DENIED, shareId)
        }
        return approval
    }

    fun callback(approvalId: String, approveUserId: String) {
        if (!userShareApprovalDao.approve(approvalId, approveUserId)) {
            throw ApprovalNotFoundException(approvalId)
        }
    }

    private fun getRemoteDevManager(projectId: String): List<String> {
        val url = "${devXProperties.projectManagerUrl}?project_id=$projectId"
        val request = Request.Builder().url(url)
            .header("X-Bkapi-Authorization", headerStr())
            .build()
        try {
            httpClient.newCall(request).execute().use {
                val body = it.body!!.string()
                if (!it.isSuccessful) {
                    logger.error("request $url failed: ${it.code}, $body")
                    throw ErrorCodeException(CommonMessageCode.SERVICE_CALL_ERROR)
                }
                val devxManagerResponse = body.readJsonString<Response<List<DevxManagerResponse>>>().data!!.first()
                return devxManagerResponse.remotedevManager.split(";")
            }
        } catch (e: Exception) {
            logger.error("request $url failed: ${e.message}")
            throw throw ErrorCodeException(CommonMessageCode.SERVICE_CALL_ERROR)
        }
    }

    private fun headerStr(): String {
        return mapOf(
            "bk_app_code" to devXProperties.appCode,
            "bk_app_secret" to devXProperties.appSecret,
            "bk_username" to SecurityUtils.getUserId()
        ).toJsonString().replace("\\s".toRegex(), "")
    }

    private fun TUserShareApproval.convert(): UserShareApprovalInfo {
        return UserShareApprovalInfo(
            shareId = shareId,
            downloadUserId = downloadUserId,
            approvalId = approvalId,
            approvalTicketUrl = approvalTicketUrl,
            createDate = createDate,
            approved = approved,
            approveUserId = approveUserId,
            approveDate = approveDate
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserShareApprovalService::class.java)
    }
}
