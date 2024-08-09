package com.tencent.bkrepo.job.worker.config

import com.tencent.bkrepo.common.api.exception.MethodNotAllowedException
import com.tencent.bkrepo.job.batch.base.BatchJob
import com.tencent.bkrepo.job.schedule.Job
import com.tencent.bkrepo.job.schedule.JobRegistrar
import com.tencent.bkrepo.job.schedule.JobScheduleType
import com.tencent.bkrepo.job.schedule.JobUtils
import com.tencent.devops.api.pojo.Response
import com.tencent.devops.schedule.config.ScheduleWorkerProperties
import com.tencent.devops.schedule.constants.SERVER_RPC_V1
import com.tencent.devops.schedule.enums.BlockStrategyEnum
import com.tencent.devops.schedule.enums.DiscoveryTypeEnum
import com.tencent.devops.schedule.enums.JobModeEnum
import com.tencent.devops.schedule.enums.MisfireStrategyEnum
import com.tencent.devops.schedule.enums.RouteStrategyEnum
import com.tencent.devops.schedule.pojo.job.JobCreateRequest
import com.tencent.devops.schedule.pojo.job.JobInfo
import com.tencent.devops.schedule.pojo.page.Page
import com.tencent.devops.schedule.pojo.worker.WorkerGroup
import com.tencent.devops.schedule.pojo.worker.WorkerGroupCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForObject

/**
 * 调度中心任务注册
 * */
class DevOpsScheduleJobRegistrar(
    private val workerRestTemplate: RestTemplate,
    workerProperties: ScheduleWorkerProperties,
    private val beanFactory: ConfigurableListableBeanFactory,
) : JobRegistrar {
    private val serverUrl = normalizeUrl(workerProperties.server)

    @Value("\${service.prefix}\${spring.application.name}")
    private var defaultGroupName = ""

    private var defaultGroupId: String = ""
    override fun init() {
        if (defaultGroupId.isEmpty()) {
            defaultGroupId = getOrCreateDefaultGroup()
        }
    }

    override fun register(job: Job) {
        with(job) {
            val request = JobCreateRequest(
                name = SYSTEM_JOB_PREFIX + name,
                description = "system job",
                groupId = determineGroupId(job),
                scheduleType = JobUtils.convertScheduleType(scheduleType).code(),
                scheduleConf = scheduleConf,
                jobMode = JobModeEnum.BEAN.code(),
                jobHandler = getJobHandlerBeanName(job),
                misfireStrategy = MisfireStrategyEnum.RETRY.code(),
                routeStrategy = RouteStrategyEnum.ROUND.code(),
                blockStrategy = BlockStrategyEnum.SERIAL_EXECUTION.code(),
                maxRetryCount = 1,
                jobTimeout = 0,
            )
            val id = workerRestTemplate.postForObject<Response<String>>(serverUrl + RPC_CREATE_JOB, request).data
                .orEmpty()
            workerRestTemplate.postForObject<Void?>("$serverUrl$RPC_START_JOB?id=$id")
            logger.info("Registering job $name: $request")
        }
    }

    override fun unregister(job: Job) {
        val jobId = determineJobId(job)
        workerRestTemplate.delete("$serverUrl$RPC_DELETE_JOB?id=$jobId")
        logger.info("Unregistering job ${job.name}")
    }

    override fun list(): List<Job> {
        val type = object : ParameterizedTypeReference<Response<Page<JobInfo>>>() {}
        val response = workerRestTemplate.exchange(serverUrl + RPC_LIST_JOB, HttpMethod.GET, HttpEntity.EMPTY, type)
            .body
        val data = response?.data ?: return emptyList()
        return data.records.filter {
            it.name.startsWith(SYSTEM_JOB_PREFIX)
        }.map {
            Job(
                name = it.name,
                scheduleConf = it.scheduleConf,
                scheduleType = JobScheduleType.CRON,
                runnable = { },
                group = it.groupId,
                id = it.id.orEmpty(),
            )
        }
    }

    override fun update(job: Job) {
        unregister(job)
        register(job)
    }

    override fun unload() {
        throw MethodNotAllowedException()
    }

    private fun determineGroupId(job: Job): String {
        return if (job.group.isEmpty()) {
            defaultGroupId
        } else {
            findWorkerGroup(job.group)?.id.orEmpty()
        }
    }

    private fun determineJobId(job: Job): String {
        return if (job.id.isNullOrEmpty()) {
            findJobInfo(SYSTEM_JOB_PREFIX + job.name)?.id.orEmpty()
        } else {
            job.id.orEmpty()
        }
    }

    fun registerJobHandlerBean(job: Job) {
        val jobHandlerName = getJobHandlerBeanName(job)
        val jobBeanName = job.name.replaceFirstChar { it.lowercase() }
        val jobHandler = JobHandlerAdapter(beanFactory.getBean(jobBeanName, BatchJob::class.java))
        beanFactory.registerSingleton(jobHandlerName, jobHandler)
    }

    private fun getJobHandlerBeanName(job: Job): String {
        return "${job.name}#JobHandler"
    }

    private fun getOrCreateDefaultGroup(): String {
        val group = findWorkerGroup(defaultGroupName)
        return if (group == null) {
            val createGroupRequest = WorkerGroupCreateRequest(
                name = defaultGroupName,
                discoveryType = DiscoveryTypeEnum.CLOUD.code(),
                description = "system group",
            )
            workerRestTemplate.postForObject<Response<String>>(
                serverUrl + RPC_CREATE_GROUP,
                createGroupRequest,
            ).data.orEmpty()
        } else {
            group.id.orEmpty()
        }
    }

    private fun findWorkerGroup(name: String): WorkerGroup? {
        val type = object : ParameterizedTypeReference<Response<Page<WorkerGroup>>>() {}
        val response = workerRestTemplate.exchange(
            "$serverUrl$RPC_LIST_GROUP?name=$name",
            HttpMethod.GET,
            HttpEntity.EMPTY,
            type,
        ).body
        return response?.data?.records?.firstOrNull()
    }

    private fun findJobInfo(name: String): JobInfo? {
        val type = object : ParameterizedTypeReference<Response<Page<JobInfo>>>() {}
        val response = workerRestTemplate.exchange(
            "$serverUrl$RPC_LIST_JOB?name=$name",
            HttpMethod.GET,
            HttpEntity.EMPTY,
            type,
        ).body
        return response?.data?.records?.firstOrNull()
    }

    private fun normalizeUrl(server: ScheduleWorkerProperties.ScheduleWorkerServerProperties): String {
        val base = server.address.trim().trimEnd('/')
        return "$base$SERVER_RPC_V1"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DevOpsScheduleJobRegistrar::class.java)
        const val SYSTEM_JOB_PREFIX = "SYSTEM_JOB_"
        private const val RPC_CREATE_JOB = "/job/create"
        private const val RPC_DELETE_JOB = "/job/delete"
        private const val RPC_START_JOB = "/job/start"
        private const val RPC_LIST_JOB = "/job/list"
        private const val RPC_LIST_GROUP = "/worker/group/list"
        private const val RPC_CREATE_GROUP = "/worker/group/create"
    }
}
