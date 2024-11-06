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

package com.tencent.bkrepo.analyst.statemachine.iterator

import com.tencent.bkrepo.analyst.pojo.Node
import org.slf4j.LoggerFactory

/**
 * 用于过滤需要扫描的制品
 */
class NodeFilter(
    private val unsupportedArtifactName: List<Regex>
) {

    /**
     * 过滤出需要扫描的制品
     *
     * @param node 待判断的制品
     *
     * @return true 需要扫描， false 不需要扫描
     */
    fun filter(node: Node): Boolean {
        val artifactName = node.packageKey ?: node.artifactName
        if (unsupportedArtifactName.any { it.matches(artifactName) }) {
            logger.info("node[$artifactName], sha256[${node.sha256}] is unsupported to scan")
            return false
        }

        return true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeFilter::class.java)
    }
}
