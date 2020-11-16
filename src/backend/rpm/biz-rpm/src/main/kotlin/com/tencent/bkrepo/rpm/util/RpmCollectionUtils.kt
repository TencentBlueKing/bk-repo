/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadata

object RpmCollectionUtils {

    fun List<NodeInfo>.filterRpmCustom(set: MutableList<String>, enabledFileLists: Boolean): List<NodeInfo> {
        val resultList = mutableListOf<NodeInfo>()
        try {
            resultList.add(
                this.first {
                    it.metadata?.get("indexType") == "primary"
                }
            )
            resultList.add(
                this.first {
                    it.metadata?.get("indexType") == "others"
                }
            )
            if (enabledFileLists) {
                resultList.add(
                    this.first {
                        it.metadata?.get("indexType") == "filelists"
                    }
                )
            }
        } catch (noSuchElementException: NoSuchElementException) {
            // todo
            // 仓库中还没有生成索引
        }
        val doubleSet = mutableSetOf<String>()
        for (str in set) {
            doubleSet.add(str)
            doubleSet.add("${str}_gz")
        }

        for (str in doubleSet) {
            try {
                resultList.add(
                    this.first {
                        it.metadata?.get("indexName") == str
                    }
                )
            } catch (noSuchElementException: NoSuchElementException) {
                // todo
                // 用户未上传对应分组文件
            }
        }
        return resultList
    }

    fun MutableList<String>.updateList(set: MutableSet<String>, mark: Boolean) {
        if (mark) {
            for (group in set) {
                if (!this.contains(group)) this.add(group)
            }
        } else {
            this.removeAll(set)
        }
    }

    fun RpmMetadata.filterRpmFileLists() {
        this.packages[0].format.files = this.packages[0].format.files.filter {
            (it.filePath.contains("bin/") && (it.filePath.endsWith(".sh"))) ||
                (it.filePath.startsWith("/etc/") && it.filePath.contains("conf")) ||
                it.filePath == "/usr/lib/sendmail"
        }
    }
}
