/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.ratelimiter.service.user

import com.tencent.bkrepo.common.ratelimiter.model.RateLimitCreatOrUpdateRequest
import com.tencent.bkrepo.common.ratelimiter.model.TRateLimit
import com.tencent.bkrepo.common.ratelimiter.repository.RateLimitRepository
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class RateLimiterConfigService(
    private val rateLimitRepository: RateLimitRepository
) {

    @Value("\${spring.cloud.client.ip-address}")
    var host: String = "127.0.0.1"

    fun list(): List<TRateLimit> {
        return rateLimitRepository.findAll()
    }

    fun create(fileCacheRequest: RateLimitCreatOrUpdateRequest) {
        with(fileCacheRequest) {
            rateLimitRepository.insert(
                TRateLimit(
                    id = null,
                    resource = resource,
                    limitDimension = limitDimension,
                    algo = algo,
                    limit = limit,
                    duration = Duration.ofSeconds(duration),
                    capacity = capacity,
                    scope = scope,
                    moduleName = moduleName
                )
            )
        }
    }

    fun checkExist(id: String): Boolean {
        return rateLimitRepository.existsById(id)
    }

    fun checkExist(fileCacheRequest: RateLimitCreatOrUpdateRequest): Boolean {
        with(fileCacheRequest) {
            return rateLimitRepository.existsByResourceAndLimitDimension(resource, limitDimension)
        }
    }

    fun delete(id: String) {
        rateLimitRepository.removeById(id)
    }

    fun getById(id: String): TRateLimit? {
        return rateLimitRepository.findById(id)
    }

    fun update(fileCacheRequest: RateLimitCreatOrUpdateRequest) {
        with(fileCacheRequest) {
            targets?.let {
                rateLimitRepository.save(
                    TRateLimit(
                        id = id,
                        resource = resource,
                        limitDimension = limitDimension,
                        algo = algo,
                        limit = limit,
                        duration = Duration.ofSeconds(duration),
                        capacity = capacity,
                        scope = scope,
                        moduleName = moduleName,
                        targets = it
                    )
                )
            } ?: run {
                rateLimitRepository.save(
                    TRateLimit(
                        id = id,
                        resource = resource,
                        limitDimension = limitDimension,
                        algo = algo,
                        limit = limit,
                        duration = Duration.ofSeconds(duration),
                        capacity = capacity,
                        scope = scope,
                        moduleName = moduleName
                    )
                )
            }
        }
    }

    fun findByModuleNameAndLimitDimension(moduleName: String, limitDimension: String): List<TRateLimit> {
        return rateLimitRepository.findByModuleNameAndLimitDimension(moduleName, limitDimension)
    }

    fun findByResourceAndLimitDimension(resource: String, limitDimension: String): List<TRateLimit> {
        return rateLimitRepository.findByResourceAndLimitDimension(resource, limitDimension)
    }

    fun findByModuleNameAndLimitDimensionAndResource(
        resource: String,
        moduleName: List<String>,
        limitDimension: String
    ): TRateLimit? {
        return rateLimitRepository.findByModuleNameAndLimitDimensionAndResource(resource, moduleName, limitDimension)
    }
}