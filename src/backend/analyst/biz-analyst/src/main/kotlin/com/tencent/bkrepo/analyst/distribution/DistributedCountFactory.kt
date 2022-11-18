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

package com.tencent.bkrepo.analyst.distribution

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class DistributedCountFactory(
    private val distributedCountDao: ObjectProvider<DistributedCountDao>,
    private val redisTemplate: ObjectProvider<RedisTemplate<String, String>>
) {
    fun create(key: String, type: String = DISTRIBUTED_COUNT_REDIS): DistributedCount {
        return when (type) {
            DISTRIBUTED_COUNT_MONGODB -> MongoDistributedCount(key, distributedCountDao.getObject())
            DISTRIBUTED_COUNT_REDIS -> RedisDistributedCount(key, redisTemplate.getObject())
            else -> throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, type)
        }
    }

    companion object {
        const val DISTRIBUTED_COUNT_MONGODB = "mongodb"
        const val DISTRIBUTED_COUNT_REDIS = "redis"
    }
}
