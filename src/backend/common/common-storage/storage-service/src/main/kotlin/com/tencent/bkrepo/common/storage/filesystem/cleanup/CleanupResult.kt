/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.storage.filesystem.cleanup

import com.tencent.bkrepo.common.api.util.HumanReadable

data class CleanupResult(
    var totalFile: Long = 0,
    var totalFolder: Long = 0,
    var totalSize: Long = 0,
    var cleanupFile: Long = 0,
    var cleanupFolder: Long = 0,
    var cleanupSize: Long = 0,
    var errorCount: Long = 0,
    /**
     * 根目录下除了tempPath与stagingPath子目录外被访问的文件数量
     */
    var rootDirNotDeletedFile: Long = 0,
    /**
     * 根目录下除了tempPath与stagingPath子目录外被访问的文件大小
     */
    var rootDirNotDeletedSize: Long = 0,
    /**
     * 根据保留策略保留的文件数量
     */
    var retainFile: Long = 0,
    /**
     * 根据保留策略保留的文件大小
     */
    var retainSize: Long = 0,
    /**
     * 保留的文件sha256
     */
    var retainSha256: MutableSet<String> = HashSet(),
) {

    override fun toString(): String {
        return "$cleanupFile/$totalFile[${HumanReadable.size(cleanupSize)}/${HumanReadable.size(totalSize)}] " +
            "files deleted, errorCount[$errorCount], $cleanupFolder/$totalFolder dirs deleted, " +
                "retainCount[$retainFile], retainSize[${HumanReadable.size(retainSize)}]"
    }
}
