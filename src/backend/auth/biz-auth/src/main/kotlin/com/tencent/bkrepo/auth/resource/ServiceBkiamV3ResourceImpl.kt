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

package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.api.ServiceBkiamV3Resource
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_TYPE_NAME
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_TYPE_VALUE_BKIAMV3
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_TYPE_VALUE_DEVOPS
import com.tencent.bkrepo.auth.service.bkiamv3.BkIamV3Service
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceBkiamV3ResourceImpl : ServiceBkiamV3Resource {

    private var bkIamV3Service: BkIamV3Service? = null

    @Value("\${$AUTH_CONFIG_PREFIX.$AUTH_CONFIG_TYPE_NAME:}")
    private var authType: String = ""

    override fun createProjectManage(userId: String, projectId: String): Response<String?> {
        initService()
        bkIamV3Service?.let {
            return ResponseBuilder.success(bkIamV3Service!!.createGradeManager(userId, projectId))
        } ?: return ResponseBuilder.success()
    }

    override fun createRepoManage(userId: String, projectId: String, repoName: String): Response<String?> {
        initService()
        bkIamV3Service?.let {
            return ResponseBuilder.success(bkIamV3Service!!.createGradeManager(userId, projectId, repoName))
        } ?: return ResponseBuilder.success()
    }

    override fun deleteRepoManageGroup(userId: String, projectId: String, repoName: String): Response<Boolean> {
        initService()
        bkIamV3Service?.let {
            return ResponseBuilder.success(bkIamV3Service!!.deleteRepoGradeManager(userId, projectId, repoName))
        } ?: return ResponseBuilder.success()
    }

    private fun initService() {
        if (
            (authType == AUTH_CONFIG_TYPE_VALUE_BKIAMV3 || authType == AUTH_CONFIG_TYPE_VALUE_DEVOPS)
            && bkIamV3Service == null
        ) {
            bkIamV3Service = SpringContextUtils.getBean(BkIamV3Service::class.java)
        }
    }
}
