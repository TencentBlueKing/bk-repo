package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.model.TNode
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where

object NodeDeleteHelper {
    fun buildCriteria(
        projectId: String,
        repoName: String,
        fullPath: String,
    ): Criteria {
        val normalizedFullPath = PathUtils.normalizeFullPath(fullPath)
        val normalizedPath = PathUtils.toPath(normalizedFullPath)
        val escapedPath = PathUtils.escapeRegex(normalizedPath)
        val criteria = where(TNode::projectId).isEqualTo(projectId)
            .and(TNode::repoName).isEqualTo(repoName)
            .and(TNode::deleted).isEqualTo(null)
            .orOperator(
                where(TNode::fullPath).regex("^$escapedPath"),
                where(TNode::fullPath).isEqualTo(normalizedFullPath)
            )
        return criteria
    }
}
