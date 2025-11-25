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

package com.tencent.bkrepo.opdata.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.opdata.model.TInternalFlow
import com.tencent.bkrepo.opdata.pojo.InternalFlowRequest
import com.tencent.bkrepo.opdata.pojo.NameWithTag
import com.tencent.bkrepo.opdata.pojo.enums.LevelType
import com.tencent.bkrepo.opdata.service.InternalFlowService
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

/**
 * 内部流转接口
 */
@Tag(name = "内部流转服务接口")
@RestController
@RequestMapping("/api/internal-flow")
@Principal(PrincipalType.ADMIN)
class InternalFlowController(
    private val internalFlowService: InternalFlowService
) {

    @Operation(summary = "根据级别查询去重后的名称列表及对应的tag")
    @GetMapping("/names/level/{level}")
    fun getDistinctNamesByLevel(@PathVariable level: LevelType): Response<List<NameWithTag>> {
        val names = internalFlowService.getDistinctNamesByLevel(level)
        return ResponseBuilder.success(names)
    }

    @Operation(summary = "根据名称查询关联节点")
    @GetMapping("/related/name/{name}")
    fun getRelatedFlowsByName(@PathVariable name: String): Response<List<TInternalFlow>> {
        val result = internalFlowService.getRelatedFlowsByName(name)
        return ResponseBuilder.success(result)
    }

    @Operation(summary = "创建内部流转配置")
    @PostMapping("/create")
    fun createInternalFlow(@RequestBody request: InternalFlowRequest): Response<TInternalFlow> {
        val flow = internalFlowService.createInternalFlow(request)
        return ResponseBuilder.success(flow)
    }


    @Operation(summary = "删除内部流转配置")
    @DeleteMapping("/{id}")
    fun deleteInternalFlow(@PathVariable id: String): Response<Void> {
        internalFlowService.deleteInternalFlow(id)
        return ResponseBuilder.success()
    }
}
