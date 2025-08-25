package com.tencent.bkrepo.media.job.service

import com.tencent.bkrepo.media.common.dao.MediaTranscodeJobDao
import com.tencent.bkrepo.media.job.k8s.K8sHelper
import com.tencent.bkrepo.media.job.k8s.K8sProperties
import com.tencent.bkrepo.media.job.k8s.buildMessage
import com.tencent.bkrepo.media.job.k8s.limits
import com.tencent.bkrepo.media.job.k8s.requests
import com.tencent.bkrepo.media.common.model.TMediaTranscodeJob
import com.tencent.bkrepo.media.common.model.TMediaTranscodeJobConfig
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
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.ZoneOffset

@Service
@EnableConfigurationProperties(K8sProperties::class)
class TranscodeJobService @Autowired constructor(
    private val k8sProperties: K8sProperties,
    private val mediaTranscodeJobDao: MediaTranscodeJobDao
) {

    private val k8sClient: ApiClient by lazy { createK8sHandler() }

    @Async
    fun startJob(config: TMediaTranscodeJobConfig) {
        val job = mediaTranscodeJobDao.findAndQueueOldestWaitingJob() ?: run {
            logger.debug("no waiting job to run")
            return
        }

        var jobName = "${job.id}-${job.updateTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli()}".lowercase()
        if (jobName.length > 63) {
            jobName = jobName.substring(0, 63)
        }
        val api = CoreV1Api(k8sClient)
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
            createK8sJob(BatchV1Api(k8sClient), jobName, config, job)
        } catch (e: ApiException) {
            logger.error(e.buildMessage())
            throw e
        }

        mediaTranscodeJobDao.initJob(job.id!!)
    }

    private fun createK8sJob(
        api: BatchV1Api,
        jobName: String,
        config: TMediaTranscodeJobConfig,
        job: TMediaTranscodeJob
    ) {
        val metadata = V1ObjectMeta()
            .name(jobName)
        val container = V1Container()
            .name("transcoder")
            .image(config.image)
            .command(PYTHON_CMD)
            .env(
                listOf(
                    V1EnvVar().name("MEDIA_JOB_JOB_PARAM").value(job.param)
                )
            )
            .volumeMounts(
                listOf(
                    V1VolumeMount()
                        .name("script-volume")
                        .mountPath("$WORK_SPACE/$CMD")
                        .subPath(CMD)
                        .readOnly(true)
                )
            )
            .resources(
                V1ResourceRequirements().apply {
                    limits(
                        cpu = k8sProperties.limit.limitCpu,
                        memory = k8sProperties.limit.limitMem,
                        ephemeralStorage = k8sProperties.limit.limitStorage,
                    )
                    requests(
                        cpu = k8sProperties.limit.requestCpu,
                        memory = k8sProperties.limit.requestMem,
                        ephemeralStorage = k8sProperties.limit.requestStorage,
                    )
                }
            )
        val scriptVolume = V1Volume()
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
        val podSpec = V1PodSpec()
            .restartPolicy("Never")
            .containers(listOf(container))
            .volumes(listOf(scriptVolume))
        val jobSpec = V1JobSpec()
            .backoffLimit(0)
            .ttlSecondsAfterFinished(60 * 60 * 24 * 3)
            .template(V1PodTemplateSpec().spec(podSpec))
        val kJob = V1Job()
            .apiVersion("batch/v1")
            .kind("Job")
            .metadata(metadata)
            .spec(jobSpec)
        val result = api.createNamespacedJob(
            k8sProperties.namespace,
            kJob,
            null,
            null,
            null,
        )
        logger.info("created job $jobName in namespace '${k8sProperties.namespace}' status: ${result.status}")
    }

    private fun createK8sHandler(): ApiClient {
        return K8sHelper.createClient(k8sProperties)
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

    companion object {
        private val logger = LoggerFactory.getLogger(TranscodeJobService::class.java)

        // 先写死，后面写到配置
        private const val SCRIPT_CONFIG_MAP_NAME = "media-transcode-job-script"
        private const val WORK_SPACE = "/data/workspace"
        private const val CMD = "run.py"
        private val PYTHON_CMD =
            listOf("/bin/bash", "-c", "source /opt/conda/bin/activate media && python $CMD")
    }
}