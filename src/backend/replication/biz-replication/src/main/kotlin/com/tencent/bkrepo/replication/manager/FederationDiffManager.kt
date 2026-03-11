package com.tencent.bkrepo.replication.manager

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import com.tencent.bkrepo.replication.pojo.federation.FederationDiffStats
import com.tencent.bkrepo.replication.pojo.federation.FederationNodeCount
import com.tencent.bkrepo.replication.pojo.federation.FederationPathDiff
import com.tencent.bkrepo.replication.pojo.federation.PathChildDiff
import com.tencent.bkrepo.replication.pojo.federation.PathDiffStats
import com.tencent.bkrepo.replication.pojo.federation.PathDiffStatus
import com.tencent.bkrepo.replication.pojo.federation.PathStatistics
import com.tencent.bkrepo.replication.pojo.request.DirectChildInfo
import com.tencent.bkrepo.replication.pojo.request.DirectChildrenPage
import com.tencent.bkrepo.replication.pojo.request.DirectChildrenRequest
import com.tencent.bkrepo.replication.pojo.request.NodeCountRequest
import com.tencent.bkrepo.replication.pojo.request.PathCountRequest
import com.tencent.bkrepo.replication.pojo.request.PathStatsRequest
import com.tencent.bkrepo.replication.pojo.request.PathStatsResult
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.impl.federation.LocalFederationManager
import com.tencent.bkrepo.replication.util.FederationDataBuilder.buildClusterInfo
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 联邦仓库差异对比管理器
 * 负责执行本地与远程集群之间的数据一致性对比
 */
@Component
class FederationDiffManager(
    private val localDataManager: LocalDataManager,
    private val clusterNodeService: ClusterNodeService,
    private val localFederationManager: LocalFederationManager,
    private val repositoryService: RepositoryService,
) {

    /**
     * 对比节点总数（最轻量级）
     */
    fun compareNodeCount(
        projectId: String,
        repoName: String,
        federationId: String,
        targetClusterId: String?,
        rootPath: String = "/"
    ): List<FederationNodeCount> {
        logger.info("Comparing federation node count for [$projectId|$repoName], federationId: $federationId")
        val startTime = System.currentTimeMillis()

        val federationRepository = localFederationManager.getFederationRepository(projectId, repoName, federationId)
            ?: throw ErrorCodeException(
                ReplicationMessageCode.FEDERATION_REPOSITORY_NOT_FOUND,
                "Federation repository not found: $federationId"
            )

        val clustersToCompare = filterClusters(federationRepository.federatedClusters, targetClusterId)

        return clustersToCompare.map { cluster ->
            compareClusterNodeCount(projectId, repoName, federationId, rootPath, cluster, startTime)
        }
    }

    /**
     * 对比路径差异（分页）
     * 对于非 generic 仓库，改用 package 层级对比
     */
    fun comparePathDiff(
        projectId: String,
        repoName: String,
        federationId: String,
        targetClusterId: String,
        path: String,
        pageNumber: Int,
        pageSize: Int,
        onlyDiff: Boolean
    ): FederationPathDiff {
        logger.info(
            "Comparing federation path diff for [$projectId|$repoName], " +
                "federationId: $federationId, targetCluster: $targetClusterId, path: $path"
        )

        val federationRepository = localFederationManager.getFederationRepository(projectId, repoName, federationId)
            ?: throw ErrorCodeException(
                ReplicationMessageCode.FEDERATION_REPOSITORY_NOT_FOUND,
                "Federation repository not found: $federationId"
            )

        val cluster = federationRepository.federatedClusters.find { it.clusterId == targetClusterId }
            ?: throw ErrorCodeException(
                ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND,
                "Cluster not found: $targetClusterId"
            )

        val clusterInfo = clusterNodeService.getByClusterId(cluster.clusterId)
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, cluster.clusterId)

        // 判断仓库类型
        val repoDetail = repositoryService.getRepoDetail(projectId, repoName)
        val supportPackage = repoDetail?.type?.supportPackage ?: false

        return if (supportPackage && path == "/") {
            // 非 generic 仓库且在根路径，使用 package 对比
            doComparePackageDiff(
                projectId, repoName, federationId, pageNumber, pageSize, onlyDiff, cluster, clusterInfo
            )
        } else {
            // generic 仓库或子路径，使用 node 对比
            doComparePathDiff(
                projectId, repoName, federationId, path, pageNumber, pageSize, onlyDiff, cluster, clusterInfo
            )
        }
    }

    /**
     * 对比路径统计（多层聚合）
     */
    fun compareDiffStats(
        projectId: String,
        repoName: String,
        federationId: String,
        targetClusterId: String,
        path: String,
        depth: Int
    ): FederationDiffStats {
        val startTime = System.currentTimeMillis()
        logger.info(
            "Comparing federation diff stats for [$projectId|$repoName], " +
                "federationId: $federationId, targetCluster: $targetClusterId, path: $path, depth: $depth"
        )

        val federationRepository = localFederationManager.getFederationRepository(projectId, repoName, federationId)
            ?: throw ErrorCodeException(
                ReplicationMessageCode.FEDERATION_REPOSITORY_NOT_FOUND,
                "Federation repository not found: $federationId"
            )

        val cluster = federationRepository.federatedClusters.find { it.clusterId == targetClusterId }
            ?: throw ErrorCodeException(
                ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND,
                "Cluster not found: $targetClusterId"
            )

        val clusterInfo = clusterNodeService.getByClusterId(cluster.clusterId)
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, cluster.clusterId)

        val localStats = localDataManager.getPathStats(projectId, repoName, path, depth)
        val remoteStats = fetchRemotePathStats(clusterInfo, cluster.projectId, cluster.repoName, path, depth)
        val rootDiff = comparePathStatsRecursive(localStats, remoteStats)

        val durationMs = System.currentTimeMillis() - startTime
        logger.info(
            "Federation diff stats completed for [${clusterInfo.name}] at [$path]: " +
                "localCount=${localStats.fileCount}, remoteCount=${remoteStats?.fileCount ?: 0}, " +
                "consistent=${rootDiff.diffStatus == PathDiffStatus.CONSISTENT}, duration=${durationMs}ms"
        )

        return FederationDiffStats(
            federationId = federationId,
            remoteClusterId = cluster.clusterId,
            remoteClusterName = clusterInfo.name,
            root = rootDiff,
            depth = depth,
            durationMs = durationMs
        )
    }

    /**
     * 智能对比（推荐方法）
     */
    fun smartCompare(
        projectId: String,
        repoName: String,
        federationId: String,
        targetClusterId: String,
        rootPath: String = "/",
        maxDepth: Int = 3
    ): List<String> {
        logger.info("Smart comparing federation diff for [$projectId|$repoName], federationId: $federationId")
        val startTime = System.currentTimeMillis()

        // Step 1: 快速健康检查
        val nodeCount = compareNodeCount(projectId, repoName, federationId, targetClusterId, rootPath).first()
        if (nodeCount.localNodeCount != nodeCount.remoteNodeCount) {
            logger.warn("Node count mismatch: local=${nodeCount.localNodeCount}, remote=${nodeCount.remoteNodeCount}")
        }

        // Step 2: 聚合哈希定位差异
        val diffStats = compareDiffStats(
            projectId, repoName, federationId, targetClusterId,
            path = rootPath,
            depth = maxDepth.coerceIn(1, 3)
        )

        // Step 3: 收集不一致路径
        val inconsistentPaths = collectInconsistentPaths(diffStats.root)

        val durationMs = System.currentTimeMillis() - startTime
        logger.info(
            "Smart comparison completed: found ${inconsistentPaths.size} inconsistent paths, duration=${durationMs}ms"
        )

        return inconsistentPaths
    }

    // ==================== 私有方法 ====================

    private fun doComparePackageDiff(
        projectId: String,
        repoName: String,
        federationId: String,
        pageNumber: Int,
        pageSize: Int,
        onlyDiff: Boolean,
        cluster: FederatedCluster,
        clusterInfo: ClusterNodeInfo
    ): FederationPathDiff {
        val safePageNumber = pageNumber.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, 1000)

        val localTotalCount = localDataManager.countPackages(projectId, repoName)
        val remoteTotalCount = countRemotePackages(clusterInfo, cluster.projectId, cluster.repoName)

        val localPackages = localDataManager.listPackagesForDiff(projectId, repoName, safePageNumber, safePageSize)
        val remotePackages =
            fetchRemotePackages(clusterInfo, cluster.projectId, cluster.repoName, safePageNumber, safePageSize)

        val packageDiffs = comparePackages(
            projectId, repoName, localPackages, remotePackages, cluster, clusterInfo, onlyDiff
        )

        val totalPackages = maxOf(localTotalCount, remoteTotalCount)
        val hasMore = (safePageNumber.toLong() * safePageSize) < totalPackages
        val isConsistent = localTotalCount == remoteTotalCount
            && packageDiffs.all { it.diffStatus == PathDiffStatus.CONSISTENT }

        logger.info(
            "Federation package diff for [${clusterInfo.name}]: " +
                "localTotal=$localTotalCount, remoteTotal=$remoteTotalCount, " +
                "page=$safePageNumber/$safePageSize, packages=${packageDiffs.size}"
        )

        return FederationPathDiff(
            federationId = federationId,
            remoteClusterId = cluster.clusterId,
            remoteClusterName = clusterInfo.name,
            path = "/",
            localTotalCount = localTotalCount,
            remoteTotalCount = remoteTotalCount,
            consistent = isConsistent,
            children = packageDiffs,
            pageNumber = safePageNumber,
            pageSize = safePageSize,
            totalChildren = totalPackages,
            hasMore = hasMore
        )
    }

    private fun comparePackages(
        projectId: String,
        repoName: String,
        localPackages: List<Any>,
        remotePackages: List<Any>,
        cluster: FederatedCluster,
        clusterInfo: ClusterNodeInfo,
        onlyDiff: Boolean
    ): List<PathChildDiff> {
        val localMap = localPackages.associateBy { (it as PackageSummary).key }
        val remoteMap = remotePackages.associateBy { (it as PackageSummary).key }
        val allKeys = (localMap.keys + remoteMap.keys).distinct().sorted()

        return allKeys.mapNotNull { key ->
            val localPkg = localMap[key] as? PackageSummary
            val remotePkg = remoteMap[key] as? PackageSummary

            val diffInfo = when {
                remotePkg == null && localPkg != null ->
                    createLocalOnlyPackageDiff(localPkg)

                localPkg == null && remotePkg != null ->
                    createRemoteOnlyPackageDiff(remotePkg)

                localPkg != null && remotePkg != null ->
                    comparePackageBothExist(localPkg, remotePkg)

                else -> null
            }

            if (onlyDiff && diffInfo?.diffStatus == PathDiffStatus.CONSISTENT) null else diffInfo
        }
    }

    private fun createLocalOnlyPackageDiff(pkg: PackageSummary): PathChildDiff {
        return PathChildDiff(
            fullPath = "/${pkg.key}",
            folder = true,
            diffStatus = PathDiffStatus.LOCAL_ONLY,
            localCount = pkg.versions.toLong(),
            remoteCount = 0
        )
    }

    private fun createRemoteOnlyPackageDiff(pkg: PackageSummary): PathChildDiff {
        return PathChildDiff(
            fullPath = "/${pkg.key}",
            folder = true,
            diffStatus = PathDiffStatus.REMOTE_ONLY,
            localCount = 0,
            remoteCount = pkg.versions.toLong()
        )
    }

    private fun comparePackageBothExist(
        localPkg: PackageSummary,
        remotePkg: PackageSummary
    ): PathChildDiff {
        val localVersionCount = localPkg.versions.toLong()
        val remoteVersionCount = remotePkg.versions.toLong()
        val status = if (localVersionCount == remoteVersionCount) {
            PathDiffStatus.CONSISTENT
        } else {
            PathDiffStatus.COUNT_MISMATCH
        }

        return PathChildDiff(
            fullPath = "/${localPkg.key}",
            folder = true,
            diffStatus = status,
            localCount = localVersionCount,
            remoteCount = remoteVersionCount
        )
    }

    private fun compareClusterNodeCount(
        projectId: String,
        repoName: String,
        federationId: String,
        rootPath: String,
        cluster: FederatedCluster,
        startTime: Long
    ): FederationNodeCount {
        val clusterInfo = clusterNodeService.getByClusterId(cluster.clusterId)
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, cluster.clusterId)

        // 判断仓库类型，针对支持 package 的仓库统计 package 数量
        val repoDetail = repositoryService.getRepoDetail(projectId, repoName)
        val supportPackage = repoDetail?.type?.supportPackage ?: false

        val localNodeCount = if (supportPackage) {
            localDataManager.countPackages(projectId, repoName)
        } else {
            localDataManager.countFileNode(projectId, repoName, rootPath)
        }

        val remoteNodeCount = if (supportPackage) {
            countRemotePackages(clusterInfo, cluster.projectId, cluster.repoName)
        } else {
            countRemoteNodes(clusterInfo, cluster.projectId, cluster.repoName, rootPath)
        }

        val durationMs = System.currentTimeMillis() - startTime
        logger.info(
            "Federation ${if (supportPackage) "package" else "node"} count for cluster [${clusterInfo.name}]: " +
                "local=$localNodeCount, remote=$remoteNodeCount, " +
                "diff=${localNodeCount - remoteNodeCount}, duration=${durationMs}ms"
        )

        return FederationNodeCount(
            federationId = federationId,
            localProjectId = projectId,
            localRepoName = repoName,
            remoteClusterId = cluster.clusterId,
            remoteClusterName = clusterInfo.name,
            remoteProjectId = cluster.projectId,
            remoteRepoName = cluster.repoName,
            localNodeCount = localNodeCount,
            remoteNodeCount = remoteNodeCount,
            durationMs = durationMs
        )
    }

    private fun doComparePathDiff(
        projectId: String,
        repoName: String,
        federationId: String,
        path: String,
        pageNumber: Int,
        pageSize: Int,
        onlyDiff: Boolean,
        cluster: FederatedCluster,
        clusterInfo: ClusterNodeInfo
    ): FederationPathDiff {
        val safePageNumber = pageNumber.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, 1000)

        val localTotalCount = localDataManager.countFilesUnderPath(projectId, repoName, path)
        val remoteTotalCount = countRemoteFilesUnderPath(clusterInfo, cluster.projectId, cluster.repoName, path)

        val localChildrenPage = localDataManager.listDirectChildren(
            projectId, repoName, path, safePageNumber, safePageSize
        )
        val remoteChildrenPage = fetchRemoteDirectChildren(
            clusterInfo, cluster.projectId, cluster.repoName, path, safePageNumber, safePageSize
        )

        val childDiffs = compareChildren(
            projectId, repoName, path, localChildrenPage, remoteChildrenPage,
            cluster, clusterInfo, onlyDiff
        )

        val totalChildren = maxOf(localChildrenPage.totalRecords, remoteChildrenPage.totalRecords)
        val hasMore = localChildrenPage.hasMore || remoteChildrenPage.hasMore
        val isConsistent = localTotalCount == remoteTotalCount
            && childDiffs.all { it.diffStatus == PathDiffStatus.CONSISTENT }

        logger.info(
            "Federation path diff for [${clusterInfo.name}] at [$path]: " +
                "localTotal=$localTotalCount, remoteTotal=$remoteTotalCount, " +
                "page=$safePageNumber/$safePageSize, children=${childDiffs.size}"
        )

        return FederationPathDiff(
            federationId = federationId,
            remoteClusterId = cluster.clusterId,
            remoteClusterName = clusterInfo.name,
            path = path,
            localTotalCount = localTotalCount,
            remoteTotalCount = remoteTotalCount,
            consistent = isConsistent,
            children = childDiffs,
            pageNumber = safePageNumber,
            pageSize = safePageSize,
            totalChildren = totalChildren,
            hasMore = hasMore
        )
    }

    private fun compareChildren(
        projectId: String,
        repoName: String,
        parentPath: String,
        localPage: DirectChildrenPage,
        remotePage: DirectChildrenPage,
        cluster: FederatedCluster,
        clusterInfo: ClusterNodeInfo,
        onlyDiff: Boolean
    ): List<PathChildDiff> {
        val localMap = localPage.records.associateBy { it.fullPath }
        val remoteMap = remotePage.records.associateBy { it.fullPath }
        val allPaths = (localMap.keys + remoteMap.keys).distinct().sorted()

        return allPaths.mapNotNull { childPath ->
            val localChild = localMap[childPath]
            val remoteChild = remoteMap[childPath]

            val diffInfo = when {
                remoteChild == null && localChild != null ->
                    createLocalOnlyDiff(projectId, repoName, localChild)

                localChild == null && remoteChild != null ->
                    createRemoteOnlyDiff(remoteChild, cluster, clusterInfo)

                localChild != null && remoteChild != null ->
                    compareBothExist(projectId, repoName, localChild, remoteChild, cluster, clusterInfo, childPath)

                else -> null
            }

            if (onlyDiff && diffInfo?.diffStatus == PathDiffStatus.CONSISTENT) null else diffInfo
        }
    }

    private fun createLocalOnlyDiff(projectId: String, repoName: String, child: DirectChildInfo): PathChildDiff {
        val count = if (child.folder) {
            localDataManager.countFilesUnderPath(projectId, repoName, child.fullPath)
        } else 1L

        return PathChildDiff(
            fullPath = child.fullPath,
            folder = child.folder,
            diffStatus = PathDiffStatus.LOCAL_ONLY,
            localCount = count,
            remoteCount = 0,
            size = if (!child.folder) child.size else null,
            localSha256 = child.sha256
        )
    }

    private fun createRemoteOnlyDiff(
        child: DirectChildInfo,
        cluster: FederatedCluster,
        clusterInfo: ClusterNodeInfo
    ): PathChildDiff {
        val count = if (child.folder) {
            countRemoteFilesUnderPath(clusterInfo, cluster.projectId, cluster.repoName, child.fullPath)
        } else 1L

        return PathChildDiff(
            fullPath = child.fullPath,
            folder = child.folder,
            diffStatus = PathDiffStatus.REMOTE_ONLY,
            localCount = 0,
            remoteCount = count,
            size = if (!child.folder) child.size else null,
            remoteSha256 = child.sha256
        )
    }

    private fun compareBothExist(
        projectId: String,
        repoName: String,
        localChild: DirectChildInfo,
        remoteChild: DirectChildInfo,
        cluster: FederatedCluster,
        clusterInfo: ClusterNodeInfo,
        childPath: String
    ): PathChildDiff {
        return when {
            localChild.folder && remoteChild.folder ->
                compareFolders(projectId, repoName, localChild, remoteChild, cluster, clusterInfo, childPath)

            !localChild.folder && !remoteChild.folder -> compareFiles(localChild, remoteChild)
            else -> compareTypeMismatch(projectId, repoName, localChild, remoteChild, cluster, clusterInfo, childPath)
        }
    }

    private fun compareFolders(
        projectId: String,
        repoName: String,
        localChild: DirectChildInfo,
        remoteChild: DirectChildInfo,
        cluster: FederatedCluster,
        clusterInfo: ClusterNodeInfo,
        childPath: String
    ): PathChildDiff {
        val localCount = localDataManager.countFilesUnderPath(projectId, repoName, childPath)
        val remoteCount = countRemoteFilesUnderPath(clusterInfo, cluster.projectId, cluster.repoName, childPath)
        val status = if (localCount == remoteCount) PathDiffStatus.CONSISTENT else PathDiffStatus.COUNT_MISMATCH

        return PathChildDiff(
            fullPath = localChild.fullPath,
            folder = true,
            diffStatus = status,
            localCount = localCount,
            remoteCount = remoteCount
        )
    }

    private fun compareFiles(localChild: DirectChildInfo, remoteChild: DirectChildInfo): PathChildDiff {
        val status = if (localChild.sha256 == remoteChild.sha256) {
            PathDiffStatus.CONSISTENT
        } else {
            PathDiffStatus.CONTENT_MISMATCH
        }

        return PathChildDiff(
            fullPath = localChild.fullPath,
            folder = false,
            diffStatus = status,
            localCount = 1,
            remoteCount = 1,
            size = localChild.size,
            localSha256 = localChild.sha256,
            remoteSha256 = remoteChild.sha256
        )
    }

    private fun compareTypeMismatch(
        projectId: String,
        repoName: String,
        localChild: DirectChildInfo,
        remoteChild: DirectChildInfo,
        cluster: FederatedCluster,
        clusterInfo: ClusterNodeInfo,
        childPath: String
    ): PathChildDiff {
        return PathChildDiff(
            fullPath = localChild.fullPath,
            folder = localChild.folder,
            diffStatus = PathDiffStatus.CONTENT_MISMATCH,
            localCount = if (localChild.folder) {
                localDataManager.countFilesUnderPath(projectId, repoName, childPath)
            } else 1,
            remoteCount = if (remoteChild.folder) {
                countRemoteFilesUnderPath(clusterInfo, cluster.projectId, cluster.repoName, childPath)
            } else 1,
            localSha256 = localChild.sha256,
            remoteSha256 = remoteChild.sha256
        )
    }

    private fun comparePathStatsRecursive(localStats: PathStatsResult, remoteStats: PathStatsResult?): PathDiffStats {
        if (remoteStats == null) {
            return PathDiffStats(
                path = localStats.path,
                folder = localStats.folder,
                localStats = PathStatistics(localStats.fileCount, localStats.totalSize, localStats.aggregateHash),
                remoteStats = null,
                diffStatus = PathDiffStatus.LOCAL_ONLY,
                children = localStats.children?.map { comparePathStatsRecursive(it, null) }
            )
        }

        // 早停优化
        if (localStats.aggregateHash == remoteStats.aggregateHash && localStats.fileCount == remoteStats.fileCount) {
            return PathDiffStats(
                path = localStats.path,
                folder = localStats.folder,
                localStats = PathStatistics(localStats.fileCount, localStats.totalSize, localStats.aggregateHash),
                remoteStats = PathStatistics(remoteStats.fileCount, remoteStats.totalSize, remoteStats.aggregateHash),
                diffStatus = PathDiffStatus.CONSISTENT,
                children = null
            )
        }

        val diffStatus = when {
            localStats.fileCount != remoteStats.fileCount -> PathDiffStatus.COUNT_MISMATCH
            localStats.aggregateHash != remoteStats.aggregateHash -> PathDiffStatus.CONTENT_MISMATCH
            else -> PathDiffStatus.CONSISTENT
        }

        val childDiffs = if (diffStatus != PathDiffStatus.CONSISTENT &&
            (localStats.children != null || remoteStats.children != null)) {
            compareChildrenStats(localStats.children, remoteStats.children)
        } else null

        return PathDiffStats(
            path = localStats.path,
            folder = localStats.folder,
            localStats = PathStatistics(localStats.fileCount, localStats.totalSize, localStats.aggregateHash),
            remoteStats = PathStatistics(remoteStats.fileCount, remoteStats.totalSize, remoteStats.aggregateHash),
            diffStatus = diffStatus,
            children = childDiffs
        )
    }

    private fun compareChildrenStats(
        localChildren: List<PathStatsResult>?,
        remoteChildren: List<PathStatsResult>?
    ): List<PathDiffStats> {
        val localMap = localChildren?.associateBy { it.path } ?: emptyMap()
        val remoteMap = remoteChildren?.associateBy { it.path } ?: emptyMap()
        val allPaths = (localMap.keys + remoteMap.keys).distinct().sorted()

        return allPaths.map { path ->
            val local = localMap[path]
            val remote = remoteMap[path]

            when {
                local != null && remote != null -> comparePathStatsRecursive(local, remote)
                local != null -> comparePathStatsRecursive(local, null)
                else -> createRemoteOnlyStats(remote!!)
            }
        }
    }

    private fun createRemoteOnlyStats(remote: PathStatsResult): PathDiffStats {
        return PathDiffStats(
            path = remote.path,
            folder = remote.folder,
            localStats = null,
            remoteStats = PathStatistics(remote.fileCount, remote.totalSize, remote.aggregateHash),
            diffStatus = PathDiffStatus.REMOTE_ONLY,
            children = remote.children?.map { createRemoteOnlyStats(it) }
        )
    }

    private fun collectInconsistentPaths(stats: PathDiffStats): List<String> {
        if (stats.diffStatus == PathDiffStatus.CONSISTENT) return emptyList()

        return if (stats.children.isNullOrEmpty() || !stats.folder) {
            listOf(stats.path)
        } else {
            val childPaths = stats.children!!.flatMap { collectInconsistentPaths(it) }
            childPaths.ifEmpty { listOf(stats.path) }
        }
    }

    private fun filterClusters(clusters: List<FederatedCluster>, targetClusterId: String?): List<FederatedCluster> {
        val filtered = targetClusterId?.let { clusters.filter { it.clusterId == targetClusterId } } ?: clusters
        if (filtered.isEmpty()) {
            throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, "No cluster found for comparison")
        }
        return filtered
    }

    // ==================== 远程调用方法 ====================

    private fun fetchRemotePathStats(
        clusterInfo: ClusterNodeInfo,
        projectId: String,
        repoName: String,
        path: String,
        depth: Int
    ): PathStatsResult? {
        return try {
            val remoteCluster = buildClusterInfo(clusterInfo)
            val artifactReplicaClient = FeignClientFactory.create<ArtifactReplicaClient>(remoteCluster)
            val request = PathStatsRequest(projectId, repoName, path, depth)
            artifactReplicaClient.getPathStats(request).data
        } catch (e: Exception) {
            logger.warn("Failed to fetch remote path stats: ${e.message}")
            null
        }
    }

    private fun fetchRemoteDirectChildren(
        clusterInfo: ClusterNodeInfo,
        projectId: String,
        repoName: String,
        parentPath: String,
        pageNumber: Int,
        pageSize: Int
    ): DirectChildrenPage {
        val remoteCluster = buildClusterInfo(clusterInfo)
        val artifactReplicaClient = FeignClientFactory.create<ArtifactReplicaClient>(remoteCluster)
        val request = DirectChildrenRequest(projectId, repoName, parentPath, pageNumber, pageSize)
        return artifactReplicaClient.listDirectChildren(request).data ?: DirectChildrenPage(
            records = emptyList(),
            pageNumber = pageNumber,
            pageSize = pageSize,
            totalRecords = 0,
            hasMore = false
        )
    }

    private fun countRemoteFilesUnderPath(
        clusterInfo: ClusterNodeInfo,
        projectId: String,
        repoName: String,
        path: String
    ): Long {
        val remoteCluster = buildClusterInfo(clusterInfo)
        val artifactReplicaClient = FeignClientFactory.create<ArtifactReplicaClient>(remoteCluster)
        val request = PathCountRequest(projectId, repoName, path)
        return artifactReplicaClient.countFilesUnderPath(request).data ?: 0
    }

    private fun countRemoteNodes(
        clusterInfo: ClusterNodeInfo,
        projectId: String,
        repoName: String,
        rootPath: String
    ): Long {
        val remoteCluster = buildClusterInfo(clusterInfo)
        val artifactReplicaClient = FeignClientFactory.create<ArtifactReplicaClient>(remoteCluster)
        val request = NodeCountRequest(projectId, repoName, rootPath)
        return artifactReplicaClient.countNodes(request).data?.fileCount ?: 0
    }

    private fun countRemotePackages(
        clusterInfo: ClusterNodeInfo,
        projectId: String,
        repoName: String
    ): Long {
        return try {
            val remoteCluster = buildClusterInfo(clusterInfo)
            val artifactReplicaClient = FeignClientFactory.create<ArtifactReplicaClient>(remoteCluster)
            artifactReplicaClient.countPackages(projectId, repoName).data ?: 0
        } catch (e: Exception) {
            logger.warn("Failed to count remote packages: ${e.message}")
            0
        }
    }

    private fun fetchRemotePackages(
        clusterInfo: ClusterNodeInfo,
        projectId: String,
        repoName: String,
        pageNumber: Int,
        pageSize: Int
    ): List<Any> {
        return try {
            val remoteCluster = buildClusterInfo(clusterInfo)
            val artifactReplicaClient = FeignClientFactory.create<ArtifactReplicaClient>(remoteCluster)
            artifactReplicaClient.listPackages(projectId, repoName, pageNumber, pageSize).data ?: emptyList()
        } catch (e: Exception) {
            logger.warn("Failed to fetch remote packages: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FederationDiffManager::class.java)
    }
}

