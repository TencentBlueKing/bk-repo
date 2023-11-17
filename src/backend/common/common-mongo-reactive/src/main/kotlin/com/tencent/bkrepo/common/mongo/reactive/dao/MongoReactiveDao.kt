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

package com.tencent.bkrepo.common.mongo.reactive.dao

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update

/**
 * mongo db reactive 数据访问层接口
 */
interface MongoReactiveDao<E> {

    /**
     * 通过查询对象查询单条文档，返回元素类型由clazz指定
     */
    suspend fun <T> findOne(query: Query, clazz: Class<T>): T?

    /**
     * 通过查询对象查询文档集合，返回元素类型由clazz指定
     */
    suspend fun <T> find(query: Query, clazz: Class<T>): List<T>

    /**
     * 新增文档到数据库的集合中
     */
    suspend fun save(entity: E): E

    /**
     * 更新文档
     */
    suspend fun updateMulti(query: Query, update: Update): UpdateResult

    /**
     * 删除文档
     */
    suspend fun remove(query: Query): DeleteResult

    /**
     * update or insert
     */
    suspend fun upsert(query: Query, update: Update): UpdateResult

    /**
     * count
     */
    suspend fun count(query: Query): Long
}
