/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 *  A copy of the MIT License is included in this file.
 *
 *
 *  Terms of the MIT License:
 *  ---------------------------------------------------
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 *  LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 *  NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 *  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.security.manager

import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import org.slf4j.LoggerFactory

class PrincipalManager(
    private val serviceUserClient: ServiceUserClient
) {

    fun checkPrincipal(userId: String, principalType: PrincipalType) {
        val platformId = SecurityUtils.getPlatformId()
        checkAnonymous(userId, platformId)

        if (principalType == PrincipalType.ADMIN) {
            if (!isAdminUser(userId)) {
                throw PermissionException()
            }
        } else if (principalType == PrincipalType.PLATFORM) {
            if (userId.isEmpty()) {
                logger.warn("platform auth with empty userId[$platformId,$userId]")
            }
            if (platformId == null && !isAdminUser(userId)) {
                throw PermissionException()
            }
        } else if (principalType == PrincipalType.GENERAL) {
            if (userId.isEmpty() || userId == ANONYMOUS_USER) {
                throw PermissionException()
            }
        }
    }

    /**
     * 检查是否为匿名用户，如果是匿名用户则返回401并提示登录
     */
    fun checkAnonymous(userId: String, platformId: String?) {
        if (userId == ANONYMOUS_USER && platformId == null) {
            throw AuthenticationException()
        }
    }

    fun isAdminUser(userId: String): Boolean {
        return serviceUserClient.userInfoById(userId).data?.admin == true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PrincipalManager::class.java)
    }
}
