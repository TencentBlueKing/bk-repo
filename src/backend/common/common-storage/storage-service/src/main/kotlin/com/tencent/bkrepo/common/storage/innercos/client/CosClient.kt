/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.storage.innercos.client

import com.google.common.util.concurrent.RateLimiter
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.concurrent.ComparableFutureTask
import com.tencent.bkrepo.common.api.concurrent.PriorityCallable
import com.tencent.bkrepo.common.api.stream.ChunkedFuture
import com.tencent.bkrepo.common.api.stream.ChunkedFutureInputStream
import com.tencent.bkrepo.common.artifact.stream.DelegateInputStream
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.api.stream.EnhanceFileChunkedFutureWrapper
import com.tencent.bkrepo.common.storage.innercos.exception.InnerCosException
import com.tencent.bkrepo.common.storage.innercos.http.CosHttpClient
import com.tencent.bkrepo.common.storage.innercos.http.HttpResponseHandler
import com.tencent.bkrepo.common.storage.innercos.request.AbortMultipartUploadRequest
import com.tencent.bkrepo.common.storage.innercos.request.CheckObjectExistRequest
import com.tencent.bkrepo.common.storage.innercos.request.CompleteMultipartUploadRequest
import com.tencent.bkrepo.common.storage.innercos.request.CopyObjectRequest
import com.tencent.bkrepo.common.storage.innercos.request.CosRequest
import com.tencent.bkrepo.common.storage.innercos.request.DeleteObjectRequest
import com.tencent.bkrepo.common.storage.innercos.request.DownloadPartRequestFactory
import com.tencent.bkrepo.common.storage.innercos.request.DownloadSession
import com.tencent.bkrepo.common.storage.innercos.request.DownloadTimeWatchDog
import com.tencent.bkrepo.common.storage.innercos.request.FileCleanupChunkedFutureListener
import com.tencent.bkrepo.common.storage.innercos.request.GetObjectRequest
import com.tencent.bkrepo.common.storage.innercos.request.InitiateMultipartUploadRequest
import com.tencent.bkrepo.common.storage.innercos.request.PartETag
import com.tencent.bkrepo.common.storage.innercos.request.PutObjectRequest
import com.tencent.bkrepo.common.storage.innercos.request.RestoreObjectRequest
import com.tencent.bkrepo.common.storage.innercos.request.SessionChunkedFutureListener
import com.tencent.bkrepo.common.storage.innercos.request.UploadPartRequest
import com.tencent.bkrepo.common.storage.innercos.request.UploadPartRequestFactory
import com.tencent.bkrepo.common.storage.innercos.response.CopyObjectResponse
import com.tencent.bkrepo.common.storage.innercos.response.CosObject
import com.tencent.bkrepo.common.storage.innercos.response.PutObjectResponse
import com.tencent.bkrepo.common.storage.innercos.response.handler.CheckArchiveObjectExistResponseHandler
import com.tencent.bkrepo.common.storage.innercos.response.handler.CheckObjectExistResponseHandler
import com.tencent.bkrepo.common.storage.innercos.response.handler.CheckObjectRestoreResponseHandler
import com.tencent.bkrepo.common.storage.innercos.response.handler.CompleteMultipartUploadResponseHandler
import com.tencent.bkrepo.common.storage.innercos.response.handler.CopyObjectResponseHandler
import com.tencent.bkrepo.common.storage.innercos.response.handler.GetObjectResponseHandler
import com.tencent.bkrepo.common.storage.innercos.response.handler.InitiateMultipartUploadResponseHandler
import com.tencent.bkrepo.common.storage.innercos.response.handler.PutObjectResponseHandler
import com.tencent.bkrepo.common.storage.innercos.response.handler.SlowLogHandler
import com.tencent.bkrepo.common.storage.innercos.response.handler.UploadPartResponseHandler
import com.tencent.bkrepo.common.storage.innercos.response.handler.VoidResponseHandler
import com.tencent.bkrepo.common.storage.innercos.retry
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import com.tencent.bkrepo.common.storage.util.createNewOutputStream
import okhttp3.Request
import org.apache.commons.logging.LogFactory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.max

/**
 * Cos Client
 */
@Suppress("UnstableApiUsage")
class CosClient(val credentials: InnerCosCredentials) {
    private val config: ClientConfig = ClientConfig(credentials)

    /**
     * 分块下载使用的执行器。可以为null,为null则不使用分块下载
     * */
    private val downloadThreadPool: ThreadPoolExecutor? = if (config.downloadWorkers > 0) {
        val namedThreadFactory = ThreadFactoryBuilder().setNameFormat("CosClient-${credentials.key}-%d").build()
        // 因为客户端存储凭证可以动态更新，所以为了避免产生过多线程数，这里设置allowCoreThreadTimeOut为true
        ThreadPoolExecutor(
            config.downloadWorkers,
            config.downloadWorkers,
            60,
            TimeUnit.SECONDS,
            PriorityBlockingQueue(),
            namedThreadFactory,
        ).apply { this.allowCoreThreadTimeOut(true) }
    } else {
        null
    }

    private val watchDog: DownloadTimeWatchDog? = if (downloadThreadPool != null) {
        DownloadTimeWatchDog(
            credentials.key.toString(),
            downloadThreadPool,
            config.downloadTimeHighWaterMark,
            config.downloadTimeLowWaterMark,
        )
    } else {
        null
    }

    private val useChunkedLoad = (watchDog != null) && (downloadThreadPool != null)

    private val fastFallbackTimeout = config.timeout shr 1

    /**
     * 单连接获取文件
     */
    fun getObject(cosRequest: GetObjectRequest): CosObject {
        val httpRequest = buildHttpRequest(cosRequest)
        return CosHttpClient.execute(httpRequest, GetObjectResponseHandler())
    }

    /**
     * 分块下载文件
     * 分块下载文件，可以利用线程并行，多连接同时下载，以突破单连接的限速。
     * 以下几种情况不使用分块下载，转换成普通下载
     * 1. 未指定下载文件范围
     * 2. 未设置downloadWorkers
     * 3. 小文件
     * 4. 分块下载处理失败
     * */
    fun getObjectByChunked(cosRequest: GetObjectRequest): CosObject {
        with(cosRequest) {
            if (rangeStart == null || rangeEnd == null || !useChunkedLoad) {
                return getObject(cosRequest)
            }
            val len = rangeEnd - rangeStart + 1
            if (!shouldUseMultipart(len)) {
                return getObject(cosRequest)
            }
            if (!checkObjectExist(CheckObjectExistRequest(key))) {
                return CosObject(null, null)
            }
            if (!watchDog!!.isHealthy()) {
                return getObject(cosRequest)
            }
            return try {
                val inputStream = chunkedLoad(key, rangeStart, rangeEnd, getTempPath(), downloadThreadPool!!)
                CosObject(eTag = null, inputStream = inputStream)
            } catch (e: Exception) {
                logger.warn("Chunked load failed,fallback to use common get object", e)
                // fallback to common get object
                getObject(cosRequest)
            }
        }
    }

    /**
     * 单连接上传InputStream
     */
    fun putStreamObject(key: String, inputStream: InputStream, length: Long): PutObjectResponse {
        return inputStream.use { putObject(PutObjectRequest(key, it, length)) }
    }

    /**
     * 分片上传
     */
    fun putFileObject(key: String, file: File, storageClass: String? = null): PutObjectResponse {
        if (!file.exists()) {
            throw InnerCosException("File[$file] does not exist.")
        }
        val length = file.length()
        return if (shouldUseMultipart(length)) {
            multipartUpload(key, file, storageClass)
        } else {
            putObject(PutObjectRequest(key, file.inputStream(), length, storageClass))
        }
    }

    /**
     * 单连接上传InputStream
     */
    private fun putObject(cosRequest: PutObjectRequest): PutObjectResponse {
        val httpRequest = buildHttpRequest(cosRequest)
        return CosHttpClient.execute(httpRequest, PutObjectResponseHandler().enableSpeedSlowLog())
    }

    /**
     * 当文件不存在时，也会执行成功，返回200
     */
    fun deleteObject(cosRequest: DeleteObjectRequest) {
        val httpRequest = buildHttpRequest(cosRequest)
        CosHttpClient.execute(httpRequest, VoidResponseHandler())
    }

    /**
     * 恢复文件
     */
    fun restoreObject(cosRequest: RestoreObjectRequest) {
        val httpRequest = buildHttpRequest(cosRequest)
        CosHttpClient.execute(httpRequest, VoidResponseHandler())
    }

    /**
     * 恢复文件
     */
    fun checkObjectRestore(cosRequest: CheckObjectExistRequest): Boolean {
        val httpRequest = buildHttpRequest(cosRequest)
        return CosHttpClient.execute(httpRequest, CheckObjectRestoreResponseHandler())
    }

    /**
     * 检查文件是否存在
     * 文件不存在时，cos返回404
     */
    fun checkObjectExist(cosRequest: CheckObjectExistRequest): Boolean {
        val httpRequest = buildHttpRequest(cosRequest)
        return CosHttpClient.execute(httpRequest, CheckObjectExistResponseHandler())
    }

    /**
     * 检查归档文件是否存在
     * 文件不存在时，cos返回404
     */
    fun checkArchiveObjectExist(cosRequest: CheckObjectExistRequest): Boolean {
        val httpRequest = buildHttpRequest(cosRequest)
        return CosHttpClient.execute(httpRequest, CheckArchiveObjectExistResponseHandler())
    }

    /**
     * 复制文件
     */
    fun copyObject(cosRequest: CopyObjectRequest): CopyObjectResponse {
        val httpRequest = buildHttpRequest(cosRequest)
        return CosHttpClient.execute(httpRequest, CopyObjectResponseHandler())
    }

    private fun multipartUpload(key: String, file: File, storageClass: String?): PutObjectResponse {
        // 计算分片大小
        val length = file.length()
        val optimalPartSize = calculateOptimalPartSize(length, true)
        // 获取uploadId
        val uploadId = initiateMultipartUpload(key, storageClass)
        // 生成分片请求
        val factory = UploadPartRequestFactory(key, uploadId, optimalPartSize, file, length)
        val futureList = mutableListOf<Future<PartETag>>()
        while (factory.hasMoreRequests()) {
            val uploadPartRequest = factory.nextUploadPartRequest()
            val future = executors.submit(uploadPart(uploadPartRequest))
            futureList.add(future)
        }
        // 等待所有完成
        try {
            val partETagList = futureList.map { it.get() }
            return completeMultipartUpload(key, uploadId, partETagList)
        } catch (exception: IOException) {
            cancelFutureList(futureList)
            abortMultipartUpload(key, uploadId)
            throw exception
        }
    }

    private fun initiateMultipartUpload(key: String, storageClass: String?): String {
        val cosRequest = InitiateMultipartUploadRequest(key, storageClass)
        val httpRequest = buildHttpRequest(cosRequest)
        return CosHttpClient.execute(httpRequest, InitiateMultipartUploadResponseHandler())
    }

    private fun uploadPart(cosRequest: UploadPartRequest): Callable<PartETag> {
        return Callable {
            retry(RETRY_COUNT) {
                val httpRequest = buildHttpRequest(cosRequest)
                val uploadPartResponse =
                    CosHttpClient.execute(httpRequest, UploadPartResponseHandler().enableSpeedSlowLog())
                PartETag(cosRequest.partNumber, uploadPartResponse.eTag)
            }
        }
    }

    private fun completeMultipartUpload(
        key: String,
        uploadId: String,
        partETagList: List<PartETag>,
    ): PutObjectResponse {
        retry(RETRY_COUNT) {
            val cosRequest = CompleteMultipartUploadRequest(key, uploadId, partETagList)
            val httpRequest = buildHttpRequest(cosRequest)
            return CosHttpClient.execute(
                httpRequest,
                CompleteMultipartUploadResponseHandler().enableTimeSlowLog(config.slowLogTime),
            )
        }
    }

    private fun abortMultipartUpload(key: String, uploadId: String) {
        val cosRequest = AbortMultipartUploadRequest(key, uploadId)
        val httpRequest = buildHttpRequest(cosRequest)
        try {
            return CosHttpClient.execute(httpRequest, VoidResponseHandler())
        } catch (ignored: IOException) {
        }
    }

    private fun <T> cancelFutureList(futures: List<Future<T>>) {
        for (future in futures) {
            if (!future.isDone) {
                future.cancel(false)
            }
        }
    }

    private fun buildHttpRequest(cosRequest: CosRequest): Request {
        cosRequest.sign(credentials, config)
        return Request.Builder()
            .method(cosRequest.method.name, cosRequest.buildRequestBody())
            .url(cosRequest.url)
            .apply { cosRequest.headers.forEach { (key, value) -> this.header(key, value) } }
            .build()
    }

    private fun shouldUseMultipart(length: Long): Boolean {
        return length > config.multipartThreshold
    }

    private fun calculateOptimalPartSize(length: Long, uploadFlag: Boolean): Long {
        val (maxParts, minimumPartSize) = if (uploadFlag) {
            Pair(config.maxUploadParts, config.minimumUploadPartSize)
        } else {
            Pair(config.maxDownloadParts, config.minimumDownloadPartSize)
        }
        val optimalPartSize = length.toDouble() / maxParts
        return max(ceil(optimalPartSize).toLong(), minimumPartSize)
    }

    private fun <T> HttpResponseHandler<T>.enableSpeedSlowLog(): SlowLogHandler<T> {
        val ignoreFileSize = config.slowLogSpeed * SLOW_LOG_SPEED_IGNORE_FILESIZE_FACTOR
        return SlowLogHandler(this, config.slowLogSpeed, -1, ignoreFileSize)
    }

    /**
     * @param time 慢日志时间，请求超过该时间，则记录慢日志
     * */
    private fun <T> HttpResponseHandler<T>.enableTimeSlowLog(time: Long): SlowLogHandler<T> {
        return SlowLogHandler(this, -1, time)
    }

    /**
     * 分块下载文件
     * 对下载的文件进行合适的分块，按顺序与创建时间为优先级下载。
     * 越靠前分块，越早创建的分块，则会越快的执行下载任务。
     * 分块下载失败，会进行一定次数的重试。
     * 下载结束后会进行临时文件清理，如果还有下载任务在执行中，
     * 则延迟调度到下一次进行清理，反复，直到临时文件清理完毕。
     * @param key 需要下载的文件名
     * @param start 下载的开始位置
     * @param end 下载的结束位置
     * @param dir 下载使用的文件路径
     * @param executor 下载使用的优先级执行器
     * */
    private fun chunkedLoad(key: String, start: Long, end: Long, dir: Path, executor: ThreadPoolExecutor): InputStream {
        val len = end - start - 1
        val optimalPartSize = calculateOptimalPartSize(len, false)
        val factory = DownloadPartRequestFactory(key, optimalPartSize, start, end)
        val futureList = mutableListOf<ChunkedFuture<File>>()
        val activeCount = AtomicInteger()
        val session = DownloadSession(activeCount = activeCount)
        val tempRootPath = Paths.get(dir.toString(), session.id)
        watchDog!!.add(session)
        try {
            /*
            * 按时间顺序进行优先级下载。同时保持一段间距，以让新进来的连接可以插队。
            * 但要注意：
            *   1. 间隔距离过长：可允许的插入块则越多，导致客户端读取超时
            *   2. 间隔距离过短：可允许插入的块则越少，导致新连接的下载任务无法插入。（此时会降级为普通下载）
            * 由于存在降级处理，所以在参数调整上，downloadTaskInterval偏向于较小值。
            * */
            var i = 0
            var priority = System.currentTimeMillis()
            val rateLimiter = RateLimiter.create(config.qps.toDouble())
            while (factory.hasMoreRequests()) {
                val downloadPartRequest = factory.nextDownloadPartRequest()
                val task = DownloadTask(i, downloadPartRequest, tempRootPath, session, priority, rateLimiter)
                val futureTask = ComparableFutureTask(task)
                executor.execute(futureTask)
                val futureWrapper = EnhanceFileChunkedFutureWrapper(futureTask) {
                    val getRequest = task.getComparable().downloadPartRequest
                    this.getObject(getRequest).inputStream ?: throw InnerCosException("not found $getRequest")
                }
                futureList.add(futureWrapper)
                priority += config.downloadTaskInterval
                if (i == 0) {
                    /*
                    * 快速降级
                    * 较短的时间内，第一个分块不能准备好，则抛出TimeoutException，由外层方法降级普通上传。
                    * 可以避免在分片下载忙碌时，继续往线程池中添加任务，且长时间等待。
                    * */
                    futureTask.get(fastFallbackTimeout, TimeUnit.MILLISECONDS)
                }
                i++
            }

            val chunkedFutureListeners = listOf(
                FileCleanupChunkedFutureListener(),
                SessionChunkedFutureListener(session),
            )
            val chunkedInput = ChunkedFutureInputStream(futureList, config.timeout, chunkedFutureListeners)
            return object : DelegateInputStream(chunkedInput) {
                override fun close() {
                    super.close()
                    cleanup(futureList, activeCount, tempRootPath)
                    session.closed = true
                }
            }
        } catch (exception: Exception) {
            logger.info("load failed: ", exception)
            cleanup(futureList, activeCount, tempRootPath)
            session.closed = true
            throw exception
        }
    }

    /**
     * 清理资源
     * */
    private fun cleanup(
        futureList: MutableList<ChunkedFuture<File>>,
        activeCount: AtomicInteger,
        tempRootPath: Path,
    ) {
        cancelFutureList(futureList)
        try {
            cleanTempPath(activeCount, tempRootPath)
        } catch (e: Exception) {
            logger.error("Failed to delete cos temp chunked download dir[$tempRootPath]")
        }
    }

    /**
     * 清理临时目录
     * 如果还有任务执行中，则延迟调度到下一次执行，反复直到临时目录被清理
     * */
    private fun cleanTempPath(activeCount: AtomicInteger, path: Path) {
        val count = activeCount.get()
        if (count == 0) {
            // 需要所有任务完成后删除临时路径
            path.toFile().deleteRecursively()
            logger.info("Delete cos temp downloading file dir $path recursively")
            return
        }
        logger.info("Path[$path] has downloading count $count.")
        val cleanTask = Runnable { cleanTempPath(activeCount, path) }
        cleanerExecutors.schedule(cleanTask, DOWNLOADING_TEMP_FILE_CLEANUP_DELAY, TimeUnit.MILLISECONDS)
    }

    /**
     * 获取临时目录
     * */
    private fun getTempPath(): Path {
        return Paths.get(credentials.upload.location)
    }

    /**
     * 分块下载任务
     * @param seq 分块序列号
     * @param downloadPartRequest 分块下载请求
     * @param rootPath 分块下载使用的路径
     * @param session 活动任务计数器
     * @param priority 分块下载任务优先级
     * */
    inner class DownloadTask(
        private val seq: Int,
        val downloadPartRequest: GetObjectRequest,
        private val rootPath: Path,
        private val session: DownloadSession,
        private val priority: Long,
        private val rateLimiter: RateLimiter,
    ) : PriorityCallable<File, DownloadTask>() {

        override fun call(): File {
            // 任务可以取消，所以可能会产生中断异常，如果调用方获取结果，则可以捕获异常。
            // 但是通常是由于调用方异常而提前终止相关任务，所以这里产生的中断异常大部分情况下无影响。
            retry(RETRY_COUNT) {
                rateLimiter.acquire()
                session.activeCount.incrementAndGet()
                // 为防止重试导致文件名重复
                val fileName = "$DOWNLOADING_CHUNkED_PREFIX${seq}_${priority}_${it}$DOWNLOADING_CHUNkED_SUFFIX"
                val filePath = rootPath.resolve(fileName)
                try {
                    val inputStream = getObject(downloadPartRequest).inputStream
                        ?: throw IOException("not found object chunk")
                    return inputStream.use { write2File(filePath, it) }
                } catch (e: Exception) {
                    // 记录下错误日志，错误仍要抛出，触发重试
                    logger.warn("Download chunk file[$filePath] failed", e)
                    throw e
                } finally {
                    session.activeCount.decrementAndGet()
                }
            }
        }

        override fun getComparable(): DownloadTask {
            return this
        }

        override fun compareTo(other: PriorityCallable<File, DownloadTask>): Int {
            return priority.compareTo(other.getComparable().priority)
        }

        /**
         * 根据输入流写入临时文件，并返回文件
         * @param filePath 文件写入路径
         * @param inputStream 源数据流
         * */
        private fun write2File(filePath: Path, inputStream: InputStream): File {
            val throughput = measureThroughput {
                filePath.createNewOutputStream().use {
                    inputStream.copyTo(it)
                }
            }
            logger.info("File[$filePath] download success, $throughput")
            return filePath.toFile()
        }
    }

    companion object {
        private val logger = LogFactory.getLog(CosClient::class.java)
        private val executors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2)
        private val cleanerExecutors = Executors.newSingleThreadScheduledExecutor()
        private const val RETRY_COUNT = 5
        private const val SLOW_LOG_SPEED_IGNORE_FILESIZE_FACTOR = 5L
        private const val DOWNLOADING_TEMP_FILE_CLEANUP_DELAY = 1000L // 1s
        private const val DOWNLOADING_CHUNkED_PREFIX = "downloading_"
        private const val DOWNLOADING_CHUNkED_SUFFIX = ".temp"
    }
}
