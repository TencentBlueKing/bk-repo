package com.tencent.bkrepo.common.quartz.converter

import com.tencent.bkrepo.common.quartz.JOB_DATA
import com.tencent.bkrepo.common.quartz.JOB_DATA_PLAIN
import com.tencent.bkrepo.common.quartz.util.SerialUtils
import org.bson.Document
import org.quartz.JobDataMap

import org.quartz.JobPersistenceException
import java.io.IOException

class JobDataConverter(private val base64Preferred: Boolean) {

    /**
     * Converts from job data map to document.
     * Depending on config, job data map can be stored
     * as a `base64` encoded or plain object.
     * @param from [JobDataMap] to convert from.
     * @param to mongo [Document] to populate.
     * @throws JobPersistenceException if could not encode.
     */
    @Throws(JobPersistenceException::class)
    fun toDocument(from: JobDataMap, to: Document) {
        if (from.isEmpty()) {
            return
        }
        if (base64Preferred) {
            try {
                to[JOB_DATA] = SerialUtils.serialize(from)
            } catch (e: IOException) {
                throw JobPersistenceException("Could not serialise job data.", e)
            }
        } else {
            to[JOB_DATA_PLAIN] = from.wrappedMap
        }
    }

    /**
     * Converts from document to job data map.
     * If `base64` is preferred, tries
     * to decode from '{@value Constants#JOB_DATA}' field.
     * Otherwise, first reads a plain object from
     * '{@value Constants#JOB_DATA_PLAIN}' field, or,
     * if not present, falls back to `base64` field.
     * @param from mongo [Document] to read from.
     * @param to [JobDataMap] to populate.
     * @return if [JobDataMap] has been populated.
     * @throws JobPersistenceException if could not decode.
     */
    @Throws(JobPersistenceException::class)
    fun toJobData(from: Document, to: JobDataMap): Boolean {
        return if (base64Preferred) {
            toJobDataFromBase64(from, to)
        } else {
            if (toJobDataFromField(from, to)) {
                true
            } else toJobDataFromBase64(from, to)
        }
    }

    /**
     * Converts from document to job data map
     * reading `base64` encoded field
     * '{@value Constants#JOB_DATA}'.
     */
    @Throws(JobPersistenceException::class)
    private fun toJobDataFromBase64(from: Document, to: JobDataMap): Boolean {
        val jobDataBase64String: String = from.getString(JOB_DATA) ?: return false
        try {
            val jobDataMap = SerialUtils.deserialize(null, jobDataBase64String)
            to.putAll(jobDataMap)
            return true
        } catch (e: IOException) {
            throw JobPersistenceException("Could not deserialize job data.", e)
        }
    }

    /**
     * Converts from document to job data map
     * reading a plain object from field
     * '{@value Constants#JOB_DATA_PLAIN}'.
     */
    private fun toJobDataFromField(from: Document, to: JobDataMap): Boolean {
        val jobDataMap = from.get(JOB_DATA_PLAIN, Map::class.java) as? Map<String, *> ?: return false
        to.putAll(jobDataMap)
        return true
    }
}
