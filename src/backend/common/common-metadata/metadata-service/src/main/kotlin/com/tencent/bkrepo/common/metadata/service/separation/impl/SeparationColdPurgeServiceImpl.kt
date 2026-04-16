package com.tencent.bkrepo.common.metadata.service.separation.impl

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.metadata.config.DataSeparationConfig
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationNodeDao
import com.tencent.bkrepo.common.metadata.service.separation.SeparationColdPurgeService
import com.tencent.bkrepo.common.metadata.util.SeparationUtils
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class SeparationColdPurgeServiceImpl(
    private val dataSeparationConfig: DataSeparationConfig,
    private val separationNodeDao: SeparationNodeDao,
) : SeparationColdPurgeService {

    override fun markColdDeletedAfterHotPathSoftDelete(
        projectId: String,
        repoName: String,
        hotDeleteRootFullPaths: Collection<String>,
        deletedAt: LocalDateTime,
    ) {
        if (!isCoManaged(projectId, repoName) || hotDeleteRootFullPaths.isEmpty()) return
        markColdSubtreesForPaths(projectId, repoName, hotDeleteRootFullPaths, deletedAt)
    }

    override fun markColdDeletedAfterHotNodeClean(
        projectId: String,
        repoName: String,
        cleanScopeRootFullPath: String,
        cleanBeforeDate: LocalDateTime,
        deletedAt: LocalDateTime,
    ) {
        if (!isCoManaged(projectId, repoName)) return
        val root = PathUtils.normalizeFullPath(cleanScopeRootFullPath)
        if (root.isEmpty()) return
        separationNodeDao.markDeletedColdMatchingHotNodeClean(
            projectId,
            repoName,
            cleanScopeRootFullPath,
            cleanBeforeDate,
            deletedAt,
        )
    }

    override fun markColdDeletedByApiPathDelete(
        projectId: String,
        repoName: String,
        coldDeleteRootFullPaths: Collection<String>,
        deletedAt: LocalDateTime,
    ) {
        checkManualPurgeAllowed(projectId, repoName)
        if (coldDeleteRootFullPaths.isEmpty()) return
        markColdSubtreesForPaths(projectId, repoName, coldDeleteRootFullPaths, deletedAt)
    }

    override fun markColdDeletedByApiNodeClean(
        projectId: String,
        repoName: String,
        cleanScopeRootFullPath: String,
        cleanBeforeDate: LocalDateTime,
        deletedAt: LocalDateTime,
    ) {
        checkManualPurgeAllowed(projectId, repoName)
        val root = PathUtils.normalizeFullPath(cleanScopeRootFullPath)
        if (root.isEmpty()) return
        separationNodeDao.markDeletedColdMatchingHotNodeClean(
            projectId,
            repoName,
            cleanScopeRootFullPath,
            cleanBeforeDate,
            deletedAt,
        )
    }

    private fun isCoManaged(projectId: String, repoName: String): Boolean {
        val key = "$projectId/$repoName"
        return SeparationUtils.matchesConfigRepos(key, dataSeparationConfig.coldCoManagedRepos)
    }

    private fun checkManualPurgeAllowed(projectId: String, repoName: String) {
        if (dataSeparationConfig.enableManualColdPurgeByApi) {
            return
        }
        throw ErrorCodeException(
            CommonMessageCode.METHOD_NOT_ALLOWED,
            "cold purge api is disabled for $projectId/$repoName",
        )
    }

    private fun markColdSubtreesForPaths(
        projectId: String,
        repoName: String,
        normalizedFullPaths: Collection<String>,
        deletedAt: LocalDateTime,
    ) {
        normalizedFullPaths.forEach { fp ->
            val nf = PathUtils.normalizeFullPath(fp)
            val escaped = PathUtils.escapeRegex(nf)
            separationNodeDao.markDeletedColdSubtreeAllCollections(
                projectId,
                repoName,
                "^$escaped(/|\$)",
                deletedAt,
            )
        }
    }
}
