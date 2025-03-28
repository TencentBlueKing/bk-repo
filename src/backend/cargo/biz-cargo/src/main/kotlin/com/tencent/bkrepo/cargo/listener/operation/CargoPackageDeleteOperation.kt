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

package com.tencent.bkrepo.cargo.listener.operation

import com.tencent.bkrepo.cargo.pojo.event.CargoOperationRequest
import com.tencent.bkrepo.cargo.pojo.event.CargoPackageDeleteRequest
import com.tencent.bkrepo.cargo.pojo.index.CrateIndex
import com.tencent.bkrepo.cargo.service.impl.CommonService

class CargoPackageDeleteOperation(
    private val request: CargoOperationRequest,
    private val commonService: CommonService
) : AbstractCargoOperation(request, commonService) {

    override fun handleEvent(versions: MutableList<CrateIndex>): MutableList<CrateIndex> {
        // TODO 删除index中的版本是否需要删除对应制品
        with(request as CargoPackageDeleteRequest) {
            logger.info(
                "Index will be refreshed for removing version $version of crate $name in repo $projectId|$repoName"
            )
            // 删除版本
            return if (versions.isNotEmpty()) {
                versions.filter { it.vers != version }.sortedBy { it.vers }.toMutableList()
            } else {
                mutableListOf()
            }
        }
    }

}
