package com.tencent.bkrepo.media.job.service

import com.google.gson.JsonSyntaxException
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

        for (condition in conditions) {
            if (condition.type == "Complete" && condition.status == "True") {
                logger.info("k8s job $jobName completed successfully. Deleting it...")
                deleteJob(jobName, jobNamespace)
                break
            }

            if (condition.type == "Failed" && condition.status == "True") {
                val reason = condition.reason ?: "NoReason"
                val message = condition.message ?: "NoMessage"
                logger.error("k8s job $jobName failed. Reason: $reason, Message: $message")
                break
            }
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


    companion object {
        private val logger: Logger = LoggerFactory.getLogger(TranscodeJobEventHandler::class.java)
    }
}