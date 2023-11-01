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

package com.tencent.bkrepo.oci.api

import com.tencent.bkrepo.common.api.constant.OCI_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.oci.pojo.third.OciReplicationRecordInfo
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam


@Api("oci")
@Primary
@FeignClient(OCI_SERVICE_NAME, contextId = "OciClient")
@RequestMapping("/service/third")
interface OciClient {

    @ApiOperation("更新第三方同步时，先传manifest文件，再传其他文件")
    @PostMapping("/packageCreate")
    fun packageCreate(
        @RequestBody record: OciReplicationRecordInfo
    ): Response<Void>

    @ApiOperation("定时从第三方仓库拉取对应的package信息")
    @PostMapping("/pull/package/{projectId}/{repoName}")
    fun getPackagesFromThirdPartyRepo(
        @PathVariable projectId: String,
        @PathVariable repoName: String
    ): Response<Void>

    @ApiOperation("刷新对应版本镜像的blob节点路径")
    @PostMapping("/blob/path/refresh/{projectId}/{repoName}")
    fun blobPathRefresh(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam packageName: String,
        @RequestParam version: String,
    ): Response<Boolean>

    @ApiOperation("当历史数据刷新完成后，删除blobs路径下的公共blob节点")
    @DeleteMapping("/blobs/delete/{projectId}/{repoName}")
    fun deleteBlobsFolderAfterRefreshed(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam packageName: String
    ): Response<Void>

    @ApiOperation("删除仓库下的包版本")
    @DeleteMapping("version/delete/{projectId}/{repoName}")
    fun deleteVersion(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam packageName: String,
        @RequestParam version: String
    ): Response<Void>
}
