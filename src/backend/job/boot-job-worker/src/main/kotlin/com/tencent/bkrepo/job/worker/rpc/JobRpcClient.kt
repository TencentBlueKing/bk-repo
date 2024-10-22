package com.tencent.bkrepo.job.worker.rpc

import com.tencent.devops.api.pojo.Response
import com.tencent.devops.schedule.constants.DATE_TIME_FORMATTER
import com.tencent.devops.schedule.constants.SERVER_RPC_V1
import com.tencent.devops.schedule.enums.ExecutionCodeEnum
import com.tencent.devops.schedule.pojo.job.JobCreateRequest
import com.tencent.devops.schedule.pojo.job.JobInfo
import com.tencent.devops.schedule.pojo.job.JobQueryParam
import com.tencent.devops.schedule.pojo.log.JobLog
import com.tencent.devops.schedule.pojo.log.LogQueryParam
import com.tencent.devops.schedule.pojo.page.Page
import com.tencent.devops.schedule.pojo.worker.WorkerGroup
import com.tencent.devops.schedule.pojo.worker.WorkerGroupCreateRequest
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForObject
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime

class JobRpcClient(private val workerRestTemplate: RestTemplate, serverAddr: String) {
    private val serverUrl = normalizeUrl(serverAddr)

    fun findJobInfoByName(name: String): JobInfo? {
        return listJob(JobQueryParam(name)).firstOrNull()
    }

    fun createJob(request: JobCreateRequest): String {
        return workerRestTemplate.postForObject<Response<String>>(serverUrl + RPC_CREATE_JOB, request).data
            .orEmpty()
    }

    fun startJob(jobId: String) {
        workerRestTemplate.postForObject<Void?>(serverUrl + RPC_START_JOB, null, jobId)
    }

    fun deleteJob(jobId: String) {
        workerRestTemplate.delete(serverUrl + RPC_DELETE_JOB, jobId)
    }

    fun listJob(jobQueryParam: JobQueryParam): List<JobInfo> {
        with(jobQueryParam) {
            val type = object : ParameterizedTypeReference<Response<Page<JobInfo>>>() {}
            val uri = UriComponentsBuilder.fromHttpUrl(serverUrl + RPC_LIST_JOB)
                .replaceQueryParam2("name", name)
                .replaceQueryParam2("groupId", groupId)
                .replaceQueryParam2("triggerStatus", triggerStatus)
                .build()
                .encode()
                .toUri()
            val response = workerRestTemplate.exchange(
                uri,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                type,
            ).body
            return response?.data?.records ?: return emptyList()
        }
    }

    fun findJobInfoById(id: String): JobInfo? {
        val type = object : ParameterizedTypeReference<Response<JobInfo>>() {}
        val response = workerRestTemplate.exchange(
            serverUrl + RPC_GET_JOB,
            HttpMethod.GET,
            HttpEntity.EMPTY,
            type,
            id,
        ).body
        return response?.data
    }

    fun findWorkerGroup(name: String): WorkerGroup? {
        val type = object : ParameterizedTypeReference<Response<Page<WorkerGroup>>>() {}
        val response = workerRestTemplate.exchange(
            serverUrl + RPC_LIST_GROUP,
            HttpMethod.GET,
            HttpEntity.EMPTY,
            type,
            name,
        ).body
        return response?.data?.records?.firstOrNull()
    }

    fun createWorkerGroup(request: WorkerGroupCreateRequest): String {
        return workerRestTemplate.postForObject<Response<String>>(
            serverUrl + RPC_CREATE_GROUP,
            request,
        ).data.orEmpty()
    }

    fun listJobLog(param: LogQueryParam): List<JobLog> {
        with(param) {
            val type = object : ParameterizedTypeReference<Response<Page<JobLog>>>() {}
            val response = workerRestTemplate.exchange(
                serverUrl + RPC_LIST_JOB_LOG,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                type,
                jobId,
                triggerTime?.toTypedArray(),
                executionCode,
            ).body
            return response?.data?.records ?: return emptyList()
        }
    }

    /**
     * 上次调度是否执行完成
     * */
    fun lastExecutionWasCompleted(jobId: String, scheduledFireTime: LocalDateTime): Boolean {
        val param = LogQueryParam(
            jobId = jobId,
            triggerTime = listOf(
                scheduledFireTime.minusMonths(1).format(DATE_TIME_FORMATTER),
                scheduledFireTime.format(DATE_TIME_FORMATTER),
            ),
            executionCode = ExecutionCodeEnum.RUNNING.code(),
        )
        return listJobLog(param).isEmpty()
    }

    private fun normalizeUrl(serverAddr: String): String {
        val base = serverAddr.trim().trimEnd('/')
        return "$base$SERVER_RPC_V1"
    }

    private fun UriComponentsBuilder.replaceQueryParam2(name: String, value: Any?): UriComponentsBuilder {
        if (value != null) {
            replaceQueryParam(name, value)
        } else {
            replaceQueryParam(name)
        }
        return this
    }

    companion object {
        private const val RPC_CREATE_JOB = "/job/create"
        private const val RPC_DELETE_JOB = "/job/delete?id={id}"
        private const val RPC_START_JOB = "/job/start?id={id}"
        private const val RPC_LIST_JOB = "/job/list?name={name}&groupId={groupId}&triggerStatus={triggerStatus}"
        private const val RPC_GET_JOB = "/job/{id}"
        private const val RPC_LIST_GROUP = "/worker/group/list?name={name}"
        private const val RPC_CREATE_GROUP = "/worker/group/create"
        private const val RPC_LIST_JOB_LOG =
            "/log/list?jobId={jobId}&triggerTime={triggerTime}&executionCode={executionCode}"
    }
}
