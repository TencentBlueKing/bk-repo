package com.tencent.bkrepo.media.job.service

import com.google.gson.JsonSyntaxException
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.media.common.dao.MediaTranscodeJobDao
import io.kubernetes.client.informer.ResourceEventHandler
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.models.V1Job
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component


@Component
class TranscodeJobEventHandler @Autowired constructor(
    private val apiClient: ApiClient,
) : ResourceEventHandler<V1Job?> {

    @Autowired
    lateinit var nodeService: NodeService

    override fun onAdd(job: V1Job?) {
        logger.info("k8s job ADDED: ${job?.metadata?.name}")
    }

    override fun onUpdate(
        oldJob: V1Job?,
        newJob: V1Job?
    ) {
        // 避免因 Informer 重新同步而触发不必要的更新逻辑
        if (oldJob?.metadata?.resourceVersion == newJob?.metadata?.resourceVersion) {
            return
        }
        logger.info("k8s job UPDATED: ${newJob?.metadata?.name}")
        handleJobStatus(newJob ?: return)
    }

    override fun onDelete(job: V1Job?, deletedFinalStateUnknown: Boolean) {
        logger.info("k8s job DELETED:${job?.metadata?.name}")
    }

    private fun handleJobStatus(job: V1Job) {
        val jobName = job.metadata?.name ?: return
        val jobNamespace = job.metadata?.namespace ?: return
        val conditions = job.status?.conditions ?: return
        val labels = job.metadata?.labels

        for (condition in conditions) {
            if (condition.type == "Complete" && condition.status == "True") {
                logger.info("k8s job $jobName completed successfully. Deleting it...")
                deleteJobs(jobName, jobNamespace, labels)
                break
            }

            if (condition.type == "Failed" && condition.status == "True") {
                val reason = condition.reason ?: "NoReason"
                val message = condition.message ?: "NoMessage"
                // 如果转码后文件已经上传成功就放弃报错，直接删除
                if (checkTranscodeFileDone(labels)) {
                    deleteJobs(jobName, jobNamespace, labels)
                    return
                }
                logger.error("k8s job $jobName failed. Reason: $reason, Message: $message")
                break
            }
        }
    }

    private fun deleteJobs(
        jobName: String,
        jobNamespace: String,
        labels: Map<String, String>?
    ) {
        deleteJob(jobName, jobNamespace)
        // 有标签的在成功时删除所有jobId相同的任务
        val jobId = labels?.get(TranscodeJobService.TRANSCODE_JOB_ID_LABEL)
        if (jobId?.isNotBlank() == true) {
            deleteJobByTag(jobId, jobNamespace)
        }
    }

    private fun deleteJob(jobName: String, namespace: String) {
        try {
            BatchV1Api(apiClient).deleteNamespacedJob(
                jobName,
                namespace,
                null,
                null,
                null,
                null,
                "Background",
                null,
            )
            logger.info("k8s job $jobName deletion initiated.")
        } catch (e: ApiException) {
            // K8s API 在删除成功时可能返回一个非 JSON 的状态体，导致 GSON 解析失败。
            // 这是一个已知的、可接受的行为，我们通常忽略这种特定的异常。
            // Kotlin 的 'is' 关键字用于类型检查，等同于 Java 的 'instanceof'。
            if (e.cause !is JsonSyntaxException) {
                logger.error("k8s job error deleting job $jobName", e)
            }
        }
    }

    private fun deleteJobByTag(jobId: String, namespace: String) {
        val labelSelector = "${TranscodeJobService.TRANSCODE_JOB_ID_LABEL}=$jobId"
        try {
            BatchV1Api(apiClient).deleteCollectionNamespacedJob(
                /* namespace = */ namespace,
                /* pretty = */ null,
                /* _continue = */ null,
                /* dryRun = */ null,
                /* fieldSelector = */ null,
                /* gracePeriodSeconds = */ 0,
                /* labelSelector = */ labelSelector,
                /* limit = */ null,
                /* orphanDependents = */ null,
                /* propagationPolicy = */ "Background",
                /* resourceVersion = */ null,
                /* resourceVersionMatch = */ null,
                /* sendInitialEvents = */ null,
                /* timeoutSeconds = */ null,
                /* body = */ null,
            )
            logger.info("k8s job $labelSelector deletion initiated.")
        } catch (e: ApiException) {
            // K8s API 在删除成功时可能返回一个非 JSON 的状态体，导致 GSON 解析失败。
            // 这是一个已知的、可接受的行为，我们通常忽略这种特定的异常。
            // Kotlin 的 'is' 关键字用于类型检查，等同于 Java 的 'instanceof'。
            if (e.cause !is JsonSyntaxException) {
                logger.error("k8s job error deleting job $labelSelector", e)
            }
        }
    }

    private fun checkTranscodeFileDone(labels: Map<String, String>?): Boolean {
        if (labels.isNullOrEmpty()) {
            return false
        }
        try {
            val projectId = labels[TranscodeJobService.TRANSCODE_JOB_PROJECT_ID] ?: return false
            val repoName = labels[TranscodeJobService.TRANSCODE_JOB_REPO_NAME] ?: return false
            var fileName = labels[TranscodeJobService.TRANSCODE_JOB_FILE_NAME] ?: return false
            fileName = "/streams/${fileName.replace(".mp4", "_1280x720.mp4")}"
            nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fileName)) ?: run {
                logger.warn("checkTranscodeFileDone not found: $projectId|$repoName|$fileName")
                return false
            }
            logger.info("checkTranscodeFileDone found: $projectId|$repoName|$fileName")
            return true
        } catch (e: Exception) {
            logger.error("checkTranscodeFileDone error", e)
            return false
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(TranscodeJobEventHandler::class.java)
    }
}