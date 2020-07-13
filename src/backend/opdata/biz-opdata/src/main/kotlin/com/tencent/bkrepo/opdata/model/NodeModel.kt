package com.tencent.bkrepo.opdata.model

import com.tencent.bkrepo.opdata.pojo.RepoMetrics
import com.tencent.bkrepo.repository.api.NodeResource
import java.lang.Exception
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class NodeModel @Autowired constructor(
    private val nodeResource: NodeResource
) {
    fun getNodeSize(projectId: String, repoName: String): RepoMetrics {
        try {
            val result = nodeResource.computeSize(projectId, repoName, "/").data ?: return RepoMetrics(repoName, 0L, 0L)
            return RepoMetrics(repoName, result.size, result.subNodeCount)
        } catch (ignored: Exception) {
            return RepoMetrics(repoName, 0L, 0L)
        }
    }
}
