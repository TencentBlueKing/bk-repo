package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.model.TDriveBlockNode
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.gt
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.lt
import org.springframework.data.mongodb.core.query.where
import java.time.LocalDateTime

object DriveBlockNodeQueryHelper {

    fun listQuery(
        nodeId: String,
        createdDate: String,
        range: Range?,
    ): Query {
        val criteria = nodeIdCriteria(nodeId)
            .and(TDriveBlockNode::createdDate).gt(LocalDateTime.parse(createdDate))
        range?.let {
            criteria.norOperator(
                TDriveBlockNode::startPos.gt(it.end),
                TDriveBlockNode::endPos.lt(it.start),
            )
        }
        return Query(criteria).with(Sort.by(TDriveBlockNode::createdDate.name))
    }

    fun nodeIdCriteria(nodeId: String): Criteria {
        return where(TDriveBlockNode::nodeId).isEqualTo(nodeId)
            .and(TDriveBlockNode::deleted).isEqualTo(null)
    }

    fun deletedCriteria(
        nodeId: String,
        nodeCreateDate: LocalDateTime,
        nodeDeleteDate: LocalDateTime,
    ): Criteria {
        return where(TDriveBlockNode::nodeId).isEqualTo(nodeId)
            .and(TDriveBlockNode::createdDate).gt(nodeCreateDate).lt(nodeDeleteDate)
    }

    fun deleteUpdate(): Update {
        return Update().set(TDriveBlockNode::deleted.name, LocalDateTime.now())
    }

    fun restoreUpdate(): Update {
        return Update().set(TDriveBlockNode::deleted.name, null)
    }

    fun findBlockCriteria(
        nodeId: String,
        startPos: Long,
        sha256: String,
        deleted: LocalDateTime?,
    ): Criteria {
        return where(TDriveBlockNode::nodeId).isEqualTo(nodeId)
            .and(TDriveBlockNode::sha256).isEqualTo(sha256)
            .and(TDriveBlockNode::startPos).isEqualTo(startPos)
            .and(TDriveBlockNode::deleted).isEqualTo(deleted)
    }
}
