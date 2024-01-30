/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.api.proxy

import com.tencent.bkrepo.common.api.constant.REPLICATION_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.replication.constant.BLOB_CHECK_URI
import com.tencent.bkrepo.replication.constant.BLOB_PULL_URI
import com.tencent.bkrepo.replication.constant.FeignResponse
import com.tencent.bkrepo.replication.pojo.blob.BlobPullRequest
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * Proxy与服务节点之间的blob数据同步接口
 *
 * 注意，不使用feign实现文件推送是因为目前feign对文件上传的实现是基于字节数组，
 * 当推送大文件或者许多文件推送请求时，容易造成内存不足。
 */
@FeignClient(REPLICATION_SERVICE_NAME, contextId = "ProxyBlobReplicaClient")
@RequestMapping("/proxy")
interface ProxyBlobReplicaClient {

    /**
     * 从远程集群拉取文件数据
     * @param request 拉取请求
     */
    @PostMapping(BLOB_PULL_URI)
    fun pull(@RequestBody request: BlobPullRequest): FeignResponse

    /**
     * 检查文件数据在远程集群是否存在
     * @param sha256 文件sha256，用于校验
     * @param storageKey 存储实例key，为空表示远程集群默认存储
     */
    @GetMapping(BLOB_CHECK_URI)
    fun check(
        @RequestParam sha256: String,
        @RequestParam storageKey: String? = null,
        @RequestParam(required = false) repoType: String? = null
    ): Response<Boolean>
}
