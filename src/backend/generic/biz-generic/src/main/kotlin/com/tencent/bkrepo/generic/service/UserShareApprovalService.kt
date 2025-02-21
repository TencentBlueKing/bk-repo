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

package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
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
import com.tencent.bkrepo.generic.pojo.share.DevxManagerResponse
import com.tencent.bkrepo.generic.pojo.share.ItsmTicket
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.devops.api.pojo.Response
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
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

    fun createApproval(shareId: String): ItsmTicket {
        val userId = SecurityUtils.getUserId()
        val shareRecord = userShareRecordDao.findById(shareId)
            ?: throw UserShareNotFoundException(shareId)
        val nodes = nodeService.listNode(
            artifact = ArtifactInfo(shareRecord.projectId, shareRecord.repoName, shareRecord.path),
            option = NodeListOption()
        )
        if (nodes.isEmpty()) {
            throw NodeNotFoundException(shareRecord.path)
        }
        val downloadFiles = if (nodes.size == 1) {
            nodes.first().name
        } else {
            nodes.joinToString(StringPool.COMMA) { it.name }
        }
        val fields = listOf(
            mapOf(
                "key" to "title",
                "value" to "下载云桌面分享文件"
            ),
            mapOf(
                "key" to "shareUser",
                "value" to nodes.first().createdBy,
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
                "key" to "downloadFiles",
                "value" to downloadFiles
            )
        )
        val ticket = itsmService.createTicket(
            fields = fields,
            serviceId = itsmProperties.shareFileServiceId,
            approvalStateId = itsmProperties.shareFileApprovalStateId,
            approvalUsers = getRemoteDevManager(shareRecord.projectId)
        )
        val approval = TUserShareApproval(
            shareId = shareId,
            approvalId = ticket.sn,
            downloadUserId = SecurityUtils.getUserId(),
            approved = false,
            createDate = LocalDateTime.now(),
        )
        userShareApprovalDao.save(approval)
        return ticket
    }

    private fun getApproval(approvalId: String?, shareId: String?): TUserShareApproval {
        val userId = SecurityUtils.getUserId()
        return if (!approvalId.isNullOrEmpty()) {
            userShareApprovalDao.findByApprovalId(approvalId, userId)
                ?: throw ApprovalNotFoundException(approvalId)
        } else if (!shareId.isNullOrEmpty()) {
            val approvals = userShareApprovalDao.findByShareId(shareId, userId)
            if (approvals.isEmpty()) {
                throw ApprovalNotFoundException(shareId)
            }
            val createDescApprovals = approvals.sortedByDescending { it.createDate }
            createDescApprovals.firstOrNull { it.approved } ?: createDescApprovals.first()
        } else {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_EMPTY, "approvalId or shareId")
        }
    }

    fun getApprovalStatus(approvalId: String?, shareId: String?): Boolean {
        return getApproval(approvalId, shareId).approved
    }

    fun checkApprovalStatus(shareId: String): TUserShareApproval {
        val approval = getApproval(null, shareId)
        if (!approval.approved) {
            throw ErrorCodeException(CommonMessageCode.REQUEST_DENIED, shareId)
        }
        return approval
    }

    fun callback(approvalId: String, approveUserId: String) {
        userShareApprovalDao.approve(approvalId, approveUserId)
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
        } catch (e : Exception) {
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

    companion object {
        private val logger = LoggerFactory.getLogger(UserShareApprovalService::class.java)
    }
}
