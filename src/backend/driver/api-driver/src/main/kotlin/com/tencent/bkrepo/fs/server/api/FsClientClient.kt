/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.fs.server.api

import com.tencent.bkrepo.common.api.constant.FS_SERVER_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.fs.server.pojo.ClientDetail
import com.tencent.bkrepo.fs.server.pojo.DailyClientDetail
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(FS_SERVER_SERVICE_NAME, url = "\${fs-service-url:}", contextId = "ClientClient", primary = false)
@RequestMapping("/service/client")
interface FsClientClient {

    @GetMapping("/list")
    fun listClients(
        @RequestParam projectId: String?,
        @RequestParam repoName: String?,
        @RequestParam pageNumber: Int?,
        @RequestParam pageSize: Int?,
        @RequestParam online: Boolean?,
        @RequestParam ip: String?,
        @RequestParam userId: String?,
        @RequestParam version: String?
    ): Response<Page<ClientDetail>>

    @GetMapping("/daily/list")
    fun listDailyClients(
        @RequestParam projectId: String?,
        @RequestParam repoName: String?,
        @RequestParam pageNumber: Int?,
        @RequestParam pageSize: Int?,
        @RequestParam ip: String?,
        @RequestParam version: String?,
        @RequestParam startTime: String?,
        @RequestParam endTime: String?,
        @RequestParam mountPoint: String?,
        @RequestParam actions: String
    ): Response<Page<DailyClientDetail>>
}
