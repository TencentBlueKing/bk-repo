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

package com.tencent.bkrepo.common.ratelimiter.constant

const val TRY_LOCK_TIMEOUT = 10L
const val KEY_PREFIX = "rateLimiter:"
const val SLEEP_TIME = 10

const val TAG_STATUS = "status"
const val TAG_NAME = "name"

const val RATE_LIMITER_TOTAL_COUNT = "rate.limiter.total.count"
const val RATE_LIMITER_TOTAL_COUNT_DESC = "总请求数"

const val RATE_LIMITER_PASSED_COUNT = "rate.limiter.passed.count"
const val RATE_LIMITER_PASSED_COUNT_DESC = "通过请求数"

const val RATE_LIMITER_LIMITED_COUNT = "rate.limiter.limited.count"
const val RATE_LIMITER_LIMITED_COUNT_DESC = "限流请求数"

const val RATE_LIMITER_EXCEPTION_COUNT = "rate.limiter.exception.count"
const val RATE_LIMITER_EXCEPTION_COUNT_DESC = "异常请求数"

const val RATE_LIMITER_CHECK_TIME = "rate.limiter.check.time"
const val RATE_LIMITER_CHECK_TIME_DESC = "限流校验耗时"