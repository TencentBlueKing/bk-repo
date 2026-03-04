package com.tencent.bkrepo.fs.server.utils

import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import java.time.LocalDateTime

object DriveNodeQueryHelper {

    /**
     * 节点属性修改是否需要走 COW（写时复制）：
     * 当目标记录不属于当前快照时，需创建同 ino 新副本后再修改，
     * 并将旧记录标记删除。
     */
    fun needCowForModify(nodeSnapSeq: Long, currentSnapSeq: Long): Boolean {
        return nodeSnapSeq != currentSnapSeq
    }


    /**
     * 基于旧版本节点构造 COW 新副本。
     *
     * 新副本保持相同 ino，重置文档主键，挂到当前快照序列号。
     */
    fun buildCowNode(
        origin: TDriveNode,
        currentSnapSeq: Long,
        operator: String,
        now: LocalDateTime = LocalDateTime.now(),
    ): TDriveNode {
        return origin.copy(
            id = null,
            createdBy = operator,
            createdDate = now,
            lastModifiedBy = operator,
            lastModifiedDate = now,
            deleted = null,
            snapSeq = currentSnapSeq,
            deleteSnapSeq = Long.MAX_VALUE,
        )
    }

    /**
     * 查当前活跃节点
     */
    fun currentCriteria(projectId: String, repoName: String, parent: String, name: String): Criteria {
        return snapshotCriteria(projectId, repoName, parent, name)
    }

    /**
     * 查快照视图节点
     *
     * 条件：snapSeq <= targetSnapSeq AND deleteSnapSeq > targetSnapSeq
     */
    fun snapshotCriteria(
        projectId: String, repoName: String, parent: String, name: String, snapSeq: Long? = null
    ): Criteria {
        return listChildrenCriteria(projectId, repoName, parent, snapSeq).and(TDriveNode::name).isEqualTo(name)
    }

    /**
     * 列出子节点
     *
     * @param snapSeq 快照序列号，null 时查当前视图
     */
    fun listChildrenCriteria(
        projectId: String,
        repoName: String,
        parent: String,
        snapSeq: Long? = null,
    ): Criteria {
        return if (snapSeq == null) {
            where(TDriveNode::projectId).isEqualTo(projectId)
                .and(TDriveNode::repoName.name).isEqualTo(repoName)
                .and(TDriveNode::parent).isEqualTo(parent)
                .and(TDriveNode::deleteSnapSeq).isEqualTo(Long.MAX_VALUE)
                .and(TDriveNode::deleted).isEqualTo(null)
        } else {
            where(TDriveNode::projectId).isEqualTo(projectId)
                .and(TDriveNode::repoName).isEqualTo(repoName)
                .and(TDriveNode::parent).isEqualTo(parent)
                .and(TDriveNode::snapSeq).lte(snapSeq)
                .and(TDriveNode::deleteSnapSeq).gt(snapSeq)
        }
    }

    /**
     * 标记节点删除
     */
    fun deleteUpdate(snapSeq: Long): Update {
        return Update()
            .set(TDriveNode::deleteSnapSeq.name, snapSeq)
            .set(TDriveNode::deleted.name, LocalDateTime.now())
    }

    /**
     * 恢复已删除节点
     */
    fun restoreUpdate(): Update {
        return Update()
            .set(TDriveNode::deleteSnapSeq.name, Long.MAX_VALUE)
            .set(TDriveNode::deleted.name, null)
    }
}
