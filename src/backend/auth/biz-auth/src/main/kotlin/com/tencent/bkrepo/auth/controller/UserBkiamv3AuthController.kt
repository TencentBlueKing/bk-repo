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

package com.tencent.bkrepo.auth.controller

import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.auth.service.bkiamv3.BkIamV3Service
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/user/auth")
class UserBkiamv3AuthController {

    @Autowired
    private var bkIamV3Service: BkIamV3Service? = null

    @ApiOperation("生成无权限申请url")
    @PostMapping("/bkiamv3/permission/url")
    fun queryProject(
        @RequestBody request: CheckPermissionRequest
    ): Response<String?> {
        val result = bkIamV3Service?.let {
            try {
                bkIamV3Service!!.getPermissionUrl(request) ?: StringPool.EMPTY
            } catch (e: Exception) {
                throw  ErrorCodeException(CommonMessageCode.PARAMETER_INVALID)
            }
        }
        return ResponseBuilder.success(result)
    }

    @ApiOperation("判断蓝鲸权限是否开启")
    @GetMapping("/bkiamv3/status")
    fun bkiamv3Status(): Response<Boolean> {
        val result = bkIamV3Service?.let {
            bkIamV3Service!!.checkIamConfiguration()
        } ?: false
        return ResponseBuilder.success(result)
    }

    @ApiOperation("在权限中心生成对应项目以及其下所有仓库的管理权限")
    @PostMapping("/bkiamv3/project/refresh/{projectId}")
    @Principal(PrincipalType.ADMIN)
    fun refreshProject(
        @PathVariable projectId: String,
        @RequestParam userId: String? = null
    ): Response<Boolean> {
        val result = bkIamV3Service?.let {
            bkIamV3Service!!.refreshProjectManager(userId, projectId)
        } ?: false
        return ResponseBuilder.success(result)
    }

    @ApiOperation("删除生成的管理员空间")
    @DeleteMapping("/bkiamv3/manager/delete")
    @Principal(PrincipalType.ADMIN)
    fun deleteProjectManager(
        @RequestParam projectId: String,
        @RequestParam repoName: String? = null,
    ): Response<Boolean> {
        val result = bkIamV3Service?.let {
            bkIamV3Service!!.deleteGradeManager(projectId, repoName)
        } ?: false
        return ResponseBuilder.success(result)
    }
}
