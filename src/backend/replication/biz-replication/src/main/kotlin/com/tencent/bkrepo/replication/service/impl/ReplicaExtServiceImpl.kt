/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.util.okhttp.BasicAuthInterceptor
import com.tencent.bkrepo.common.storage.innercos.http.toMediaTypeOrNull
import com.tencent.bkrepo.common.storage.innercos.retry
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.NODE_FULL_PATH
import com.tencent.bkrepo.replication.pojo.ext.CheckRepoDifferenceRequest
import com.tencent.bkrepo.replication.pojo.remote.request.RemoteConfigCreateRequest
import com.tencent.bkrepo.replication.pojo.remote.request.RemoteCreateRequest
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.objects.PackageConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.PathConstraint
import com.tencent.bkrepo.replication.pojo.task.setting.ConflictStrategy
import com.tencent.bkrepo.replication.pojo.task.setting.ReplicaSetting
import com.tencent.bkrepo.replication.util.OkHttpClientPool
import com.tencent.bkrepo.replication.replica.context.ReplicaContext.Companion.READ_TIMEOUT
import com.tencent.bkrepo.replication.replica.context.ReplicaContext.Companion.WRITE_TIMEOUT
import com.tencent.bkrepo.replication.service.RemoteNodeService
import com.tencent.bkrepo.replication.service.ReplicaExtService
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class ReplicaExtServiceImpl(
    private val repositoryClient: RepositoryClient,
    private val packageClient: PackageClient,
    private val nodeClient: NodeClient,
    private val remoteNodeService: RemoteNodeService,
    private val replicationProperties: ReplicationProperties,
    ) : ReplicaExtService {

    override fun findRepoDifference(request: CheckRepoDifferenceRequest): Any {
        with(request) {
            val repoDetail = queryRepoDetail(
                host = localHost,
                userName = localUserName,
                password = localPassword,
                projectId = localProjectId,
                repoName = localRepoName
            )
            return when (repoDetail.type) {
                RepositoryType.GENERIC -> {
                    findDifferenceForGeneric(request)
                }
                else -> {
                    findDifferenceForNonGeneric(request)
                }
            }
        }
    }

    override fun syncRepoDifference(request: CheckRepoDifferenceRequest) {
        val difference = findRepoDifference(request)
        logger.info("Will try to sync difference artifact")
        if (difference is List<*>) {
            runSync(
                request = request,
                pathConstraints = convertPathConstraint(difference as List<String>)
            )
        } else {
            runSync(
                request = request,
                packageConstraints = convertPackageConstraint(difference as MutableMap<String, List<String>>)
            )
        }
    }


    private fun runSync(
        request: CheckRepoDifferenceRequest,
        packageConstraints: List<PackageConstraint>? = null,
        pathConstraints: List<PathConstraint>? = null,
        ) {
        with(request) {
            val taskRequest = convertRemoteConfigCreateRequest(
                pathConstraints = pathConstraints,
                packageConstraints = packageConstraints,
                clusterId = remoteClusterId!!,
                userName = remoteUserName,
                password = remotePassword,
                projectId = remoteProjectId,
                repoName = remoteRepoName
            )
            logger.info("Will create runonce task to do sync")
            remoteNodeService.remoteClusterCreate(
                localProjectId, localRepoName, RemoteCreateRequest(listOf(taskRequest))
            )
            logger.info("Runonce task will be executed")
            remoteNodeService.executeRunOnceTask(localProjectId, localRepoName, taskRequest.name)
        }
    }

    private fun convertPackageConstraint(
        difference: MutableMap<String, List<String>>
    ) : List<PackageConstraint>? {
        if (difference.isEmpty()) return null
        return difference.map {
            PackageConstraint(packageKey = it.key, versions = it.value)
        }
    }

    private fun convertPathConstraint(
        difference: List<String>
    ) : List<PathConstraint>? {
        if (difference.isEmpty()) return null
        return difference.map {
            PathConstraint(path = it)
        }
    }

    private fun convertRemoteConfigCreateRequest(
        packageConstraints: List<PackageConstraint>? = null,
        pathConstraints: List<PathConstraint>? = null,
        clusterId: String,
        userName: String?,
        password: String?,
        projectId: String,
        repoName: String
    ): RemoteConfigCreateRequest {
        return RemoteConfigCreateRequest(
            name = "sync-${StringPool.uniqueId()}",
            clusterId = clusterId,
            remoteUserUsername = userName,
            remoteUserPassword = password,
            remoteProjectId = projectId,
            remoteRepoName = repoName,
            packageConstraints = packageConstraints,
            pathConstraints = pathConstraints,
            replicaType = ReplicaType.RUN_ONCE,
            setting = ReplicaSetting(conflictStrategy = ConflictStrategy.OVERWRITE),
            description = BACKEND_REPO_SYNC,
            enable = true
        )
    }


    private fun findDifferenceForGeneric(request: CheckRepoDifferenceRequest): List<String> {
        with(request) {
            val result: MutableList<String> = mutableListOf()
            var pageNum = 1
            val pageSize = replicationProperties.pageSize
            do {
                val nodes = listNodes(
                    host = localHost,
                    userName = localUserName,
                    password = localPassword,
                    projectId = localProjectId,
                    repoName = localRepoName,
                    pageNumber = pageNum,
                    pageSize = pageSize
                )
                if (nodes.isNullOrEmpty()) return result
                val existPathList = searchExistNodesFromRemote(
                    host = remoteHost,
                    projectId = remoteProjectId,
                    repoName = remoteRepoName,
                    fullPathList = nodes,
                    pageSize = pageSize,
                    httpClient = getOkhttpClient(remoteHost, remoteUserName, remotePassword)
                )
                val pathDifference = findDifference(nodes, existPathList)
                if (pathDifference.isNotEmpty()) {
                    result.addAll(pathDifference)
                }
                pageNum += 1
            } while (!nodes.isNullOrEmpty())
            return result
        }
    }

    private fun findDifferenceForNonGeneric(request: CheckRepoDifferenceRequest): MutableMap<String, List<String>> {
        val result: MutableMap<String, List<String>> = mutableMapOf()
        val option = PackageListOption(pageNumber = 1, pageSize = replicationProperties.pageSize)
        do {
            val packages = listPackages(request, option)
            packages.forEach {
                val versionDifference = findVersionDifference(request, it.key)
                if (versionDifference.isNotEmpty()) {
                    result[it.key] = versionDifference
                }
            }
            option.pageNumber += 1
        } while (packages.isNotEmpty())
        return result
    }

    private fun findVersionDifference(
        request: CheckRepoDifferenceRequest,
        packageKey: String
        ): List<String> {
        with(request) {
            val localVersionList = listVersions(
                host = localHost,
                userName = localUserName,
                password = localPassword,
                projectId = localProjectId,
                repoName = localRepoName,
                packageKey = packageKey
            )
            val remoteVersionList = listVersions(
                host = remoteHost,
                userName = remoteUserName,
                password = remotePassword,
                projectId = remoteProjectId,
                repoName = remoteRepoName,
                packageKey = packageKey
            )
            return findVersionDifference(localVersionList, remoteVersionList) ?: emptyList()
        }
    }

    private fun queryRepoDetail(
        host: String?,
        userName: String?,
        password: String?,
        projectId: String,
        repoName: String,
    ): RepositoryDetail {
        return if (host.isNullOrEmpty()) {
            repositoryClient.getRepoDetail(projectId, repoName, null).data
        } else {
            queryRepoDetailFromHost(
                host = host,
                projectId = projectId,
                repoName = repoName,
                httpClient = getOkhttpClient(host, userName!!, password!!)
            )
        } ?: throw RepoNotFoundException("$projectId|$repoName")
    }

    private fun listNodes(
        host: String?,
        userName: String?,
        password: String?,
        projectId: String,
        repoName: String,
        pageNumber: Int,
        pageSize: Int
    ): List<String>? {
        return if (host.isNullOrEmpty()) {
            val option = NodeListOption(
                includeFolder = false,
                deep = true,
                pageNumber = pageNumber,
                pageSize = pageSize
            )
            nodeClient.listNodePage(
                projectId = projectId,
                repoName = repoName,
                path = PathUtils.UNIX_SEPARATOR.toString(),
                option = option
            ).data?.records?.map { it.fullPath }
        } else {
            listNodesFromRemote(
                host = host,
                projectId = projectId,
                repoName = repoName,
                pageNumber = pageNumber,
                pageSize = pageSize,
                httpClient = getOkhttpClient(host, userName!!, password!!)
            )?.map { it.fullPath }
        }
    }


    private fun listVersions(
        host: String?,
        userName: String?,
        password: String?,
        projectId: String,
        repoName: String,
        packageKey: String
    ): List<PackageVersion>? {
        return if (host.isNullOrEmpty()) {
            packageClient.listAllVersion(projectId, repoName, packageKey).data
        } else {
            listPackageVersionsFromRemote(
                host = host,
                projectId = projectId,
                repoName = repoName,
                packageKey = packageKey,
                httpClient = getOkhttpClient(host, userName!!, password!!)
            )
        }
    }

    private fun listPackages(
        request: CheckRepoDifferenceRequest,
        option: PackageListOption
    ) : List<PackageSummary> {
        with(request) {
            return if (localHost.isNullOrEmpty()) {
                packageClient.listPackagePage(
                    projectId = localProjectId,
                    repoName = localRepoName,
                    option = option
                ).data?.records ?: emptyList()
            } else {
                listPackagesFromHost(
                    host = localHost!!,
                    projectId = localProjectId,
                    repoName = localRepoName,
                    pageNumber = option.pageNumber,
                    pageSize = option.pageSize,
                    httpClient = getOkhttpClient(localHost!!, localUserName!!, localPassword!!)
                ) ?: emptyList()
            }
        }
    }

    private fun findVersionDifference(
        localVersionList: List<PackageVersion>?,
        remoteVersionList: List<PackageVersion>?
    ) : List<String>? {
        if (remoteVersionList.isNullOrEmpty()) return localVersionList?.map { it.name }
        if (localVersionList.isNullOrEmpty()) return null
        val localList = localVersionList.map { it.name }
        val remoteList = remoteVersionList.map { it.name }
        return findDifference(localList, remoteList)
    }

    private fun findDifference(localList: List<String>, remoteList: List<String>?) : List<String> {
        if (remoteList.isNullOrEmpty()) return localList
        val result: MutableList<String> = mutableListOf()
        localList.forEach {
            if (!remoteList.contains(it)) result.add(it)
        }
        return result
    }

    private fun queryRepoDetailFromHost(
        host: String,
        projectId: String,
        repoName: String,
        httpClient: OkHttpClient
    ): RepositoryDetail? {
        val url = "$host/repository/api/repo/info/$projectId/$repoName"
        return try {
            val request = Request.Builder().url(url).get().build()
            val responseContent = doRequest(httpClient, request) ?: return null
            responseContent.readJsonString<Response<RepositoryDetail?>>().data
        } catch (exception: Exception) {
            logger.error("queryRepoDetailFromRemote url is $url, error: ", exception)
            null
        }
    }

    private fun listPackagesFromHost(
        host: String,
        projectId: String,
        repoName: String,
        pageNumber: Int,
        pageSize: Int,
        httpClient: OkHttpClient
    ): List<PackageSummary>? {
        val url = "$host/repository/api/package/page/$projectId/$repoName" +
            "?pageNumber=$pageNumber&pageSize=$pageSize"
        return try {
            val request = Request.Builder().url(url).get().build()
            val responseContent = doRequest(httpClient, request) ?: return null
            responseContent.readJsonString<Response<Page<PackageSummary>>>().data?.records
        } catch (exception: Exception) {
            logger.error("listPackagesFromRemote url is $url, error: ", exception)
            null
        }
    }

    private fun listPackageVersionsFromRemote(
        host: String,
        projectId: String,
        repoName: String,
        packageKey: String,
        httpClient: OkHttpClient
    ): List<PackageVersion>? {
        val url = "$host/repository/api/version/list/$projectId/$repoName?packageKey=$packageKey"
        return try {
            val body = VersionListOption().toJsonString()
                .toRequestBody(MediaTypes.APPLICATION_JSON.toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(body).build()
            val responseContent = doRequest(httpClient, request) ?: return null
            responseContent.readJsonString<Response<List<PackageVersion>>>().data
        } catch (exception: Exception) {
            logger.error("listPackageVersionsFromRemote url is $url, error: ", exception)
            null
        }
    }

    private fun listNodesFromRemote(
        host: String,
        projectId: String,
        repoName: String,
        pageNumber: Int,
        pageSize: Int,
        httpClient: OkHttpClient
    ): List<NodeInfo>? {
        val url = "$host/repository/api/node/page/$projectId/$repoName"
        return try {
            val body = NodeListOption(
                includeFolder = false,
                deep = true,
                pageNumber = pageNumber,
                pageSize = pageSize
            ).toJsonString()
                .toRequestBody(MediaTypes.APPLICATION_JSON.toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(body).build()
            val responseContent = doRequest(httpClient, request) ?: return null
            responseContent.readJsonString<Response<Page<NodeInfo>>>().data?.records
        } catch (exception: Exception) {
            logger.error("listNodesFromRemote url is $url, error: ", exception)
            null
        }
    }

    private fun searchExistNodesFromRemote(
        host: String,
        projectId: String,
        repoName: String,
        fullPathList: List<String>,
        pageSize: Int,
        httpClient: OkHttpClient
    ): List<String>? {
        val url = "$host/repository/api/node/search"
        return try {
            val ruleList = mutableListOf<Rule>(
                Rule.QueryRule(PROJECT_ID, projectId, OperationType.EQ),
                Rule.QueryRule(REPO_NAME, repoName, OperationType.EQ),
                Rule.QueryRule(NODE_FULL_PATH, fullPathList, OperationType.IN),
            )
            val body = QueryModel(
                page = PageLimit(1, pageSize),
                select = mutableListOf(NODE_FULL_PATH),
                rule = Rule.NestedRule(ruleList, Rule.NestedRule.RelationType.AND),
                sort = null
            ).toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(body).build()
            val responseContent = doRequest(httpClient, request) ?: return null
            responseContent.readJsonString<Response<Page<Map<String, String>>>>()
                .data?.records?.map { it[NODE_FULL_PATH]!! }
        } catch (exception: Exception) {
            logger.error("searchExistNodesFromRemote url is $url, error: ", exception)
            null
        }
    }

    private fun doRequest(
        httpClient: OkHttpClient,
        request: Request
    ): String? {
        retry(times = 3, delayInSeconds = 5) {
            httpClient.newCall(request).execute().use {
                return if (it.isSuccessful) {
                    it.body!!.string()
                } else {
                    logger.warn("request ${request.url} response code is ${it.code}")
                    null
                }
            }
        }
    }


    private fun getOkhttpClient(
        host: String,
        userName: String,
        password: String,
    ) : OkHttpClient {
        val cluster = ClusterInfo(
            name = host,
            url = host,
            username = userName,
            password = password,
        )
        val readTimeout = Duration.ofMillis(READ_TIMEOUT)
        val writeTimeout = Duration.ofMillis(WRITE_TIMEOUT)
        return OkHttpClientPool.getHttpClient(
            emptyList(),
            cluster,
            readTimeout,
            writeTimeout,
            Duration.ZERO,
            BasicAuthInterceptor(userName, password),
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReplicaExtServiceImpl::class.java)
        private const val BACKEND_REPO_SYNC = "backendRepoSync"
    }
}
