/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.job.service

import com.tencent.bkrepo.job.migrate.pojo.MigrationContext
import com.tencent.bkrepo.job.migrate.pojo.Node

interface MigrateArchivedFileService {
    /**
     * 迁移已归档制品
     *
     * @param context 迁移上下文
     * @param node 待迁移制品
     *
     * @return 是否迁移成功，返回true表示已迁移或目标存储中已存在对应的归档文件，false表示文件未归档，抛出异常表示不支持迁移的归档状态
     *
     * @throws IllegalStateException 处于不支持迁移的状态时抛出该异常
     */
    fun migrateArchivedFile(context: MigrationContext, node: Node): Boolean

    /**
     * 迁移已归档制品
     *
     * @param srcStorageKey 源存储
     * @param dstStorageKey 目标存储
     * @param sha256 待迁移制品sha256
     *
     * @return 是否迁移成功，返回true表示已迁移或目标存储中已存在对应的归档文件，false表示文件未归档，抛出异常表示不支持迁移的归档状态
     *
     * @throws IllegalStateException 处于不支持迁移的状态时抛出该异常
     */
    fun migrateArchivedFile(srcStorageKey: String?, dstStorageKey: String?, sha256: String): Boolean

    /**
     * 检查归档文件是否存在，且处于COMPLETED状态
     *
     * @param storageKey 存储key
     * @param sha256 制品sha256
     *
     * @return true表示归档文件存在且未COMPLETED状态，false表示归档文件不存在
     *
     * @throws IllegalStateException 归档文件存在但是处于非COMPLETED状态
     */
    fun archivedFileCompleted(storageKey: String?, sha256: String): Boolean
}
