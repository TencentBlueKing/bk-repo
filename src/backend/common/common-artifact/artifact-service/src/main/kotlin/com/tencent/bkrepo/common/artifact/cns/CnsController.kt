/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.cns

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Principal(type = PrincipalType.ADMIN)
@RestController
@RequestMapping("/service/cns")
class CnsController(
    private val cnsService: CnsService
) {
    @GetMapping("/exist")
    fun exist(
        @RequestParam(required = false) key: String?,
        @RequestParam sha256: String
    ): Response<Boolean> {
        return ResponseBuilder.success(cnsService.exist(key, sha256))
    }

    @GetMapping("/check")
    fun check(
        @RequestParam(required = false) key: String?,
        @RequestParam sha256: String,
        @RequestParam(required = false) repoType: String? = null
    ): Response<Boolean> {
        val repositoryType = repoType?.let { RepositoryType.ofValueOrDefault(repoType) }
        return ResponseBuilder.success(cnsService.check(key, sha256, repositoryType))
    }
}
