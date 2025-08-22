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

package com.tencent.bkrepo.analyst.component.manager

abstract class AbstractScanExecutorResultManager : ScanExecutorResultManager {
    protected inline fun <T, reified R : ResultItem<T>> convert(
        credentialsKey: String?,
        sha256: String,
        scanner: String,
        data: T
    ): R {
        return R::class.java.constructors[0].newInstance(null, credentialsKey, sha256, scanner, data) as R
    }

    /**
     * 替换同一文件使用同一扫描器原有的扫描结果
     */
    protected fun <T : ResultItem<*>, D : ResultItemDao<T>> replace(
        credentialsKey: String?,
        sha256: String,
        scanner: String,
        resultItemDao: D,
        resultItems: List<T>
    ) {
        resultItemDao.deleteBy(credentialsKey, sha256, scanner)
        resultItemDao.insert(resultItems)
    }

    /**
     * 批量清理报告
     *
     * @param resultItemDaoList 不同类型报告Dao
     * @param sha256 制品sha256
     * @param scannerName 扫描器名
     * @param batchSize 清理批大小
     *
     * @return 清理的报告数量
     */
    protected fun clean(
        resultItemDaoList: List<ResultItemDao<*>>,
        credentialsKey: String?,
        sha256: String,
        scannerName: String,
        batchSize: Int?
    ): Long {
        var deletedCount = 0L
        var currentBatchSize = batchSize?.toLong()
        for (resultItemDao in resultItemDaoList) {
            if (currentBatchSize != null && currentBatchSize <= 0) {
                return deletedCount
            }

            val result = resultItemDao.deleteBy(credentialsKey, sha256, scannerName, currentBatchSize?.toInt())
            deletedCount += result.deletedCount

            if (currentBatchSize != null) {
                currentBatchSize -= result.deletedCount
            }
        }

        return deletedCount
    }
}
