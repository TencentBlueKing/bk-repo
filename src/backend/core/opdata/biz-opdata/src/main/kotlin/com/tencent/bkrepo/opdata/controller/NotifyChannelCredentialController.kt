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

package com.tencent.bkrepo.opdata.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.notify.api.NotifyChannelCredential
import com.tencent.bkrepo.common.notify.service.NotifyChannelCredentialService
import com.tencent.bkrepo.common.metadata.annotation.LogOperate
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/notify/channel/credentials")
@Principal(PrincipalType.ADMIN)
class NotifyChannelCredentialController(
    private val notifyChannelCredentialService: NotifyChannelCredentialService
) {
    @ApiOperation("创建通知渠道凭据")
    @PostMapping
    @LogOperate(type = "NOTIFY_CREATE", desensitize = true)
    fun create(@RequestBody credential: NotifyChannelCredential): Response<NotifyChannelCredential> {
        return ResponseBuilder.success(notifyChannelCredentialService.create(SecurityUtils.getUserId(), credential))
    }

    @ApiOperation("删除通知渠道凭据")
    @DeleteMapping("/{name}")
    @LogOperate(type = "NOTIFY_DELETE")
    fun delete(@PathVariable name: String): Response<Void> {
        notifyChannelCredentialService.delete(name)
        return ResponseBuilder.success()
    }

    @ApiOperation("更新通知渠道凭据")
    @PutMapping("/{name}")
    @LogOperate(type = "NOTIFY_UPDATE", desensitize = true)
    fun update(
        @PathVariable name: String,
        @RequestBody credential: NotifyChannelCredential
    ): Response<NotifyChannelCredential> {
        return ResponseBuilder.success(notifyChannelCredentialService.update(SecurityUtils.getUserId(), credential))
    }

    @ApiOperation("获取通知渠道凭据列表")
    @GetMapping
    @LogOperate(type = "NOTIFY_LIST")
    fun list(): Response<List<NotifyChannelCredential>> {
        return ResponseBuilder.success(notifyChannelCredentialService.list())
    }
}
