/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.oci.service.impl

import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.oci.pojo.artifact.OciTagArtifactInfo
import com.tencent.bkrepo.oci.pojo.tags.TagsInfo
import com.tencent.bkrepo.oci.service.OciTagService
import com.tencent.bkrepo.repository.api.PackageClient
import org.springframework.stereotype.Service

@Service
class OciTagServiceImpl(
    private val packageClient: PackageClient
) : OciTagService {
    override fun getTagList(artifactInfo: OciTagArtifactInfo, n: Int?, last: String?): TagsInfo {
        with(artifactInfo) {
            val versionList = packageClient.listAllVersion(
                projectId,
                repoName,
                PackageKeys.ofOci(packageName)
            ).data.orEmpty()
            var tagList = mutableListOf<String>()
            versionList.forEach {
                tagList.add(it.name)
            }
            tagList.sort()
            tagList = filterHandler(
                tags = tagList,
                n = n,
                last = last
            )
            return TagsInfo(packageName, tagList)
        }
    }

    /**
     * 根据n或者last进行过滤（注意n是否会超过tags总长）
     * 1 n和last 都不存在，则返回所有
     * 2 n存在， last不存在，则返回前n个
     * 3 last存在 n不存在， 则返回查到的last，如不存在，则返回空列表
     * 4 last存在，n存在，则返回last之后的n个
     */
    private fun filterHandler(tags: MutableList<String>, n: Int?, last: String?): MutableList<String> {
        var tagList = tags
        if (n != null) {
            tagList = handleNFilter(tagList, n, last)
        } else {
            if (!last.isNullOrEmpty()) {
                val index = tagList.indexOf(last)
                tagList = if (index == -1) {
                    mutableListOf()
                } else {
                    mutableListOf(last)
                }
            }
        }
        return tagList
    }

    /**
     * 处理n存在时的逻辑
     */
    private fun handleNFilter(tags: MutableList<String>, n: Int, last: String?): MutableList<String> {
        var tagList = tags
        var size = n
        val length = tagList.size
        tagList = if (!last.isNullOrEmpty()) {
            // 当last存在，n也存在 则获取last所在后n个tag
            val index = tagList.indexOf(last)
            if (index == -1) {
                mutableListOf()
            } else {
                // 需要判断last后n个是否超过tags总长
                if (index + size + 1 > length) {
                    size = length - 1 - index
                }
                tagList.subList(index + 1, size + 1)
            }
        } else {
            // 需要判断n个是否超过tags总长
            if (size > length) {
                size = length
            }
            tagList.subList(0, size)
        }
        return tagList
    }
}
