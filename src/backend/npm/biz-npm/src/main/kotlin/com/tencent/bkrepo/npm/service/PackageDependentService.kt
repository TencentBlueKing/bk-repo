package com.tencent.bkrepo.npm.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactMigrateContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.artifact.util.http.HttpClientBuilderFactory
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.artifact.repository.NpmLocalRepository
import com.tencent.bkrepo.npm.constants.NPM_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_FULL_PATH
import com.tencent.bkrepo.npm.constants.PKG_NAME
import com.tencent.bkrepo.npm.pojo.NpmDataMigrationResponse
import com.tencent.bkrepo.npm.utils.GsonUtils
import com.tencent.bkrepo.npm.utils.MigrationUtils
import com.tencent.bkrepo.npm.utils.ThreadPoolManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import javax.annotation.Resource

@Service
class PackageDependentService {
    @Value("\${npm.migration.data.url: ''}")
    private val url: String = StringPool.EMPTY

    @Value("\${npm.migration.package.count: 100}")
    private val count: Int = DEFAULT_COUNT

    @Autowired
    private lateinit var asyncExecutor: ThreadPoolTaskExecutor

    private val okHttpClient: OkHttpClient by lazy {
        HttpClientBuilderFactory.create().readTimeout(TIMEOUT, TimeUnit.SECONDS).build()
    }

    private final fun initTotalDataSetByUrl() {
        if (StringUtils.isEmpty(url)) {
            return
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
            logger.error(
                "http send [$url] for get all package name data failed, {}",
                exception.message
            )
        } finally {
            response?.body()?.close()
        }
    }

    private final fun initTotalDataSetByFile() {
        val inputStream: InputStream = this.javaClass.classLoader.getResourceAsStream(FILE_NAME) ?: return
        val use = inputStream.use { GsonUtils.transferInputStreamToJson(it) }
        totalDataSet =
            use.entrySet().stream().filter { it.value.asBoolean }.map { it.key }.collect(Collectors.toSet())
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    fun dependentMigrationByUrl(artifactInfo: NpmArtifactInfo): NpmDataMigrationResponse<String> {
        initTotalDataSetByUrl()
        return dependentMigration(artifactInfo)
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    fun dependentMigrationByFile(artifactInfo: NpmArtifactInfo): NpmDataMigrationResponse<String> {
        initTotalDataSetByFile()
        return dependentMigration(artifactInfo)
    }

    fun dependentMigration(artifactInfo: NpmArtifactInfo): NpmDataMigrationResponse<String> {
        val attributes = RequestContextHolder.getRequestAttributes() as ServletRequestAttributes
        RequestContextHolder.setRequestAttributes(attributes, true)
        successSet.clear()
        errorSet.clear()
        logger.info("npm dependent migration pkgName size : [${totalDataSet.size}]")
        val start = System.currentTimeMillis()
        val list = MigrationUtils.split(totalDataSet, count)
        val callableList: MutableList<Callable<Set<String>>> = mutableListOf()
        list.forEach {
            callableList.add(Callable {
                RequestContextHolder.setRequestAttributes(attributes)
                doDependentMigration(artifactInfo, it.toSet())
                errorSet
            })
        }
        val resultList = ThreadPoolManager.submit(callableList)
        val elapseTimeMillis = System.currentTimeMillis() - start
        logger.info(
            "npm package dependent migrate, total size[${totalDataSet.size}], success[${successSet.size}], " +
                "fail[${errorSet.size}], elapse [${millisToSecond(elapseTimeMillis)}] s totally."
        )
        val collect = resultList.stream().flatMap { set -> set.stream() }.collect(Collectors.toSet())
        return NpmDataMigrationResponse(
            "npm dependent 依赖迁移信息展示：",
            totalDataSet.size,
            successSet.size,
            errorSet.size,
            millisToSecond(elapseTimeMillis),
            collect
        )
    }

    fun doDependentMigration(artifactInfo: NpmArtifactInfo, data: Set<String>) {
        data.forEach { pkgName ->
            try {
                dependentMigrate(artifactInfo, pkgName)
                logger.info("npm package name: [$pkgName] dependent migration success!")
                successSet.add(pkgName)
                if (isMultipleOfTen(successSet.size)) {
                    logger.info(
                        "dependent migrate progress rate : successRate:[${successSet.size}/${totalDataSet.size}], " +
                            "failRate[${errorSet.size}/${totalDataSet.size}]"
                    )
                }
            } catch (exception: IOException) {
                logger.error("failed to query [$pkgName.json] file, {}", exception.message)
                errorSet.add(pkgName)
            } catch (exception: InterruptedException) {
                logger.error("failed to query [$pkgName.json] file, {}", exception.message)
                errorSet.add(pkgName)
            }
        }
    }

    fun dependentMigrate(artifactInfo: NpmArtifactInfo, pkgName: String) {
        val context = ArtifactMigrateContext()
        context.contextAttributes[NPM_FILE_FULL_PATH] = String.format(NPM_PKG_FULL_PATH, pkgName)
        context.contextAttributes[PKG_NAME] = pkgName
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        (repository as NpmLocalRepository).dependentMigrate(context)
    }

    fun checkResponse(response: Response): Boolean {
        if (!response.isSuccessful) {
            logger.warn("Download file from remote failed: [${response.code()}]")
            return false
        }
        return true
    }

    companion object {
        private const val FILE_NAME = "pkgName.json"
        const val TIMEOUT = 60L
        const val DEFAULT_COUNT = 100
        val logger: Logger = LoggerFactory.getLogger(PackageDependentService::class.java)

        private var totalDataSet = mutableSetOf<String>()
        private val successSet = mutableSetOf<String>()
        private val errorSet = mutableSetOf<String>()

        fun millisToSecond(millis: Long): Long {
            return millis / DataMigrationService.MILLIS_RATE
        }

        fun isMultipleOfTen(size: Int): Boolean {
            return size.rem(10) == 0
        }
    }
}
