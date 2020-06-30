package com.tencent.bkrepo.common.quartz.converter

import com.tencent.bkrepo.common.quartz.util.Keys.KEY_GROUP
import com.tencent.bkrepo.common.quartz.util.Keys.KEY_NAME
import org.bson.Document
import org.quartz.Job
import org.quartz.JobBuilder
import org.quartz.JobDataMap
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.JobPersistenceException
import org.quartz.spi.ClassLoadHelper

class JobConverter(
    private val loadHelper: ClassLoadHelper,
    private val jobDataConverter: JobDataConverter
) {

    /**
     * Converts job detail into document.
     * Depending on the config, job data map can be stored
     * as a `base64` encoded (default) or plain object.
     */
    fun toDocument(newJob: JobDetail, key: JobKey): Document {
        val job = Document()
        job[KEY_NAME] = key.name
        job[KEY_GROUP] = key.group
        job[JOB_DESCRIPTION] = newJob.description
        job[JOB_CLASS] = newJob.jobClass.name
        job[JOB_DURABILITY] = newJob.isDurable
        job[JOB_REQUESTS_RECOVERY] = newJob.requestsRecovery()
        jobDataConverter.toDocument(newJob.jobDataMap, job)
        return job
    }

    /**
     * Converts from document to job detail.
     */
    @Throws(JobPersistenceException::class)
    fun toJobDetail(doc: Document): JobDetail {
        return try {
            // Make it possible for subclasses to use custom class loaders.
            // When Quartz jobs are implemented as Clojure records, the only way to use
            // them without switching to gen-class is by using a
            // clojure.lang.DynamicClassLoader instance.
            val jobClass: Class<Job> = loadHelper.classLoader.loadClass(doc.getString(JOB_CLASS)) as Class<Job>
            val builder = createJobBuilder(doc, jobClass)
            withDurability(doc, builder)
            withRequestsRecovery(doc, builder)
            val jobData = createJobDataMap(doc)
            builder.usingJobData(jobData).build()
        } catch (e: ClassNotFoundException) {
            throw JobPersistenceException("Could not load job class " + doc.get(JOB_CLASS), e)
        }
    }

    /**
     * Converts document into job data map.
     * Will first try [JobDataConverter] to deserialize
     * from '{@value Constants#JOB_DATA}' (`base64`)
     * or '{@value Constants#JOB_DATA_PLAIN}' fields.
     * If didn't succeed, will try to build job data
     * from root fields (legacy, subject to remove).
     */
    private fun createJobDataMap(doc: Document): JobDataMap {
        val jobData = JobDataMap()
        if (!jobDataConverter.toJobData(doc, jobData)) {
            for (key in doc.keys) {
                if (key != KEY_NAME
                    && key != KEY_GROUP
                    && key != JOB_CLASS
                    && key != JOB_DESCRIPTION
                    && key != JOB_DURABILITY
                    && key != JOB_REQUESTS_RECOVERY
                    && key != "_id"
                ) {
                    jobData[key] = doc[key]
                }
            }
        }
        jobData.clearDirtyFlag()
        return jobData
    }

    private fun withDurability(doc: Document, builder: JobBuilder) {
        val jobDurability = doc[JOB_DURABILITY]
        if (jobDurability != null) {
            when (jobDurability) {
                is Boolean -> {
                    builder.storeDurably(jobDurability)
                }
                is String -> {
                    builder.storeDurably(jobDurability.toBoolean())
                }
                else -> {
                    throw JobPersistenceException(
                        "Illegal value for " + JOB_DURABILITY + ", class "
                            + jobDurability.javaClass + " not supported"
                    )
                }
            }
        }
    }

    private fun withRequestsRecovery(doc: Document, builder: JobBuilder) {
        if (doc.getBoolean(JOB_REQUESTS_RECOVERY, false)) {
            builder.requestRecovery(true)
        }
    }

    private fun createJobBuilder(doc: Document, jobClass: Class<Job>): JobBuilder {
        return JobBuilder.newJob(jobClass)
            .withIdentity(doc.getString(KEY_NAME), doc.getString(KEY_GROUP))
            .withDescription(doc.getString(JOB_DESCRIPTION))
    }

    companion object {
        const val JOB_DURABILITY = "durability"
        private const val JOB_CLASS = "jobClass"
        private const val JOB_DESCRIPTION = "jobDescription"
        const val JOB_REQUESTS_RECOVERY = "requestsRecovery"
    }
}
