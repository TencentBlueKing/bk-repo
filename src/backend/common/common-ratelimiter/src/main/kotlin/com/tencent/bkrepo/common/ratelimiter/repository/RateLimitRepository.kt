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

package com.tencent.bkrepo.common.ratelimiter.repository

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.common.ratelimiter.model.TRateLimit
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

@Repository
class RateLimitRepository : SimpleMongoDao<TRateLimit>() {

    fun existsById(id: String) : Boolean {
        return find(Query(TRateLimit::id.isEqualTo(id))).isNotEmpty()
    }

    fun existsByResourceAndLimitDimension(resource: String, limitDimension: String) : Boolean {
        return exists(
            Query(
                Criteria.where(TRateLimit::resource.name).isEqualTo(resource)
                    .and(TRateLimit::limitDimension.name).isEqualTo(limitDimension)
            )
        )
    }

    fun findByResourceAndLimitDimension(resource: String, limitDimension: String) : List<TRateLimit> {
        return find(
            Query(
                Criteria.where(TRateLimit::resource.name).isEqualTo(resource)
                    .and(TRateLimit::limitDimension.name).isEqualTo(limitDimension)
            )
        )
    }

    fun findByModuleNameAndLimitDimension(moduleName: String, limitDimension: String): List<TRateLimit> {
        return find(
            Query(
                Criteria.where(TRateLimit::moduleName.name).regex("$moduleName")
                    .and(TRateLimit::limitDimension.name).isEqualTo(limitDimension)
            )
        )
    }

    fun findByModuleNameAndLimitDimensionAndResource(
        resource: String,
        moduleName: List<String>,
        limitDimension: String
    ): TRateLimit? {
        return findOne(
            Query(
                Criteria.where(TRateLimit::moduleName.name).isEqualTo("$moduleName")
                    .and(TRateLimit::limitDimension.name).isEqualTo(limitDimension)
                    .and(TRateLimit::resource.name).isEqualTo(resource)
            )
        )
    }
}
