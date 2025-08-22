/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.conan.listener

import com.tencent.bkrepo.common.api.util.AsyncUtils.trace
import com.tencent.bkrepo.conan.listener.event.ConanPackageDeleteEvent
import com.tencent.bkrepo.conan.listener.event.ConanPackageUploadEvent
import com.tencent.bkrepo.conan.listener.event.ConanRecipeDeleteEvent
import com.tencent.bkrepo.conan.listener.event.ConanRecipeUploadEvent
import com.tencent.bkrepo.conan.listener.operation.ConanPackageDeleteOperation
import com.tencent.bkrepo.conan.listener.operation.ConanPackageUploadOperation
import com.tencent.bkrepo.conan.listener.operation.ConanRecipeDeleteOperation
import com.tencent.bkrepo.conan.listener.operation.ConanRecipeUploadOperation
import com.tencent.bkrepo.conan.pool.ConanThreadPoolExecutor
import com.tencent.bkrepo.conan.service.impl.CommonService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadPoolExecutor

@Component
class ConanEventListener(
    val commonService: CommonService
) {

    val threadPoolExecutor: ThreadPoolExecutor = ConanThreadPoolExecutor.instance

    /**
     * 删除conan recipe下的package，更新package下的index.json文件
     */
    @EventListener(ConanPackageDeleteEvent::class)
    fun handle(event: ConanPackageDeleteEvent) {
        val task = ConanPackageDeleteOperation(event.request, commonService).trace()
        threadPoolExecutor.submit(task)
    }

    /**
     * 删除conan recipe，更新
     */
    @EventListener(ConanRecipeDeleteEvent::class)
    fun handle(event: ConanRecipeDeleteEvent) {
        val task = ConanRecipeDeleteOperation(event.request, commonService).trace()
        threadPoolExecutor.submit(task)
    }

    /**
     * conan recipe 上传成功后，更新对应的index.json文件
     */
    @EventListener(ConanRecipeUploadEvent::class)
    fun handle(event: ConanRecipeUploadEvent) {
        val task = ConanRecipeUploadOperation(event.request, commonService).trace()
        threadPoolExecutor.submit(task)
    }

    /**
     * conan package 上传成功后，更新对应的index.json文件
     */
    @EventListener(ConanPackageUploadEvent::class)
    fun handle(event: ConanPackageUploadEvent) {
        val task = ConanPackageUploadOperation(event.request, commonService).trace()
        threadPoolExecutor.submit(task)
    }
}
