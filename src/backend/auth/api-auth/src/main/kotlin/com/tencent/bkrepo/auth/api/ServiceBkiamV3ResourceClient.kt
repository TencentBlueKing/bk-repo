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

package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.AUTH_SERVICE_BKIAMV3_PREFIX
import com.tencent.bkrepo.common.api.constant.AUTH_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Api(tags = ["SERVICE_BKIAMV3"], description = "蓝鲸权限v3接口")
@Primary
@FeignClient(AUTH_SERVICE_NAME, contextId = "ServiceBkiamV3Resource")
@RequestMapping(AUTH_SERVICE_BKIAMV3_PREFIX)
interface ServiceBkiamV3ResourceClient {
    @ApiOperation("创建项目管理员")
    @PostMapping("/create/project/manage/{projectId}")
    fun createProjectManage(
        @ApiParam(value = "用户id")
        @RequestParam userId: String,
        @ApiParam(value = "项目名称")
        @PathVariable projectId: String
    ): Response<String?>

    @ApiOperation("创建2级仓库管理员")
    @PostMapping("/create/repo/manage/{projectId}/{repoName}")
    fun createRepoManage(
        @ApiParam(value = "用户id")
        @RequestParam userId: String,
        @ApiParam(value = "项目名称")
        @PathVariable projectId: String,
        @ApiParam(value = "仓库名称")
        @PathVariable repoName: String
    ): Response<String?>

    @ApiOperation("删除仓库相关用户组")
    @DeleteMapping("/delete/repo/manage/{projectId}/{repoName}")
    fun deleteRepoManageGroup(
        @ApiParam(value = "用户id")
        @RequestParam userId: String,
        @ApiParam(value = "项目名称")
        @PathVariable projectId: String,
        @ApiParam(value = "仓库名称")
        @PathVariable repoName: String
    ): Response<Boolean>


    @ApiOperation("检查项目默认的rbac项目组是否已经存在")
    @PostMapping("/rbac/group/check")
    fun getExistRbacDefaultGroupProjectIds(
        @ApiParam(value = "项目ID列表")
        @RequestBody projectIdList: List<String> = emptyList()
    ): Response<Map<String, Boolean>>

}
