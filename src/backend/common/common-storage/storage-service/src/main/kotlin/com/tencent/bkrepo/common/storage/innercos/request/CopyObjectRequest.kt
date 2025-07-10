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

package com.tencent.bkrepo.common.storage.innercos.request

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.innercos.COS_COPY_SOURCE
import com.tencent.bkrepo.common.storage.innercos.PATH_DELIMITER
import com.tencent.bkrepo.common.storage.innercos.client.ClientConfig
import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod
import com.tencent.bkrepo.common.storage.innercos.http.toRequestBody
import okhttp3.RequestBody

data class CopyObjectRequest(
    val sourceBucket: String,
    val sourceKey: String,
    val destinationKey: String
) : CosRequest(HttpMethod.PUT, destinationKey) {

    override fun buildRequestBody(): RequestBody {
        return StringPool.EMPTY.toRequestBody()
    }

    override fun sign(credentials: InnerCosCredentials, config: ClientConfig): String {
        val sourceEndpoint = config.endpointBuilder.buildEndpoint(credentials.region, sourceBucket)
        val sourceUri = StringBuilder().append(PATH_DELIMITER).append(sourceKey.trim(PATH_DELIMITER)).toString()
        headers[COS_COPY_SOURCE] = sourceEndpoint + sourceUri
        return super.sign(credentials, config)
    }
}
