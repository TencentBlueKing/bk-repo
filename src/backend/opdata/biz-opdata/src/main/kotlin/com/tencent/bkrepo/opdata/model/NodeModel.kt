package com.tencent.bkrepo.opdata.model

import com.tencent.bkrepo.repository.api.NodeResource
import java.lang.Exception
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class NodeModel @Autowired constructor(
    private val nodeResource: NodeResource
) {
    fun getNodeSize(projectId: String, repoName: String): Long {
        try {
            val result = nodeResource.computeSize(projectId, repoName, "/").data ?: return 0
            return result.size
        } catch (e: Exception) {
            return 0L
        }
    }

    fun getNodeNum(projectId: String, repoName: String): Long {
        try {
            val result = nodeResource.list(projectId, repoName, "/", false, true).data ?: return 0L
            return result.size.toLong()
        } catch (e: Exception) {
            return 0L
        }
    }
}
