/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.migrate.pojo

import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class MigrationContext(
    val task: MigrateRepoStorageTask,
    val srcCredentials: StorageCredentials?,
    val dstCredentials: StorageCredentials?,
) {
    private var transferringCount: Long = 0
    private val lock: ReentrantLock = ReentrantLock()
    private val condition: Condition = lock.newCondition()

    fun transferring() = transferringCount

    /**
     * 增加传输中的制品数量
     */
    fun incTransferringCount() {
        lock.withLock { transferringCount++ }
    }

    /**
     * 减少传输中的制品数量
     */
    fun decTransferringCount() {
        lock.withLock {
            transferringCount--
            if (transferringCount == 0L) {
                condition.signal()
            }
        }
    }

    /**
     * 等待所有数据传输完成
     */
    fun waitAllTransferFinished() {
        lock.withLock {
            while (transferringCount != 0L) {
                condition.await()
            }
        }
    }
}
