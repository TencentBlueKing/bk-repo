/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.job.controller.user

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.job.config.properties.ExpiredCacheFileCleanupJobProperties
import com.tencent.bkrepo.job.pojo.FileCacheCheckRequest
import com.tencent.bkrepo.job.pojo.FileCacheRequest
import com.tencent.bkrepo.job.pojo.TFileCache
import com.tencent.bkrepo.job.service.FileCacheService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/cache")
@Principal(PrincipalType.ADMIN)
class FileCacheController(
    val fileCacheService: FileCacheService,
    val properties: ExpiredCacheFileCleanupJobProperties,
) {

    @GetMapping("/list")
    fun list(): Response<List<TFileCache>> {
        return ResponseBuilder.success(fileCacheService.list())
    }

    @PostMapping("/update")
    fun update(@RequestBody request:FileCacheRequest):Response<Void> {
        request.id?.let {
            var checkStatus = updateCheck(request)
            if (checkStatus.status) {
                return ResponseBuilder.success()
            } else {
                return ResponseBuilder.fail(HttpStatus.BAD_REQUEST.value, checkStatus.msg)
            }
        } ?: let {
            return ResponseBuilder.fail(HttpStatus.BAD_REQUEST.value, "id is null")
        }
    }

    fun updateCheck(request: FileCacheRequest):CheckStatus {
        fileCacheService.getById(request.id!!)?.let {
            var fileCacheCheckRequest = FileCacheCheckRequest(
                projectId = request.projectId,
                repoName = request.repoName,
                days = request.days,
                size = request.size
            )
            fileCacheService.checkExist(fileCacheCheckRequest)?.let {
                if( it.id != request.id) {
                    var checkStatus = CheckStatus(
                        status = false,
                        msg = "has same config"
                    )
                    return checkStatus
                }
            }
            fileCacheService.update(request)
            var checkStatus = CheckStatus(
                status = true,
                msg = ""
            )
            return checkStatus
        }
        var checkStatus = CheckStatus(
            status = false,
            msg = "id not existed"
        )
        return checkStatus
    }

    // 新增
    @PostMapping("/create")
    fun create(@RequestBody request:FileCacheRequest):Response<Void> {
        var fileCacheCheckRequest = FileCacheCheckRequest(
            repoName = request.repoName,
            projectId = request.projectId,
            days = request.days,
            size = request.size
        )
        fileCacheService.checkExist(fileCacheCheckRequest)?.let {
            return ResponseBuilder.fail(HttpStatus.BAD_REQUEST.value, "has same config")
        }
        fileCacheService.create(request)
        return ResponseBuilder.success()
    }

    // 删除
    @DeleteMapping("/delete/{id}")
    fun delete(@PathVariable id:String): Response<Void> {
        fileCacheService.getById(id)?.let {
            fileCacheService.delete(id)
            return ResponseBuilder.success()
        }
        return ResponseBuilder.fail(HttpStatus.BAD_REQUEST.value, "id not existed")
    }

    // 获取配置中的属性
    @GetMapping("/config")
    fun getConfig(): Response<String> {
        var config = properties
        return ResponseBuilder.success(config.toJsonString())
    }

}

data class CheckStatus(
    val status: Boolean,
    val msg: String
)