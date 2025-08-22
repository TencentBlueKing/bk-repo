/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.webhook.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.annotation.LogOperate
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.webhook.constant.AssociationType
import com.tencent.bkrepo.webhook.pojo.CreateWebHookRequest
import com.tencent.bkrepo.webhook.pojo.UpdateWebHookRequest
import com.tencent.bkrepo.webhook.pojo.WebHook
import com.tencent.bkrepo.webhook.pojo.WebHookLog
import com.tencent.bkrepo.webhook.service.WebHookService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "WebHook管理接口")
@RestController
@RequestMapping("/api/webhook")
class UserWebHookController(
    private val webHookService: WebHookService
) {

    @Operation(summary = "创建WebHook")
    @PostMapping("/create")
    @LogOperate(type = "WEBHOOK_CREATE", desensitize = true)
    fun createWebHook(
        @RequestAttribute userId: String,
        @RequestBody request: CreateWebHookRequest
    ): Response<Void> {
        webHookService.createWebHook(userId, request)
        return ResponseBuilder.success()
    }

    @Operation(summary = "更新WebHook")
    @PutMapping("/update")
    @LogOperate(type = "WEBHOOK_UPDATE", desensitize = true)
    fun updateWebHook(
        @RequestAttribute userId: String,
        @RequestBody request: UpdateWebHookRequest
    ): Response<Void> {
        webHookService.updateWebHook(userId, request)
        return ResponseBuilder.success()
    }

    @Operation(summary = "删除WebHook")
    @DeleteMapping("/delete/{id}")
    @LogOperate(type = "WEBHOOK_DELETE")
    fun deleteWebHook(
        @RequestAttribute userId: String,
        @PathVariable id: String
    ): Response<Void> {
        webHookService.deleteWebHook(userId, id)
        return ResponseBuilder.success()
    }

    @Operation(summary = "查询WebHook")
    @GetMapping("/{id}")
    fun getWebHook(
        @RequestAttribute userId: String,
        @PathVariable id: String
    ): Response<WebHook> {
        return ResponseBuilder.success(webHookService.getWebHook(userId, id))
    }

    @Operation(summary = "查询WebHook列表")
    @GetMapping("/list")
    @LogOperate(type = "WEBHOOK_LIST")
    fun listWebHook(
        @RequestAttribute userId: String,
        @RequestParam associationType: AssociationType,
        @RequestParam associationId: String?
    ): Response<List<WebHook>> {
        return ResponseBuilder.success(webHookService.listWebHook(userId, associationType, associationId))
    }

    @Operation(summary = "测试WebHook")
    @PostMapping("/test/{id}")
    fun testWebHook(
        @RequestAttribute userId: String,
        @PathVariable id: String
    ): Response<WebHookLog> {
        return ResponseBuilder.success(webHookService.testWebHook(userId, id))
    }

    @Operation(summary = "重试WebHook请求")
    @PostMapping("/retry/{logId}")
    fun retryWebHookRequest(
        @PathVariable logId: String
    ): Response<WebHookLog> {
        return ResponseBuilder.success(webHookService.retryWebHookRequest(logId))
    }
}
