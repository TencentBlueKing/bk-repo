package com.tencent.bkrepo.common.metadata.util.drive

import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where

object DriveNodeDaoHelper {

    fun currentParentNameCriteria(projectId: String, repoName: String, parent: Long, name: String): Criteria {
        return listChildrenCriteria(projectId, repoName, parent).and(TDriveNode::name.name).isEqualTo(name)
    }

    fun listChildrenCriteria(
        projectId: String,
        repoName: String,
        parent: Long? = null,
        snapSeq: Long? = null,
    ): Criteria {
        val criteria = where(TDriveNode::projectId).isEqualTo(projectId)
            .and(TDriveNode::repoName.name).isEqualTo(repoName)
        parent?.let { criteria.and(TDriveNode::parent.name).isEqualTo(it) }
        return if (snapSeq == null) {
            criteria.and(TDriveNode::deleteSnapSeq.name).isEqualTo(Long.MAX_VALUE)
                .and(TDriveNode::deleted.name).isEqualTo(null)
        } else {
            criteria.and(TDriveNode::snapSeq.name).lte(snapSeq)
                .and(TDriveNode::deleteSnapSeq.name).gt(snapSeq)
        }
    }
}
