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

package com.tencent.bkrepo.analyst.controller.user

import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.analyst.pojo.response.ScannerBase
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.common.metadata.annotation.LogOperate
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Api("扫描器配置接口")
@RestController
@RequestMapping("/api/scanners")
class UserScannerController @Autowired constructor(
    private val scannerService: ScannerService
) {

    @ApiOperation("创建扫描器接口")
    @PostMapping
    @Principal(PrincipalType.ADMIN)
    @LogOperate(type = "SCANNER_CREATE", desensitize = true)
    fun create(
        @RequestBody scanner: Scanner
    ): Response<Scanner> {
        return ResponseBuilder.success(scannerService.create(scanner))
    }

    @ApiOperation("获取扫描器列表")
    @GetMapping
    @Principal(PrincipalType.ADMIN)
    @LogOperate(type = "SCANNER_LIST")
    fun list(): Response<List<Scanner>> {
        return ResponseBuilder.success(scannerService.list())
    }

    @ApiOperation("获取扫描器基本信息列表")
    @GetMapping("/base")
    fun listBaseInf(
        @RequestParam(required = false) packageType: String? = null,
        @RequestParam(required = false) scanType: String? = null
    ): Response<List<ScannerBase>> {
        val scannerBaseList = scannerService.find(packageType, scanType)
        return ResponseBuilder.success(scannerBaseList.map {
            ScannerBase(
                it.name, it.type, it.description, it.supportFileNameExt, it.supportPackageTypes, it.supportScanTypes
            )
        })
    }

    @ApiOperation("获取支持扫描的文件名后缀")
    @GetMapping("/support/ext")
    fun supportFileNameExt(): Response<Set<String>> {
        return ResponseBuilder.success(scannerService.supportFileNameExt())
    }

    @ApiOperation("获取支持扫描的包类型")
    @GetMapping("/support/package")
    fun supportPackageType(): Response<Set<String>> {
        return ResponseBuilder.success(scannerService.supportPackageType())
    }

    @ApiOperation("获取扫描器")
    @GetMapping("/{name}")
    @Principal(PrincipalType.ADMIN)
    @LogOperate(type = "SCANNER_GET")
    fun get(@PathVariable("name") name: String): Response<Scanner> {
        return ResponseBuilder.success(scannerService.get(name))
    }

    @ApiOperation("删除扫描器")
    @DeleteMapping("/{name}")
    @Principal(PrincipalType.ADMIN)
    @LogOperate(type = "SCANNER_DELETE")
    fun delete(@PathVariable("name") name: String): Response<Void> {
        scannerService.delete(name)
        return ResponseBuilder.success()
    }

    @ApiOperation("更新扫描器")
    @PutMapping("/{name}")
    @Principal(PrincipalType.ADMIN)
    @LogOperate(type = "SCANNER_UPDATE", desensitize = true)
    fun update(
        @PathVariable("name") name: String,
        @RequestBody scanner: Scanner
    ): Response<Scanner> {
        if (name != scanner.name) {
            throw BadRequestException(CommonMessageCode.PARAMETER_INVALID)
        }
        return ResponseBuilder.success(scannerService.update(scanner))
    }
}
