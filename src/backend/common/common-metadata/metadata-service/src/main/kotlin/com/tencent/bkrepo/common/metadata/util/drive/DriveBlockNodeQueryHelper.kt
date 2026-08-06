package com.tencent.bkrepo.common.metadata.util.drive

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.model.drive.TDriveBlockNode
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo

object DriveBlockNodeQueryHelper {

    fun listBlocksQuery(
        range: Range,
        projectId: String,
        repoName: String,
        ino: Long,
        snapSeq: Long? = null,
    ): Query {
        val criteria = if (snapSeq != null) {
            snapCriteria(projectId, repoName, ino, snapSeq)
        } else {
            curSnapCriteria(projectId, repoName, ino)
        }
        criteria.and(TDriveBlockNode::startPos.name).lte(range.end)
            .and(TDriveBlockNode::endPos.name).gte(range.start)
        return Query(criteria).with(Sort.by(TDriveBlockNode::createdDate.name))
    }

    fun curSnapCriteria(projectId: String, repoName: String, ino: Long): Criteria {
        return Criteria.where(TDriveBlockNode::ino.name).`is`(ino)
            .and(TDriveBlockNode::projectId.name).isEqualTo(projectId)
            .and(TDriveBlockNode::repoName.name).isEqualTo(repoName)
    }

    fun snapCriteria(projectId: String, repoName: String, ino: Long, snapSeq: Long): Criteria {
        return Criteria.where(TDriveBlockNode::ino.name).`is`(ino)
            .and(TDriveBlockNode::projectId.name).isEqualTo(projectId)
            .and(TDriveBlockNode::repoName.name).isEqualTo(repoName)
            .and(TDriveBlockNode::snapSeq.name).lte(snapSeq)
    }
}
