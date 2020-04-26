package com.tencent.bkrepo.npm.service

import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.util.http.HttpClientBuilderFactory
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.constants.DIST
import com.tencent.bkrepo.npm.constants.VERSIONS
import com.tencent.bkrepo.npm.pojo.NpmDataMigrationResponse
import com.tencent.bkrepo.npm.utils.GsonUtils
import com.tencent.bkrepo.npm.utils.ThreadPoolManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.Callable
import java.util.stream.Collectors

@Service
class DataMigrationService {

    @Value("\${npm.data.url: ''}")
    private val url: String = StringPool.EMPTY

    @Value("\${npm.client.registry: ''}")
    private val registry: String = StringPool.EMPTY

    private val okHttpClient: OkHttpClient by lazy { HttpClientBuilderFactory.create().build() }

    private var totalDataSet = mutableSetOf<String>()
    private val successSet = mutableSetOf<String>()
    private val errorSet = mutableSetOf<String>()

    private final fun initTotalDataSetByUrl() {
        if (totalDataSet.isNullOrEmpty() && StringUtils.isNotEmpty(url)) {
            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()
            totalDataSet = response.body()!!.byteStream().let { GsonUtils.transferInputStreamToJson(it) }.keySet()
        }
    }

    private final fun initTotalDataSetByFile() {
        if (totalDataSet.isNullOrEmpty()) {
            val inputStream: InputStream? = this.javaClass.classLoader.getResourceAsStream(FILE_NAME)
            totalDataSet = GsonUtils.gson.fromJson<JsonObject>(
                InputStreamReader(inputStream!!),
                object : TypeToken<JsonObject>() {}.type
            ).keySet()
        }
    }

    fun dataMigrationByFile(artifactInfo: NpmArtifactInfo): NpmDataMigrationResponse<String> {
        initTotalDataSetByFile()
        return dataMigration(artifactInfo)
    }

    fun dataMigrationByUrl(artifactInfo: NpmArtifactInfo): NpmDataMigrationResponse<String> {
        initTotalDataSetByUrl()
        return dataMigration(artifactInfo)
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun dataMigration(artifactInfo: NpmArtifactInfo): NpmDataMigrationResponse<String> {
        totalDataSet.removeAll(successSet)
        successSet.clear()
        errorSet.clear()
        logger.info("npm total pkgName size : ${totalDataSet.size}")
        val start = System.currentTimeMillis()
        val list = split(totalDataSet, 300)
        val callableList: MutableList<Callable<Set<String>>> = mutableListOf()
        list.forEach {
            callableList.add(Callable {
                doDataMigration(artifactInfo, it.toSet())
                errorSet
            })
        }
        val resultList = ThreadPoolManager.execute(callableList)
        val elapseTimeMillis = System.currentTimeMillis() - start
        logger.info("npm history data migration, success[${successSet.size}], fail[${errorSet.size}], elapse [${elapseTimeMillis / 1000}] s totally")
        val collect = resultList.stream().flatMap { set -> set.stream() }.collect(Collectors.toSet())
        return NpmDataMigrationResponse(
            "数据迁移信息展示：",
            totalDataSet.size,
            successSet.size,
            errorSet.size,
            elapseTimeMillis / 1000,
            collect
        )
    }

    fun doDataMigration(artifactInfo: NpmArtifactInfo, data: Set<String>) {
        logger.info("current Thread : ${Thread.currentThread().name}")
        data.forEach { pkgName ->
            try {
                val request = Request.Builder().url(registry.trimEnd('/').plus("/$pkgName")).get().build()
                val response = okHttpClient.newCall(request).execute()
                val searchPackageInfo = response.body()!!.byteStream().let { GsonUtils.transferInputStreamToJson(it) }
                installTgzFile(searchPackageInfo)
                response.body()!!.close()
                successSet.add(pkgName)
            } catch (exception: Exception) {
                logger.warn("failed to install $pkgName.json file", exception)
                errorSet.add(pkgName)
            }
        }
    }

    fun installTgzFile(jsonObject: JsonObject) {
        val versions = jsonObject.getAsJsonObject(VERSIONS)
        versions.keySet().forEach { version ->
            val tarball = versions.getAsJsonObject(version).getAsJsonObject(DIST).get("tarball").asString
            val request = Request.Builder().url(tarball).get().build()
            okHttpClient.newCall(request).execute()
        }
    }

    fun <T> split(set: Set<T>, count: Int = 1000): List<List<T>> {
        val list = set.toList()
        if (set.isEmpty()) {
            return emptyList()
        }
        val resultList = mutableListOf<List<T>>()
        var itemList: MutableList<T>?
        val size = set.size

        if (size < count) {
            resultList.add(list)
        } else {
            val pre = size / count
            val last = size % count
            for (i in 0 until pre) {
                itemList = mutableListOf()
                for (j in 0 until count) {
                    itemList.add(list[i * count + j])
                }
                resultList.add(itemList)
            }
            if (last > 0) {
                itemList = mutableListOf()
                for (i in 0 until last) {
                    itemList.add(list[pre * count + i])
                }
                resultList.add(itemList)
            }
        }
        return resultList
    }

    companion object {
        private const val FILE_NAME = "pkgName.json"
        val logger: Logger = LoggerFactory.getLogger(DataMigrationService::class.java)
    }
}
