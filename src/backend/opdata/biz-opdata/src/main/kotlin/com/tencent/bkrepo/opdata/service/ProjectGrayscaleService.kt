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

package com.tencent.bkrepo.opdata.service

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
class ProjectGrayscaleService(
    private val redisTemplate: RedisTemplate<String, String>,
) {

    fun list():  MutableList<MutableMap<String, String>>{
        val keys = redisTemplate.opsForHash<String, String>().keys(KEY_NAME)
        val target: MutableList<MutableMap<String, String>> = mutableListOf()
        if(keys.isNotEmpty()){
            keys.forEach {
                val temp = mutableMapOf<String, String>()
                val value = redisTemplate.opsForHash<String, String>().get(KEY_NAME, it)!!
                temp[it] = value
                target.add(temp)
            }
        }
        return target
    }

    fun addOrUpdate(projectId: String, environment: String){
        redisTemplate.opsForHash<String, String>().put(KEY_NAME, projectId, environment)
    }

    fun delete(projectId: String) {
        redisTemplate.opsForHash<String, String>().delete(KEY_NAME, projectId)
    }

    fun check(projectId: String): Boolean{
        return redisTemplate.opsForHash<String, String>().hasKey(KEY_NAME, projectId)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectGrayscaleService::class.java)
        // HASH 表名
        private const val KEY_NAME = "project:project_router"
    }
}