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

package com.tencent.bkrepo.scanner.task.iterator

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.scanner.pojo.Node
import org.slf4j.LoggerFactory

/**
 * 依赖包迭代器
 */
class PackageIterator(
    private val packageClient: PackageClient,
    private val nodeClient: NodeClient,
    override val position: PackageIteratePosition
) : PageableIterator<Node>() {
    override fun nextPageData(page: Int, pageSize: Int): List<Node> {
        val packages = requestPackages(page, pageSize)

        val nodeQueryRule = nodeQueryRule(packages)
        val nodeQueryModel = QueryModel(PageLimit(DEFAULT_PAGE_NUMBER, pageSize), null, nodeSelect, nodeQueryRule)
        val nodeRes = nodeClient.search(nodeQueryModel)
        if (nodeRes.isNotOk()) {
            logger.error("Search nodes failed: [${nodeRes.message}], queryModel:[$nodeQueryModel]")
            throw SystemErrorException()
        }

        return convert(packages, nodeRes.data!!.records)
    }

    private fun requestPackages(page: Int, pageSize: Int): List<Package> {
        val packageQueryModel = QueryModel(PageLimit(page, pageSize), null, packageSelect, position.rule)
        val packageRes = packageClient.searchPackage(packageQueryModel)
        if (packageRes.isNotOk()) {
            logger.error("Search package failed: [${packageRes.message}], queryModel:[$packageQueryModel]")
            throw SystemErrorException()
        }
        if (packageRes.data!!.records.isEmpty()) {
            return emptyList()
        }
        return packageRes.data!!.records.map { convert(it) }
    }

    private fun nodeQueryRule(packages: List<Package>): Rule {
        TODO()
    }

    private fun convert(packageSummary: Map<*, *>) = Package(
        projectId = packageSummary[PackageSummary::projectId.name] as String,
        repoName = packageSummary[PackageSummary::repoName.name] as String,
        artifactName = packageSummary[PackageSummary::name.name] as String,
        packageKey = packageSummary[PackageSummary::key.name] as String,
        packageVersion = packageSummary[PackageSummary::latest.name] as String
    )

    private fun convert(packages: List<Package>, nodeDetail: List<Map<String, Any?>>): List<Node> {
        TODO()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PackageIterator::class.java)
        private val packageSelect = listOf(
            PackageSummary::projectId.name,
            PackageSummary::repoName.name,
            PackageSummary::key.name,
            PackageSummary::name.name,
            PackageSummary::latest.name
        )
        private val nodeSelect = listOf(
            NodeDetail::sha256.name,
            NodeDetail::size.name,
            NodeDetail::fullPath.name,
            NodeDetail::repoName.name,
            NodeDetail::name.name
        )
    }

    private data class Package(
        val projectId: String,
        val repoName: String,
        val artifactName: String,
        val packageKey: String,
        val packageVersion: String
    )

    /**
     * 当前遍历到的位置
     */
    data class PackageIteratePosition(
        /**
         * 需要遍历的依赖包匹配规则
         */
        val rule: Rule,
        override var page: Int = INITIAL_PAGE,
        override var pageSize: Int = DEFAULT_PAGE_SIZE,
        override var index: Int = INITIAL_INDEX
    ) : PageIteratePosition(page = page, pageSize = pageSize, index = index)
}
