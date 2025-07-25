/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.opdata.service.ProjectGrayscaleService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody


@RestController
@RequestMapping("/api/project/grayscale")
@Principal(PrincipalType.ADMIN)
class ProjectGrayscaleController(
    private val projectGrayscaleService: ProjectGrayscaleService
){

    /**
     * 获取灰度项目的所有配置
     */
    @GetMapping("/list")
    fun getAllProjectGrayscale(): Response<MutableList<MutableMap<String, String>>> {
        return ResponseBuilder.success(projectGrayscaleService.list())
    }


    @DeleteMapping("/delete/{id}")
    fun deleteProjectGrayscale(@PathVariable("id") id: String): Response<Void> {
        projectGrayscaleService.delete(id)
        return ResponseBuilder.success()
    }

    @PostMapping("/create")
    fun createProjectGrayscale(
        @RequestBody request: CreateOrUpdateProjectGrayscaleRequest
    ): Response<Void> {
        with(request) {
            if (projectGrayscaleService.check(projectId)) {
                throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, projectId)
            }
            projectGrayscaleService.addOrUpdate(projectId, environment)
            return ResponseBuilder.success()
        }
    }

    @PutMapping("/update")
    fun updateProjectGrayscale(
        @RequestBody request: CreateOrUpdateProjectGrayscaleRequest
    ): Response<Void> {
        with(request) {
            if (!projectGrayscaleService.check(projectId)) {
                throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, projectId)
            }
            projectGrayscaleService.addOrUpdate(projectId, environment)
            return ResponseBuilder.success()
        }
    }
}

data class CreateOrUpdateProjectGrayscaleRequest(
    val projectId: String,
    val environment: String
)