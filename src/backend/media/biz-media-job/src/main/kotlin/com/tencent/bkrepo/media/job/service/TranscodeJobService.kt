package com.tencent.bkrepo.media.job.service

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.media.common.dao.MediaTranscodeJobDao
import com.tencent.bkrepo.media.common.dao.TranscodeJobConfigDao
import com.tencent.bkrepo.media.job.k8s.K8sProperties
import com.tencent.bkrepo.media.job.k8s.limits
import com.tencent.bkrepo.media.job.k8s.requests
import com.tencent.bkrepo.media.common.model.TMediaTranscodeJob
import com.tencent.bkrepo.media.common.model.TMediaTranscodeJobConfig
import com.tencent.bkrepo.media.common.pojo.transcode.MediaTranscodeJobStatus
import com.tencent.bkrepo.media.common.pojo.transcode.TranscodeReportData
import com.tencent.bkrepo.media.job.pojo.ResourceLimit
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource
import io.kubernetes.client.openapi.models.V1Container
import io.kubernetes.client.openapi.models.V1EnvVar
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1JobSpec
import io.kubernetes.client.openapi.models.V1KeyToPath
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1PodSpec
import io.kubernetes.client.openapi.models.V1PodTemplateSpec
import io.kubernetes.client.openapi.models.V1ResourceRequirements
import io.kubernetes.client.openapi.models.V1Volume
import io.kubernetes.client.openapi.models.V1VolumeMount
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.ZoneOffset

@Service
class TranscodeJobService @Autowired constructor(
    private val apiClient: ApiClient,
    private val k8sProperties: K8sProperties,
    private val mediaTranscodeJobDao: MediaTranscodeJobDao,
    private val transcodeJobConfigDao: TranscodeJobConfigDao
) {
    @Async
    fun startJob(config: TMediaTranscodeJobConfig) {
        val job = mediaTranscodeJobDao.findAndQueueOldestWaitingJob() ?: run {
            logger.debug("no waiting job to run")
            return
        }

        // 存在独立项目的情况就使用独立项目配置替代默认配置
        val projectConfig = transcodeJobConfigDao.findOne(
            Query(where(TMediaTranscodeJobConfig::projectId).isEqualTo(job.projectId))
        ) ?: config

        var jobName = "${job.id}-${job.updateTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli()}".lowercase()
        if (jobName.length > 63) {
            jobName = jobName.take(63)
        }
        val api = CoreV1Api(apiClient)
        try {
            val configMapName = SCRIPT_CONFIG_MAP_NAME
            if (api.exec {
                    api.readNamespacedConfigMap(
                        configMapName,
                        k8sProperties.namespace,
                        null,
                        null,
                        null
                    )
                } == null) {
                logger.error("configmap ${k8sProperties.namespace}:$configMapName not found error")
                return
            }
            createK8sJob(BatchV1Api(apiClient), job.id ?: "", jobName, projectConfig, job)
        } catch (e: ApiException) {
            logger.error(e.buildMessage())
            throw e
        }

        mediaTranscodeJobDao.updateJobStatus(job.id!!, MediaTranscodeJobStatus.INIT)
    }

    private fun createK8sJob(
        api: BatchV1Api,
        jobId: String,
        jobName: String,
        config: TMediaTranscodeJobConfig,
        job: TMediaTranscodeJob
    ) {
        val metadata = V1ObjectMeta()
            .name(jobName)
            .namespace(k8sProperties.namespace)
            .labels(
                mapOf(
                    TRANSCODE_JOB_APP_LABEL_KEY to TRANSCODE_JOB_APP_LABEL_VALUE,
                    TRANSCODE_JOB_ID_LABEL to jobId,
                    TRANSCODE_JOB_PROJECT_ID to job.projectId,
                    TRANSCODE_JOB_REPO_NAME to job.repoName,
                    TRANSCODE_JOB_FILE_NAME to job.fileName,
                )
            )
        val resource = config.resource?.readJsonString<ResourceLimit>()
        val container = V1Container()
            .name("transcoder")
            .image(config.image)
            .command(PYTHON_CMD)
            .env(
                listOf(
                    V1EnvVar().name("MEDIA_JOB_JOB_PARAM").value(job.param),
                    V1EnvVar().name("MEDIA_JOB_ENABLE_COS").value("${config.cosConfigMapName != null}"),
                    V1EnvVar().name("MEDIA_JOB_PROJECT_ID").value(job.projectId),
                    V1EnvVar().name("MEDIA_JOB_WORKSPACE_NAME").value(job.repoName),
                )
            )
            .volumeMounts(genContainerMount(config))
            .resources(
                V1ResourceRequirements().apply {
                    limits(
                        cpu = resource?.limitCpu ?: k8sProperties.limit.limitCpu.toString(),
                        memory = resource?.limitMem ?: k8sProperties.limit.limitMem.toString(),
                        ephemeralStorage = resource?.limitStorage ?: k8sProperties.limit.limitStorage.toString(),
                    )
                    requests(
                        cpu = resource?.requestCpu ?: k8sProperties.limit.requestCpu.toString(),
                        memory = resource?.requestMem ?: k8sProperties.limit.requestMem.toString(),
                        ephemeralStorage = resource?.requestStorage ?: k8sProperties.limit.requestStorage.toString(),
                    )
                }
            )
        val podSpec = V1PodSpec()
            .restartPolicy("Never")
            .containers(listOf(container))
            .volumes(genVolume(config))
        val jobSpec = V1JobSpec()
            .backoffLimit(0)
            .ttlSecondsAfterFinished(60 * 60 * 24 * 3)
            .template(V1PodTemplateSpec().spec(podSpec))
        val kJob = V1Job()
            .apiVersion("batch/v1")
            .kind("Job")
            .metadata(metadata)
            .spec(jobSpec)
        api.createNamespacedJob(
            k8sProperties.namespace,
            kJob,
            null,
            null,
            null,
        )
        logger.info("created job $jobName in namespace ${k8sProperties.namespace}")
    }

    private fun genContainerMount(config: TMediaTranscodeJobConfig): List<V1VolumeMount> {
        val res = mutableListOf(
            V1VolumeMount()
                .name("script-volume")
                .mountPath("$WORK_SPACE/$CMD")
                .subPath(CMD)
                .readOnly(true)
        )

        // 存在 cos 则添加对于 cos 的挂载
        if (config.cosConfigMapName != null) {
            res.add(
                V1VolumeMount()
                    .name("cos")
                    .mountPath("/root/$COS_FILE")
                    .subPath(COS_FILE)
                    .readOnly(false),

                )
            res.add(
                V1VolumeMount()
                    .name("cos")
                    .mountPath("/etc/$COS_DNS_FILE")
                    .subPath(COS_DNS_FILE)
                    .readOnly(false)
            )
        }

        return res
    }

    private fun genVolume(config: TMediaTranscodeJobConfig): List<V1Volume> {
        val res = mutableListOf(
            V1Volume()
                .name("script-volume")
                .configMap(
                    V1ConfigMapVolumeSource()
                        .name(SCRIPT_CONFIG_MAP_NAME)
                        .items(
                            listOf(
                                V1KeyToPath().apply {
                                    key = CMD
                                    path = CMD
                                },
                            )
                        )
                )
        )
        // 存在 cos 则添加对 cos 的配置
        if (config.cosConfigMapName != null) {
            res.add(
                V1Volume()
                    .name("cos")
                    .configMap(
                        V1ConfigMapVolumeSource()
                            .name(config.cosConfigMapName)
                            .items(
                                listOf(
                                    V1KeyToPath().apply {
                                        key = COS_FILE
                                        path = COS_FILE
                                    },
                                    V1KeyToPath().apply {
                                        key = COS_DNS_FILE
                                        path = COS_DNS_FILE
                                    },
                                )
                            )
                    )
            )
        }
        return res
    }

    private fun <T> CoreV1Api.exec(block: () -> T?): T? {
        try {
            return block()
        } catch (e: ApiException) {
            if (e.code == HttpStatus.NOT_FOUND.value()) {
                logger.warn("k8s exec not found, ${e.responseBody}")
                return null
            }
            throw e
        }
    }

    private fun ApiException.buildMessage(): String {
        val builder = StringBuilder().append(code)
            .appendLine("[$message]")
            .appendLine(responseHeaders)
            .appendLine(responseBody)
        return builder.toString()
    }

    fun restartJob(ids: Set<String>) {
        mediaTranscodeJobDao.updateJobsStatus(ids, MediaTranscodeJobStatus.WAITING)
    }

    /**
     * 上报转码任务状态和监控信息
     */
    fun jobReport(artifactInfo: ArtifactInfo, data: TranscodeReportData) {
        mediaTranscodeJobDao.updateStatus(
            projectId = artifactInfo.projectId,
            repoName = artifactInfo.repoName,
            fileName = artifactInfo.getResponseName(),
            status = data.status,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TranscodeJobService::class.java)

        // 先写死，后面写到配置
        private const val SCRIPT_CONFIG_MAP_NAME = "media-transcode-job-script"
        private const val WORK_SPACE = "/data/workspace"
        private const val CMD = "run.py"
        private const val COS_FILE = ".cos.yaml"
        private const val COS_DNS_FILE = "resolv.conf"
        private val PYTHON_CMD =
            listOf("/bin/bash", "-c", "source /opt/conda/bin/activate media && python $CMD")
        const val TRANSCODE_JOB_ID_LABEL = "BKREPO_MEDIA_TRANSCODE_JOB_ID"
        const val TRANSCODE_JOB_PROJECT_ID = "BKREPO_MEDIA_TRANSCODE_JOB_PROJECT_ID"
        const val TRANSCODE_JOB_REPO_NAME = "BKREPO_MEDIA_TRANSCODE_JOB_REPO_NAME"
        const val TRANSCODE_JOB_FILE_NAME = "BKREPO_MEDIA_TRANSCODE_JOB_FILE_NAME"
        const val TRANSCODE_JOB_APP_LABEL_KEY = "app"
        const val TRANSCODE_JOB_APP_LABEL_VALUE = "BKREPO_MEDIA_TRANSCODE_JOB"
    }
}