package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.fs.server.constant.UPLOADID_KEY
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import java.time.LocalDateTime

object BlockNodeUploadId {

    const val KEY = UPLOADID_KEY

    /**
     * 集群分发未 finish 块的过期天数。成功 finish 会清空 expireDate。
     * ponytail: 7 天上限，分发超过此时长仍未 finish 会被 Job 清掉。
     */
    const val REPLICA_BLOCK_EXPIRE_DAYS = 7L

    fun replicaBlockExpireDate(): LocalDateTime = LocalDateTime.now().plusDays(REPLICA_BLOCK_EXPIRE_DAYS)

    fun replaceMetadata(metadata: List<MetadataModel>?, uploadId: String): List<MetadataModel> {
        val others = metadata.orEmpty().filterNot { it.key == KEY }
        return others + MetadataModel(key = KEY, value = uploadId, system = true)
    }

    /**
     * 分发会话按源 node._id 固定，同一文件重试才能 finish 到首次已写入的块。
     */
    fun replicaSession(nodeId: String?): String = "replica/${nodeId.orEmpty()}"

    /**
     * 仅当目标 node 已带同一会话才允许 finish。旧源端 uniqueId 先 finish 再覆盖 node 时对不上，
     * 跳过以免 NODE_DELETED listener 把刚完成的块当旧版本删掉。
     */
    fun finishSession(nodeUploadId: String?, requestUploadId: String): String? {
        if (nodeUploadId.isNullOrEmpty() || requestUploadId.isEmpty()) return null
        return requestUploadId.takeIf { it == nodeUploadId }
    }
}
