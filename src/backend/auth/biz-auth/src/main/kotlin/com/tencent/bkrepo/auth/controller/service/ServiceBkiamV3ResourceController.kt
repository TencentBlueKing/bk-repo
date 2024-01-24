/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.auth.controller.service

import com.tencent.bkrepo.auth.api.ServiceBkiamV3ResourceClient
import com.tencent.bkrepo.auth.service.bkiamv3.BkIamV3Service
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.lock.service.LockOperation
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceBkiamV3ResourceController : ServiceBkiamV3ResourceClient {
    @Autowired
    private var bkIamV3Service: BkIamV3Service? = null

    @Autowired
    lateinit var lockOperation: LockOperation

    override fun createProjectManage(userId: String, projectId: String): Response<String?> {
        bkIamV3Service?.let {
            val gradeId = lockAction(projectId) {
                bkIamV3Service!!.createGradeManager(userId, projectId)
            }
            return ResponseBuilder.success(gradeId)
        } ?: return ResponseBuilder.success()
    }

    override fun createRepoManage(userId: String, projectId: String, repoName: String): Response<String?> {
        bkIamV3Service?.let {
            val repoGradeId = lockAction(projectId) {
            bkIamV3Service!!.createGradeManager(userId, projectId, repoName)
            }
            return ResponseBuilder.success(repoGradeId)
        } ?: return ResponseBuilder.success()
    }

    override fun deleteRepoManageGroup(userId: String, projectId: String, repoName: String): Response<Boolean> {
        bkIamV3Service?.let {
            return ResponseBuilder.success(bkIamV3Service!!.deleteGradeManager(projectId, repoName))
        } ?: return ResponseBuilder.success(true)
    }

    override fun getExistRbacDefaultGroupProjectIds(projectIds: List<String>): Response<Map<String, Boolean>> {
        bkIamV3Service?.let {
            return ResponseBuilder.success(bkIamV3Service!!.getExistRbacDefaultGroupProjectIds(projectIds))
        } ?: return ResponseBuilder.success(emptyMap())
    }

    /**
     * 针对自旋达到次数后，还没有获取到锁的情况默认也会执行所传入的方法,确保业务流程不中断
     */
    private fun <T> lockAction(projectId: String, action: () -> T): T {
        val lockKey = "$AUTH_LOCK_KEY_PREFIX$projectId"
        val lock = lockOperation.getLock(lockKey)
        return if (lockOperation.getSpinLock(lockKey, lock)) {
            try {
                action()
            } finally {
                lockOperation.close(lockKey, lock)
            }
        } else {
            action()
        }
    }

    companion object {
        const val AUTH_LOCK_KEY_PREFIX = "auth:lock:gradeCreate:"
    }
}
