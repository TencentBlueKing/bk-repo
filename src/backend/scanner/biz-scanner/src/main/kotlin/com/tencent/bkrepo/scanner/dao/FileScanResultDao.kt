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

package com.tencent.bkrepo.scanner.dao

import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.common.scanner.pojo.scanner.Scanner
import com.tencent.bkrepo.scanner.model.TFileScanResult
import com.tencent.bkrepo.scanner.model.TScanResult
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class FileScanResultDao : SimpleMongoDao<TFileScanResult>() {
    /**
     * 更新扫描结果
     *
     * @param credentialsKey 文件所在存储，为null时表示在默认存储
     * @param sha256 文件sha256
     * @param taskId 扫描任务id
     * @param scanner 使用的扫描器
     * @param resultOverview 结果预览
     * @param startDateTime 文件开始执行扫描的时间
     * @param finishedDateTime 文件结束扫描的时间
     */
    fun upsertResult(
        credentialsKey: String?,
        sha256: String,
        taskId: String,
        scanner: Scanner,
        resultOverview: Map<String, Any?>,
        startDateTime: LocalDateTime,
        finishedDateTime: LocalDateTime
    ): UpdateResult {
        // TODO 通用upsert重试逻辑抽象
        return try {
            doUpsertResult(
                credentialsKey, sha256, taskId, scanner, resultOverview, startDateTime, finishedDateTime
            )
        } catch (e: DuplicateKeyException) {
            doUpsertResult(
                credentialsKey, sha256, taskId, scanner, resultOverview, startDateTime, finishedDateTime
            )
        }
    }

    private fun doUpsertResult(
        credentialsKey: String?,
        sha256: String,
        taskId: String,
        scanner: Scanner,
        resultOverview: Map<String, Any?>,
        startDateTime: LocalDateTime,
        finishedDateTime: LocalDateTime
    ): UpdateResult {
        val criteria = TFileScanResult::credentialsKey.isEqualTo(credentialsKey)
            .and(TFileScanResult::sha256).isEqualTo(sha256)
        val scanResult = TScanResult(
            taskId = taskId,
            startDateTime = startDateTime,
            finishedDateTime = finishedDateTime,
            scanner = scanner.name,
            scannerType = scanner.type,
            scannerVersion = scanner.version,
            overview = resultOverview
        )
        val update = buildUpdate(finishedDateTime).set(scanner.name, scanResult)
        return upsert(Query(criteria), update)
    }

    /**
     * TODO 通用更新lastModifiedDate逻辑抽象
     */
    private fun buildUpdate(lastModifiedDate: LocalDateTime = LocalDateTime.now()): Update =
        Update.update(TFileScanResult::lastModifiedDate.name, lastModifiedDate)
}
