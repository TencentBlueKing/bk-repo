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

package com.tencent.bkrepo.replication.replica.base.interceptor.progress

import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.replication.pojo.blob.RequestTag
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.replica.base.process.ProgressListener
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class ProgressInterceptor : Interceptor {

    private val listener by lazy { SpringContextUtils.getBean<ProgressListener>() }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val tag = request.tag(RequestTag::class.java)
        if (tag != null) {
            val task = tag.task
            val key = tag.key
            listener.onStart(task, key,request.body!!.contentLength() - tag.size)
            try {
                val response = chain.proceed(wrapRequest(request, task, key))
                if (response.isSuccessful) {
                    listener.onSuccess(task)
                } else {
                    listener.onFailed(task, key)
                }
                return response
            } catch (e: IOException) {
                listener.onFailed(task, key)
                throw e
            }
        }

        return chain.proceed(request)
    }

    private fun wrapRequest(request: Request, task: ReplicaTaskInfo, key: String): Request {
        return request.newBuilder()
            .method(request.method, ProgressRequestBody(request.body!!, listener, task, key))
            .build()
    }
}
