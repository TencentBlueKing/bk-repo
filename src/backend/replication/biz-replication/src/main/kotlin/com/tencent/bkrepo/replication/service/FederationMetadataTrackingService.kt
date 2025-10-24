package com.tencent.bkrepo.replication.service

/**
 * 联邦元数据跟踪服务接口
 */
interface FederationMetadataTrackingService {

    /**
     * 创建文件传输跟踪记录
     */
    fun createTrackingRecord(
        taskKey: String,
        remoteClusterId: String,
        projectId: String,
        localRepoName: String,
        remoteProjectId: String,
        remoteRepoName: String,
        nodePath: String,
        nodeId: String
    )

    /**
     * 删除指定任务key和节点ID的记录
     */
    fun deleteByTaskKeyAndNodeId(taskKey: String, nodeId: String)

    /**
     * 处理未完成的文件传输
     * 返回成功处理的记录数量
     */
    fun processPendingFileTransfers(): Int

    /**
     * 清理过期的失败记录
     * 根据配置的最大重试次数和保留天数，删除符合条件的失败记录
     */
    fun cleanExpiredFailedRecords(): Long
}
