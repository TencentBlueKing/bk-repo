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

package com.tencent.bkrepo.cargo.listener

import com.tencent.bkrepo.cargo.listener.event.CargoPackageDeleteEvent
import com.tencent.bkrepo.cargo.listener.event.CargoPackageUploadEvent
import com.tencent.bkrepo.cargo.listener.event.CargoPackageYankEvent
import com.tencent.bkrepo.cargo.listener.operation.CargoPackageDeleteOperation
import com.tencent.bkrepo.cargo.listener.operation.CargoPackageUploadOperation
import com.tencent.bkrepo.cargo.listener.operation.CargoPackageYankOperation
import com.tencent.bkrepo.cargo.pool.CargoThreadPoolExecutor
import com.tencent.bkrepo.cargo.service.impl.CommonService
import com.tencent.bkrepo.common.service.otel.util.AsyncUtils.trace
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadPoolExecutor

@Component
class CargoEventListener(
    val commonService: CommonService
) {

    val threadPoolExecutor: ThreadPoolExecutor = CargoThreadPoolExecutor.instance

    /**
     * 删除cargo下的package，更新对应index目录下的文件
     */
    @EventListener(CargoPackageDeleteEvent::class)
    fun handle(event: CargoPackageDeleteEvent) {
        val task = CargoPackageDeleteOperation(event.request, commonService).trace()
        threadPoolExecutor.submit(task)
    }


    /**
     * cargo 上传成功后，更新对应index目录下的文件
     */
    @EventListener(CargoPackageUploadEvent::class)
    fun handle(event: CargoPackageUploadEvent) {
        val task = CargoPackageUploadOperation(event.request, commonService).trace()
        threadPoolExecutor.submit(task)
    }

    /**
     * cargo yank/unyank后，更新对应index目录下的文件
     */
    @EventListener(CargoPackageYankEvent::class)
    fun handle(event: CargoPackageYankEvent) {
        val task = CargoPackageYankOperation(event.request, commonService).trace()
        threadPoolExecutor.submit(task)
    }
}
