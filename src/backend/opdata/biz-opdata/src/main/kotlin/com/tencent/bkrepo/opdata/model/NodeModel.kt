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

package com.tencent.bkrepo.opdata.model

import com.tencent.bkrepo.opdata.constant.B_0
import com.tencent.bkrepo.opdata.constant.GB_1
import com.tencent.bkrepo.opdata.constant.GB_10
import com.tencent.bkrepo.opdata.constant.MB_100
import com.tencent.bkrepo.opdata.constant.MB_500
import com.tencent.bkrepo.opdata.pojo.RepoMetrics
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.FileExtensionStatInfo
import java.lang.Exception
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class NodeModel @Autowired constructor(
    private val nodeClient: NodeClient
) {
    companion object {
        private val sizeRange = listOf(B_0, MB_100, MB_500, GB_1, GB_10)
    }

    fun getNodeSize(projectId: String, repoName: String): RepoMetrics {
        try {
            val result = nodeClient.computeSize(projectId, repoName, "/").data ?: return RepoMetrics(repoName, 0L, 0L)
            return RepoMetrics(repoName, result.size, result.subNodeCount)
        } catch (ignored: Exception) {
            return RepoMetrics(repoName, 0L, 0L)
        }
    }

    fun getProjNodeSizeDistribution(projectId: String): Map<String, Long> {
        return nodeClient.computeSizeDistribution(projectId, sizeRange, null).data ?: mapOf()
    }

    fun getRepoNodeSizeDistribution(projectId: String, repoName: String): Map<String, Long> {
        return nodeClient.computeSizeDistribution(projectId, sizeRange, repoName).data ?: mapOf()
    }

    fun getFileExtensions(projectId: String, repoName: String?): List<String> {
        return nodeClient.getFileExtensions(projectId, repoName).data ?: listOf()
    }

    fun getFileExtensionStat(projectId: String, extension: String, repoName: String?): FileExtensionStatInfo {
        return nodeClient.statFileExtension(projectId, extension, repoName).data
            ?: FileExtensionStatInfo(projectId, repoName, extension, 0L, 0L)
    }
}
