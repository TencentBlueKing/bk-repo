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

package com.tencent.bkrepo.replication.replica.replicator.standalone

import com.tencent.bkrepo.common.artifact.constant.SOURCE_TYPE
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.replication.exception.ArtifactSourceCheckException
import com.tencent.bkrepo.replication.exception.RegexCheckException
import com.tencent.bkrepo.replication.replica.repository.remote.ArtifactPushMappings
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.replicator.Replicator
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 同步到远端集群的同步实现类
 */
@Component
class RemoteReplicator : Replicator {

    override fun checkVersion(context: ReplicaContext) {
        // 同步到远端集群不需要进行校验
    }

    override fun replicaProject(context: ReplicaContext) {
        // 不允许同步整个项目到远端集群
    }

    override fun replicaRepo(context: ReplicaContext) {
        // 不允许同步整个项目到远端集群
    }

    override fun replicaPackage(context: ReplicaContext, packageSummary: PackageSummary) {
        // do nothing
    }

    override fun replicaPackageVersion(
        context: ReplicaContext,
        packageSummary: PackageSummary,
        packageVersion: PackageVersion
    ): Boolean {
        if (!checkSourceType(packageVersion))
            throw ArtifactSourceCheckException(
                "Current version is coming from the proxy or replication source, so ignore it"
            )
        if (!remotePackageConstraint(context, packageSummary, packageVersion))
            throw RegexCheckException("Error occurred while checking the rules for package and version")
        return ArtifactPushMappings.push(packageSummary, packageVersion, context)
    }

    /**
     * 只针对version中元数据 sourceType为ArtifactChannel.LOCAL的package才推送
     */
    private fun checkSourceType(packageVersion: PackageVersion): Boolean {
        if (packageVersion.metadata.isEmpty() || packageVersion.metadata[SOURCE_TYPE] == null) return true
        return packageVersion.metadata[SOURCE_TYPE] == ArtifactChannel.LOCAL
    }

    /**
     * 针对third party集群限制逻辑和其他不一样，做特殊处理
     * 非third party集群是只要无限制条件则同步所有，有限制条件则按规则走
     * third party集群需要的是无限制条件则只同步当前version，有限制条件则按规则走
     */
    private fun remotePackageConstraint(
        context: ReplicaContext,
        packageSummary: PackageSummary,
        packageVersion: PackageVersion
    ): Boolean {
        with(context) {
            return regexCheck(
                packageName = packageSummary.name,
                version = packageVersion.name,
                extension = remoteCluster.extension
            )
        }
    }

    /**
     * 判断package name或者version是否满足规则
     * 如果package规则和version规则都为空，则返回true
     * 如果name满足，则继续判断version规则，如都满足则返回true
     * 如果name满足, version不满足，则返回false
     * 如果name不满足，则返回false
     */
    private fun regexCheck(
        packageName: String,
        version: String,
        extension: Map<String, Any>?
    ): Boolean {
        logger.info("Will check the rules for packageName and version")
        if (extension.isNullOrEmpty()) return true
        val packageRegex = extension["packageRegex"] as List<*>?
        val versionRegex = extension["versionRegex"] as List<*>?
        if (!packageRegex.isNullOrEmpty()) {
            var tempFlag = false
            packageRegex.forEach {
                if (packageName.matches(Regex(it as String))) {
                    tempFlag = true
                    return@forEach
                }
            }
            if (!tempFlag) return false
        }
        if (versionRegex.isNullOrEmpty()) {
            return true
        }
        versionRegex.forEach {
            if (version.matches(Regex(it as String))) return true
        }
        return false
    }

    override fun replicaFile(context: ReplicaContext, node: NodeInfo): Boolean {
        // 暂时不支持同步单个文件到外部集群
        return true
    }

    override fun replicaDir(context: ReplicaContext, node: NodeInfo) {
        // 暂时不支持同步目录到外部集群
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RemoteReplicator::class.java)
    }
}
