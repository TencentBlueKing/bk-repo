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

package com.tencent.bkrepo.common.lock.dao

import com.tencent.bkrepo.common.lock.model.TMongoCas
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Repository

@Repository
class MongoCasDao : SimpleMongoDao<TMongoCas>() {

    fun findByKey(key: String): TMongoCas? {
        return this.findOne(Query(TMongoCas::key.isEqualTo(key)))
    }

    fun deleteByKey(key: String) {
        if (key.isNotBlank()) {
            this.remove(Query(TMongoCas::key.isEqualTo(key)))
        }
    }

    fun incrByKey(key: String, increase: Long): TMongoCas? {
        val criteria = where(TMongoCas::key).isEqualTo(key)
        val query = Query(criteria)
        val updateMax = Update().max(TMongoCas::value.name, 0L)
        val options = FindAndModifyOptions().apply { this.upsert(true).returnNew(true) }
        determineMongoTemplate()
            .findAndModify(query, updateMax, options, TMongoCas::class.java)
        val update = Update().inc(TMongoCas::value.name, increase)
        return determineMongoTemplate()
            .findAndModify(query, update, options, TMongoCas::class.java)
    }
}