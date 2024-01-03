package com.tencent.bkrepo.job.batch.base

import com.tencent.bkrepo.job.config.properties.BatchJobProperties
import com.tencent.bkrepo.job.pojo.TJobFailover
import com.tencent.bkrepo.job.repository.JobSnapshotRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

/**
 * 使用mongodb的故障转移实现
 * */
abstract class MongodbFailoverJob<C : JobContext>(batchJobProperties: BatchJobProperties) :
    CenterNodeJob<C>(batchJobProperties), FailoverJob {
    @Autowired
    private lateinit var jobSnapshotRepository: JobSnapshotRepository

    private val name = getJobName()

    override fun failover() {
        val jobFailover = capture()
        jobSnapshotRepository.save(jobFailover)
        logger.info("Job [$name] failover successful.")
    }

    override fun isFailover(): Boolean {
        return jobSnapshotRepository.findFirstByNameOrderByIdDesc(name) != null
    }

    override fun recover() {
        jobSnapshotRepository.findFirstByNameOrderByIdDesc(name)?.let {
            reply(it)
            logger.info("Job [$name] recover successful.")
            jobSnapshotRepository.deleteByName(name)
        }
    }

    /**
     * 发生故障时，捕获现场，以便后续恢复
     * */
    abstract fun capture(): TJobFailover

    /**
     * 根据故障转移记录[jobFailover]恢复现场
     * */
    abstract fun reply(jobFailover: TJobFailover)

    companion object {
        private val logger = LoggerFactory.getLogger(MongodbFailoverJob::class.java)
    }
}
