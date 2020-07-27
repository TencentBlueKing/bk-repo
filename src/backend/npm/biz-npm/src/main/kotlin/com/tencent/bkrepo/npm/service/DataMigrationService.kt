package com.tencent.bkrepo.npm.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactMigrateContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.artifact.util.http.HttpClientBuilderFactory
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.utils.ThreadPoolManager
import com.tencent.bkrepo.npm.constants.NPM_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_FULL_PATH
import com.tencent.bkrepo.npm.constants.PKG_NAME
import com.tencent.bkrepo.npm.dao.repository.MigrationErrorDataRepository
import com.tencent.bkrepo.npm.model.TMigrationErrorData
import com.tencent.bkrepo.npm.pojo.NpmDataMigrationResponse
import com.tencent.bkrepo.npm.pojo.migration.MigrationErrorDataInfo
import com.tencent.bkrepo.npm.pojo.migration.service.MigrationErrorDataCreateRequest
import com.tencent.bkrepo.npm.utils.GsonUtils
import com.tencent.bkrepo.npm.utils.MigrationUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import javax.annotation.Resource

@Service
class DataMigrationService {

    @Value("\${npm.migration.data.url: ''}")
    private val url: String = StringPool.EMPTY

    @Value("\${npm.migration.package.count: 100}")
    private val count: Int = DEFAULT_COUNT

    @Autowired
    private lateinit var migrationErrorDataRepository: MigrationErrorDataRepository

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    private val okHttpClient: OkHttpClient by lazy {
        HttpClientBuilderFactory.create().readTimeout(TIMEOUT, TimeUnit.SECONDS).build()
    }

    private final fun initTotalDataSetByUrl(): Set<String> {
        var totalDataSet: Set<String> = emptySet()
        if (url.isEmpty()) {
            return totalDataSet
        }
        var response: Response? = null
        try {
            val request = Request.Builder().url(url).get().build()
            response = okHttpClient.newCall(request).execute()
            if (checkResponse(response)) {
                val use = response.body()!!.byteStream().use { GsonUtils.transferInputStreamToJson(it) }
                totalDataSet =
                    use.entrySet().stream().filter { it.value.asBoolean }.map { it.key }.collect(Collectors.toSet())
            }
        } catch (exception: IOException) {
            logger.error("http send [$url] for get all package name data failed, {}", exception.message)
            throw exception
        } finally {
            response?.body()?.close()
        }
        return totalDataSet
    }

    private final fun initTotalDataSetByFile(): Set<String> {
        val inputStream: InputStream = this.javaClass.classLoader.getResourceAsStream(FILE_NAME) ?: return emptySet()
        val use = inputStream.use { GsonUtils.transferInputStreamToJson(it) }
        return use.entrySet().stream().filter { it.value.asBoolean }.map { it.key }.collect(Collectors.toSet())
    }

    private final fun initTotalDataSetByPkgName(pkgName: String): Set<String> {
        if (pkgName.isNotBlank()) {
            val pkgNameSet = pkgName.split(',').filter { it.isNotBlank() }.map { it.trim() }.toMutableSet()
            return pkgNameSet
        }
        return emptySet()
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun dataMigrationByFile(artifactInfo: NpmArtifactInfo, useErrorData: Boolean): NpmDataMigrationResponse<String> {
        logger.info("migraion by file request parameter:[isUseErrorData: $useErrorData, fileName: $FILE_NAME]")
        var totalDataSet: Set<String>
        if (useErrorData) {
            val result = find(artifactInfo.projectId, artifactInfo.repoName)
            if (result == null) {
                totalDataSet = initTotalDataSetByFile()
            } else {
                totalDataSet = result.errorData
            }
        } else {
            totalDataSet = initTotalDataSetByFile()
        }
        logger.info("migration by file filter results: $totalDataSet, size: ${totalDataSet.size}")
        return dataMigration(totalDataSet, artifactInfo, useErrorData)
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun dataMigrationByUrl(artifactInfo: NpmArtifactInfo, useErrorData: Boolean): NpmDataMigrationResponse<String> {
        logger.info("migraion by url request parameter: [url: $url, isUseErrorData: $useErrorData]")
        var totalDataSet: Set<String>
        if (useErrorData) {
            val result = find(artifactInfo.projectId, artifactInfo.repoName)
            if (result == null) {
                totalDataSet = initTotalDataSetByUrl()
            } else {
                totalDataSet = result.errorData
            }
        } else {
            totalDataSet = initTotalDataSetByUrl()
        }
        logger.info("migration by url filter results: $totalDataSet, size: ${totalDataSet.size}")
        return dataMigration(totalDataSet, artifactInfo, useErrorData)
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun dataMigrationByPkgName(
        artifactInfo: NpmArtifactInfo,
        useErrorData: Boolean,
        pkgName: String
    ): NpmDataMigrationResponse<String> {
        logger.info("request parameter: [isUseErrorData: $useErrorData, pkgName: $pkgName]")
        val pkgNameSet = initTotalDataSetByPkgName(pkgName)
        logger.info("migration by pkgName filter results: $pkgNameSet, size: ${pkgNameSet.size}")
        return dataMigration(pkgNameSet, artifactInfo, useErrorData)
    }

    fun dataMigration(
        totalDataSet: Set<String>,
        artifactInfo: NpmArtifactInfo,
        useErrorData: Boolean
    ): NpmDataMigrationResponse<String> {
        val attributes = RequestContextHolder.getRequestAttributes() as ServletRequestAttributes
        RequestContextHolder.setRequestAttributes(attributes, true)

        val successSet = mutableSetOf<String>()
        val errorSet = mutableSetOf<String>()

        val start = System.currentTimeMillis()
        val list = MigrationUtils.split(totalDataSet, count)
        val callableList: MutableList<Callable<Set<String>>> = mutableListOf()
        list.forEach {
            callableList.add(Callable {
                RequestContextHolder.setRequestAttributes(attributes, true)
                doDataMigration(artifactInfo, it.toSet(), totalDataSet, successSet, errorSet)
                errorSet
            })
        }
        val resultList = ThreadPoolManager.submit(callableList)
        val elapseTimeMillis = System.currentTimeMillis() - start
        logger.info(
            "npm history data migration, total size[${totalDataSet.size}], success[${successSet.size}], " +
                "fail[${errorSet.size}], elapse [${millisToSecond(elapseTimeMillis)}] s totally."
        )
        val collect = resultList.stream().flatMap { set -> set.stream() }.collect(Collectors.toSet())
        if (collect.isNotEmpty() && useErrorData) {
            insertErrorData(artifactInfo, collect)
        }
        return NpmDataMigrationResponse(
            "数据迁移信息展示：",
            totalDataSet.size,
            successSet.size,
            errorSet.size,
            millisToSecond(elapseTimeMillis),
            collect
        )
    }

    fun doDataMigration(
        artifactInfo: NpmArtifactInfo,
        data: Set<String>,
        totalDataSet: Set<String>,
        successSet: MutableSet<String>,
        errorSet: MutableSet<String>
    ) {
        data.forEach { pkgName ->
            try {
                Thread.sleep(SLEEP_MILLIS)
                migrate(artifactInfo, pkgName)
                successSet.add(pkgName)
                logger.info("migration npm package [$pkgName] success!")
                if (isMultipleOfFive(successSet.size)) {
                    logger.info(
                        "progress rate : successRate:[${successSet.size}/${totalDataSet.size}], " +
                            "failRate[${errorSet.size}/${totalDataSet.size}]"
                    )
                }
            } catch (exception: IOException) {
                logger.error("failed to install [$pkgName.json] file, {}", exception.message)
                errorSet.add(pkgName)
            } catch (exception: InterruptedException) {
                logger.error("failed to install [$pkgName.json] file, {}", exception.message)
                errorSet.add(pkgName)
            }
        }
    }

    fun migrate(artifactInfo: NpmArtifactInfo, pkgName: String) {
        val context = ArtifactMigrateContext()
        context.contextAttributes[NPM_FILE_FULL_PATH] = String.format(NPM_PKG_FULL_PATH, pkgName)
        context.contextAttributes[PKG_NAME] = pkgName
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        repository.migrate(context)
    }

    fun checkResponse(response: Response): Boolean {
        if (!response.isSuccessful) {
            logger.warn("Download file from remote failed: [${response.code()}]")
            return false
        }
        return true
    }

    private fun insertErrorData(artifactInfo: NpmArtifactInfo, collect: Set<String>) {
        val result = find(artifactInfo.projectId, artifactInfo.repoName)
        val counter = result?.counter?.plus(1) ?: 0
        val dataCreateRequest = MigrationErrorDataCreateRequest(
            projectId = artifactInfo.projectId,
            repoName = artifactInfo.repoName,
            counter = counter,
            errorData = jacksonObjectMapper().writeValueAsString(collect)
        )
        create(dataCreateRequest)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun create(dataCreateRequest: MigrationErrorDataCreateRequest) {
        with(dataCreateRequest) {
            this.takeIf { errorData.isNotBlank() } ?: throw ErrorCodeException(
                CommonMessageCode.PARAMETER_MISSING,
                this::errorData.name
            )
            val errorData = TMigrationErrorData(
                projectId = projectId,
                repoName = repoName,
                counter = counter,
                errorData = errorData,
                createdBy = operator,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = operator,
                lastModifiedDate = LocalDateTime.now()
            )
            migrationErrorDataRepository.insert(errorData)
                .also { logger.info("Create migration error data [$dataCreateRequest] success.") }
        }
    }

    fun find(projectId: String, repoName: String): MigrationErrorDataInfo? {
        // repositoryService.checkRepository(projectId, repoName)
        val criteria = Criteria.where(TMigrationErrorData::projectId.name).`is`(projectId)
            .and(TMigrationErrorData::repoName.name).`is`(repoName)
        val query = Query.query(criteria).with(Sort.by(Sort.Direction.DESC, TMigrationErrorData::counter.name)).limit(0)
        return mongoTemplate.findOne(query, TMigrationErrorData::class.java)?.let { convert(it)!! }
    }

    companion object {
        const val FILE_NAME = "pkgName.json"
        const val TIMEOUT = 60L
        const val DEFAULT_COUNT = 100
        const val MILLIS_RATE = 1000L
        const val SLEEP_MILLIS = 20L

        val logger: Logger = LoggerFactory.getLogger(DataMigrationService::class.java)

        fun convert(tMigrationErrorData: TMigrationErrorData?): MigrationErrorDataInfo? {
            return tMigrationErrorData?.let {
                MigrationErrorDataInfo(
                    counter = it.counter,
                    errorData = jacksonObjectMapper().readValue(
                        it.errorData, object : TypeReference<MutableSet<String>>() {}
                    ),
                    projectId = it.projectId,
                    repoName = it.repoName,
                    createdBy = it.createdBy,
                    createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME)
                )
            }
        }

        fun millisToSecond(millis: Long): Long {
            return millis / MILLIS_RATE
        }

        fun isMultipleOfFive(size: Int): Boolean {
            return size.rem(5) == 0
        }
    }
}
