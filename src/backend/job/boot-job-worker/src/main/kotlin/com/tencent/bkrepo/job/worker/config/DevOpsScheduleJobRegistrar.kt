package com.tencent.bkrepo.job.worker.config

import com.tencent.bkrepo.common.api.exception.MethodNotAllowedException
import com.tencent.bkrepo.job.batch.base.BatchJob
import com.tencent.bkrepo.job.schedule.Job
import com.tencent.bkrepo.job.schedule.JobRegistrar
import com.tencent.bkrepo.job.schedule.JobScheduleType
import com.tencent.bkrepo.job.schedule.JobUtils
import com.tencent.bkrepo.job.worker.rpc.JobRpcClient
import com.tencent.devops.schedule.enums.BlockStrategyEnum
import com.tencent.devops.schedule.enums.DiscoveryTypeEnum
import com.tencent.devops.schedule.enums.JobModeEnum
import com.tencent.devops.schedule.enums.MisfireStrategyEnum
import com.tencent.devops.schedule.enums.RouteStrategyEnum
import com.tencent.devops.schedule.pojo.job.JobCreateRequest
import com.tencent.devops.schedule.pojo.job.JobQueryParam
import com.tencent.devops.schedule.pojo.worker.WorkerGroupCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory

/**
 * 调度中心任务注册
 * */
class DevOpsScheduleJobRegistrar(
    private val jobRpcClient: JobRpcClient,
    private val beanFactory: ConfigurableListableBeanFactory,
    private val prefix: String,
) : JobRegistrar {

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
            val routeStrategy = if (sharding) RouteStrategyEnum.SHARDING_BROADCAST else RouteStrategyEnum.ROUND
            val request = JobCreateRequest(
                name = prefix + name,
                description = "system job",
                groupId = determineGroupId(job),
                scheduleType = JobUtils.convertScheduleType(scheduleType).code(),
                scheduleConf = scheduleConf,
                jobMode = JobModeEnum.BEAN.code(),
                jobHandler = getJobHandlerBeanName(job),
                misfireStrategy = MisfireStrategyEnum.IGNORE.code(),
                routeStrategy = routeStrategy.code(),
                blockStrategy = BlockStrategyEnum.DISCARD_LATER.code(),
                maxRetryCount = 0,
                jobTimeout = 0,
            )
            val jobId = jobRpcClient.createJob(request)
            jobRpcClient.startJob(jobId)
            logger.info("Registering job $name: $request")
        }
    }

    override fun unregister(job: Job) {
        val jobId = determineJobId(job)
        jobRpcClient.deleteJob(jobId)
        logger.info("Unregistering job ${job.name}")
    }

    override fun list(): List<Job> {
        val jobQueryParam = JobQueryParam(name = prefix)
        return jobRpcClient.listJob(jobQueryParam).map {
            Job(
                name = it.name,
                scheduleConf = it.scheduleConf,
                scheduleType = JobScheduleType.CRON,
                runnable = { },
                group = it.groupId,
                id = it.id.orEmpty(),
                sharding = it.routeStrategy == RouteStrategyEnum.SHARDING_BROADCAST.code(),
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
            jobRpcClient.findWorkerGroup(job.group)?.id.orEmpty()
        }
    }

    private fun determineJobId(job: Job): String {
        return if (job.id.isNullOrEmpty()) {
            jobRpcClient.findJobInfoByName(prefix + job.name)?.id.orEmpty()
        } else {
            job.id.orEmpty()
        }
    }

    fun registerJobHandlerBean(job: Job) {
        val jobHandlerName = getJobHandlerBeanName(job)
        val jobBeanName = job.name.replaceFirstChar { it.lowercase() }
        val jobHandler = JobHandlerAdapter(beanFactory.getBean(jobBeanName, BatchJob::class.java), jobRpcClient)
        beanFactory.registerSingleton(jobHandlerName, jobHandler)
    }

    private fun getJobHandlerBeanName(job: Job): String {
        return "${job.name}#JobHandler"
    }

    private fun getOrCreateDefaultGroup(): String {
        val group = jobRpcClient.findWorkerGroup(defaultGroupName)
        return if (group == null) {
            val createGroupRequest = WorkerGroupCreateRequest(
                name = defaultGroupName,
                discoveryType = DiscoveryTypeEnum.CLOUD.code(),
                description = "system group",
            )
            jobRpcClient.createWorkerGroup(createGroupRequest)
        } else {
            group.id.orEmpty()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DevOpsScheduleJobRegistrar::class.java)
    }
}
