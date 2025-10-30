package com.tencent.bkrepo.replication.replica.replicator.base

import com.google.common.base.Throwables
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.retry
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.fs.server.constant.FS_ATTR_KEY
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.DELAY_IN_SECONDS
import com.tencent.bkrepo.replication.constant.RETRY_COUNT
import com.tencent.bkrepo.replication.enums.WayOfPushArtifact
import com.tencent.bkrepo.replication.exception.ArtifactPushException
import com.tencent.bkrepo.replication.manager.LocalDataManager.Companion.federatedSource
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
     *
     * @param context 复制上下文
     * @param node 节点信息，支持NodeInfo和TBlockNode两种类型
     * @param logPrefix 日志前缀，用于区分不同的执行器
     * @param postPush 推送后处理逻辑
     */
    protected fun <T> executeFilePush(
        context: ReplicaContext,
        node: T,
        logPrefix: String = "",
        postPush: (ReplicaContext, T) -> Unit = { _, _ -> },
        afterCompletion: (ReplicaContext, T) -> Unit = { _, _ -> }
    ) {
        with(context) {
            var type: String = replicationProperties.pushType
            var downGrade = false
            val storageCredentialsKey = remoteRepo?.storageCredentials?.key

            // 提取节点信息
            val (sha256, fullPath) = when (node) {
                is NodeInfo -> node.sha256 to node.fullPath
                is TBlockNode -> node.sha256 to node.nodeFullPath
                else -> throw IllegalArgumentException("Unsupported node type: ${node!!::class.simpleName}")
            }

            retry(times = RETRY_COUNT, delayInSeconds = DELAY_IN_SECONDS) { retry ->
                if (preCheck(this, sha256!!, storageCredentialsKey, remoteRepoType)) {
                    logger.info(
                        "${logPrefix}The file [$fullPath] with sha256 [$sha256] " +
                            "will be pushed to the remote server ${cluster.name}, try the $retry time!"
                    )

                    try {
                        performFilePush(context, node, type, downGrade)
                        postPush(context, node)
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
                afterCompletion(context, node)
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
    private fun <T> performFilePush(
        context: ReplicaContext,
        node: T,
        pushType: String,
        downGrade: Boolean
    ) {
        // 提取节点信息
        val nodeInfoData = when (node) {
            is NodeInfo -> NodeInfoData(
                name = node.fullPath,
                size = node.size,
                sha256 = node.sha256,
                md5 = node.md5,
                crc64ecma = node.crc64ecma
            )
            is TBlockNode -> NodeInfoData(
                name = node.nodeFullPath,
                size = node.size,
                sha256 = node.sha256,
                md5 = null,  // TBlockNode没有md5属性
                crc64ecma = node.crc64ecma
            )
            else -> throw IllegalArgumentException("Unsupported node type: ${node!!::class.simpleName}")
        }

        artifactReplicationHandler.blobPush(
            filePushContext = FilePushContext(
                context = context,
                name = nodeInfoData.name,
                size = nodeInfoData.size,
                sha256 = nodeInfoData.sha256,
                md5 = nodeInfoData.md5,
                crc64ecma = nodeInfoData.crc64ecma,
                federatedSource = federatedSource(node)
            ),
            pushType = pushType,
            downGrade = downGrade
        )
    }

    // 内部数据类用于封装节点信息
    private data class NodeInfoData(
        val name: String,
        val size: Long,
        val sha256: String?,
        val md5: String?,
        val crc64ecma: String?
    )

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

    protected fun unNormalNode(node: NodeInfo) = node.sha256 == FAKE_SHA256

    protected fun blockNode(node: NodeInfo) = unNormalNode(node)
        && node.nodeMetadata?.any { it.key == FS_ATTR_KEY } == true

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractFileReplicator::class.java)
    }
}