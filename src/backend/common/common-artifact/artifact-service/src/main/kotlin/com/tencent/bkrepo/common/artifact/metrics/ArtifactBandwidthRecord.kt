/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.metrics

/**
 * 流量聚合记录
 * 用于按维度（项目/仓库/类型）聚合流量数据，定期上报后清理内存
 *
 * @param projectId 项目ID
 * @param repoName 仓库名称
 * @param type 传输类型（RECEIVE/RESPONSE）
 * @param bytes 聚合的字节数
 */
data class ArtifactBandwidthRecord(
    val projectId: String,
    val repoName: String,
    val type: String,
    val bytes: Long,
) {
    companion object {
        const val LABEL_PROJECT_ID = "projectId"
        const val LABEL_REPO_NAME = "repoName"
        const val LABEL_TYPE = "type"

        // 传输类型常量
        const val TYPE_RECEIVE = "RECEIVE"
        const val TYPE_RESPONSE = "RESPONSE"

        /**
         * 生成聚合键
         * @return 格式: "projectId:repoName:type"
         */
        fun buildKey(projectId: String, repoName: String, type: String): String {
            return "$projectId:$repoName:$type"
        }

        /**
         * 从聚合键解析出各维度值
         * @return Triple(projectId, repoName, type)
         */
        fun parseKey(key: String): Triple<String, String, String> {
            val parts = key.split(":")
            require(parts.size == 3) { "Invalid bandwidth record key: $key" }
            return Triple(parts[0], parts[1], parts[2])
        }
    }
}

