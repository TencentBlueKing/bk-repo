package com.tencent.bkrepo.replication.replica.replicator.base

import com.google.common.base.Throwables
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.retry
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.DELAY_IN_SECONDS
import com.tencent.bkrepo.replication.constant.RETRY_COUNT
import com.tencent.bkrepo.replication.enums.WayOfPushArtifact
import com.tencent.bkrepo.replication.exception.ArtifactPushException
import com.tencent.bkrepo.replication.replica.context.FilePushContext
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.replicator.Replicator
import com.tencent.bkrepo.replication.replica.replicator.base.internal.ClusterArtifactReplicationHandler
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import org.slf4j.LoggerFactory

/**
 * 抽象文件复制器基类
 */
abstract class AbstractFileReplicator(
    protected val artifactReplicationHandler: ClusterArtifactReplicationHandler,
    protected val replicationProperties: ReplicationProperties
) : Replicator {

    /**
     * 执行文件推送
     * @param context replication上下文
     * @param node 节点信息
     * @param logPrefix 日志前缀，用于区分不同的执行器
     * @param postPush 推送后处理逻辑
     * @return 是否执行了推送操作
     */
    protected fun executeFilePush(
        context: ReplicaContext,
        node: NodeInfo,
        logPrefix: String = "",
        postPush: (ReplicaContext, NodeInfo) -> Unit = { _, _ -> }
    ): Boolean {
        with(context) {
            var type: String = replicationProperties.pushType
            var downGrade = false
            val storageCredentialsKey = remoteRepo?.storageCredentials?.key

            return retry(times = RETRY_COUNT, delayInSeconds = DELAY_IN_SECONDS) { retry ->
                if (preCheck(this, node.sha256!!, storageCredentialsKey, remoteRepoType)) {
                    logger.info(
                        "${logPrefix}The file [${node.fullPath}] with sha256 [${node.sha256}] " +
                            "will be pushed to the remote server ${cluster.name}, try the $retry time!"
                    )

                    try {
                        performFilePush(context, node, type, downGrade)
                        postPush(context, node)
                        return@retry true
                    } catch (throwable: Throwable) {
                        handlePushException(throwable, logPrefix)

                        // 处理降级逻辑
                        if (shouldDowngrade(throwable)) {
                            type = WayOfPushArtifact.PUSH_WITH_DEFAULT.value
                            downGrade = true
                        }
                        throw throwable
                    }
                }
                false
            }
        }
    }


    private fun preCheck(
        context: ReplicaContext,
        sha256: String,
        storageKey: String?,
        repoType: RepositoryType
    ): Boolean =
        context.blobReplicaClient?.check(sha256 = sha256, storageKey = storageKey, repoType = repoType)?.data != true

    /**
     * 执行实际的文件推送操作
     */
    private fun performFilePush(
        context: ReplicaContext,
        node: NodeInfo,
        pushType: String,
        downGrade: Boolean
    ) {
        artifactReplicationHandler.blobPush(
            filePushContext = FilePushContext(
                context = context,
                name = node.fullPath,
                size = node.size,
                sha256 = node.sha256,
                md5 = node.md5,
                crc64ecma = node.crc64ecma,
            ),
            pushType = pushType,
            downGrade = downGrade
        )
    }

    /**
     * 处理推送异常
     */
    private fun handlePushException(throwable: Throwable, logPrefix: String) {
        logger.warn(
            "${logPrefix}File replica push error $throwable, trace is " +
                "${Throwables.getStackTraceAsString(throwable)}!"
        )
    }

    /**
     * 判断是否需要降级处理
     */
    private fun shouldDowngrade(throwable: Throwable): Boolean {
        return throwable is ArtifactPushException &&
            (throwable.code == HttpStatus.METHOD_NOT_ALLOWED.value ||
                throwable.code == HttpStatus.UNAUTHORIZED.value)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractFileReplicator::class.java)
    }
}