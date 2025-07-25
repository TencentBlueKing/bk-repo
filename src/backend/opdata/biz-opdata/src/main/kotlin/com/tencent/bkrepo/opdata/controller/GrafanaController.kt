/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.opdata.controller

import com.google.common.net.HttpHeaders.CONTENT_TYPE
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.opdata.pojo.QueryRequest
import com.tencent.bkrepo.opdata.pojo.SearchRequest
import com.tencent.bkrepo.opdata.service.GrafanaService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/grafana")
@Principal(PrincipalType.GENERAL)
class GrafanaController @Autowired constructor(
    private val grafanaService: GrafanaService
) {
    @GetMapping("")
    fun ping(): ResponseEntity<Any> {
        return ResponseEntity.ok().header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body("{}")
    }

    @PostMapping("/search")
    fun search(@RequestBody request: SearchRequest): ResponseEntity<Any> {
        val result = grafanaService.search(request)
        val response = JsonUtils.objectMapper.writeValueAsString(result)
        return ResponseEntity.ok().header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(response)
    }

    @PostMapping("/query")
    fun query(@RequestBody request: QueryRequest): ResponseEntity<Any> {
        val result = grafanaService.query(request)
        val response = JsonUtils.objectMapper.writeValueAsString(result)
        return ResponseEntity.ok().header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(response)
    }

    @PostMapping("/annotations")
    fun annotations(): ResponseEntity<Any> {
        return ResponseEntity.ok().header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body("{}")
    }
}
