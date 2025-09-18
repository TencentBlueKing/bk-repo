/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.replication.api.federation

import com.tencent.bkrepo.common.api.constant.REPLICATION_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedClusterRemoveRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryConfigRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 联邦仓库配置同步
 */
@Tag(name = "联邦仓库配置同步")
@FeignClient(REPLICATION_SERVICE_NAME, contextId = "FederatedRepositoryClient")
@RequestMapping("/replica/federation")
interface FederatedRepositoryClient {
    @Operation(summary = "同步配置")
    @PostMapping("/config/sync")
    fun createFederatedConfig(@RequestBody request: FederatedRepositoryConfigRequest): Response<Boolean>


    @Operation(summary = "删除配置")
    @DeleteMapping("/config/delete/{projectId}/{repoName}/{key}")
    fun deleteConfig(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable key: String,
    ): Response<Void>

    @Operation(summary = "删除配置")
    @DeleteMapping("/config/cluster/delete")
    fun removeClusterFromFederation(@RequestBody request: FederatedClusterRemoveRequest): Response<Void>


}
