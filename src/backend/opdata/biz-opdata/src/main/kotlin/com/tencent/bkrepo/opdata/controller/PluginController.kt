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

package com.tencent.bkrepo.opdata.controller

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.annotation.LogOperate
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.opdata.pojo.plugin.PluginCreateRequest
import com.tencent.bkrepo.opdata.pojo.plugin.PluginDetail
import com.tencent.bkrepo.opdata.pojo.plugin.PluginListOption
import com.tencent.bkrepo.opdata.pojo.plugin.PluginUpdateRequest
import com.tencent.bkrepo.opdata.service.PluginService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "插件服务接口")
@RestController
@RequestMapping("/api/plugin")
@Principal(PrincipalType.ADMIN)
class PluginController(
    private val pluginService: PluginService
) {

    @Operation(summary = "插件列表")
    @GetMapping
    @LogOperate(type = "PLUGIN_LIST")
    fun list(option: PluginListOption): Response<Page<PluginDetail>> {
        return ResponseBuilder.success(pluginService.list(option))
    }

    @Operation(summary = "创建插件")
    @PostMapping
    @LogOperate(type = "PLUGIN_CREATE")
    fun create(@RequestBody request: PluginCreateRequest): Response<Void> {
        pluginService.create(request)
        return ResponseBuilder.success()
    }

    @Operation(summary = "更新插件")
    @PutMapping
    @LogOperate(type = "PLUGIN_UPDATE")
    fun update(@RequestBody request: PluginUpdateRequest): Response<Void> {
        pluginService.update(request)
        return ResponseBuilder.success()
    }

    @Operation(summary = "删除插件")
    @DeleteMapping("/{pluginId}")
    @LogOperate(type = "PLUGIN_DELETE")
    fun delete(@PathVariable pluginId: String): Response<Void> {
        pluginService.delete(pluginId)
        return ResponseBuilder.success()
    }

    @Operation(summary = "加载插件")
    @PostMapping("/load/{pluginId}")
    @LogOperate(type = "PLUGIN_LOAD")
    fun load(
        @PathVariable pluginId: String,
        host: String
    ): Response<Void> {
        pluginService.load(pluginId, host)
        return ResponseBuilder.success()
    }

    @Operation(summary = "卸载插件")
    @DeleteMapping("/unload/{pluginId}")
    @LogOperate(type = "PLUGIN_UNLOAD")
    fun unload(
        @PathVariable pluginId: String,
        host: String
    ): Response<Void> {
        pluginService.unload(pluginId, host)
        return ResponseBuilder.success()
    }
}
