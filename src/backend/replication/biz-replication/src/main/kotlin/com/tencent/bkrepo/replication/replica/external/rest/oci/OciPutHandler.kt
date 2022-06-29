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

package com.tencent.bkrepo.replication.replica.external.rest.oci

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.replication.constant.METHOD
import com.tencent.bkrepo.replication.constant.PUT_METHOD
import com.tencent.bkrepo.replication.replica.external.exception.RepoDeployException
import com.tencent.bkrepo.replication.replica.external.rest.base.Handler
import okhttp3.OkHttpClient
import okhttp3.Response
import org.slf4j.LoggerFactory

/**
 * put 请求处理类
 */
class OciPutHandler(
    httpClient: OkHttpClient
) : Handler(httpClient) {
    /**
     * 针对特殊code做判断
     */
    override fun isFailure(response: Response): Boolean {
        if (BAD_RESPONSE_CODE.contains(response.code())) {
            throw RepoDeployException("Response error: code is ${response.code()}")
        }
        return true
    }
    /**
     * 设置请求相关属性
     */
    override fun setRequestProperty(map: Map<String, Any?>) {
        this.map.putAll(map)
        this.map[METHOD] = PUT_METHOD
    }
    companion object {
        private val logger = LoggerFactory.getLogger(OciPutHandler::class.java)
        private val BAD_RESPONSE_CODE = mutableListOf(
            HttpStatus.BAD_REQUEST.value,
            HttpStatus.METHOD_NOT_ALLOWED.value,
            HttpStatus.NOT_FOUND.value,
            HttpStatus.FORBIDDEN.value,
        )
    }
}
