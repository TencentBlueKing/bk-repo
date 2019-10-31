package com.tencent.bkrepo.generic

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID_DEFAULT_VALUE
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.generic.constant.BKREPO_META_PREFIX
import com.tencent.bkrepo.generic.constant.HEADER_OVERWRITE
import com.tencent.bkrepo.generic.constant.HEADER_SHA256
import com.tencent.bkrepo.generic.constant.HEADER_SIZE
import com.tencent.bkrepo.generic.pojo.BlockInfo
import com.tencent.bkrepo.generic.pojo.upload.UploadTransactionInfo
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit


/**
 *
 * @author: carrypan
 * @date: 2019-10-11
 */
@Disabled
@DisplayName("文件上传下载集成测试")
class IntegrationTest {

    private val client = OkHttpClient.Builder()
            .writeTimeout(1000, TimeUnit.SECONDS)
            .readTimeout(1000, TimeUnit.SECONDS)
            .build()

    private val mapper = jacksonObjectMapper()

    @Test
    @DisplayName("随机字符串简单上传/下载测试")
    fun randomStringSimpleFileTest() {
        val content = RandomStringUtils.randomAlphabetic(1024)
        val sha256 = FileDigestUtils.fileSha256(listOf(content.byteInputStream()))

        val request = Request.Builder().url("http://127.0.0.1:8001/upload/simple/test/test/root/random.txt")
                .header(AUTH_HEADER_USER_ID, AUTH_HEADER_USER_ID_DEFAULT_VALUE)
                .header(BKREPO_META_PREFIX + "key", "value")
                .header(HEADER_SHA256, sha256)
                .header(HEADER_OVERWRITE, "true")
                .put(RequestBody.create(MediaType.parse("application/octet-stream"), content))
                .build()
        checkResponse(client.newCall(request).execute(), object: TypeReference<Response<Void>>(){})

        val downloadRequest = Request.Builder().url("http://127.0.0.1:8001/download/simple/test/test/root/random.txt")
                .header(AUTH_HEADER_USER_ID, AUTH_HEADER_USER_ID_DEFAULT_VALUE)
                .build()

        val downloadResponse = client.newCall(downloadRequest).execute()

        val downloadContent = downloadResponse.body()?.string()

        Assertions.assertEquals(content, downloadContent)
    }

    @Test
    @DisplayName("简单上传/下载测试")
    fun simpleFileTest() {
        val start = System.currentTimeMillis()
        val file = File("/Users/carrypan/Downloads/opencv-master.zip")

        val uploadSha256 = FileDigestUtils.fileSha256(listOf(file.inputStream()))

        val request = Request.Builder().url("http://127.0.0.1:8001/upload/simple/test/test/root/opencv.zip")
                .header(AUTH_HEADER_USER_ID, AUTH_HEADER_USER_ID_DEFAULT_VALUE)
                .header(HEADER_OVERWRITE, "true")
                .post(RequestBody.create(MediaType.parse("application/octet-stream"), file))
                .build()
        checkResponse(client.newCall(request).execute(), object: TypeReference<Response<Void>>(){})

        val totalTime = (System.currentTimeMillis() - start) / 1000
        val uploadSpeed = file.length().toFloat() / 1024 / 1024 / totalTime
        println("上传平均速度: $uploadSpeed MB/S")

        val downloadRequest = Request.Builder().url("http://127.0.0.1:8001/download/simple/test/test/root/opencv.zip")
                .header(AUTH_HEADER_USER_ID, AUTH_HEADER_USER_ID_DEFAULT_VALUE)
                .build()

        val downloadResponse = client.newCall(downloadRequest).execute()
        val downloadSha256 = FileDigestUtils.fileSha256(listOf(downloadResponse.body()!!.byteStream()))

        Assertions.assertEquals(uploadSha256, downloadSha256)
    }

    @Test
    @DisplayName("分块上传下载测试")
    fun blockFileTest() {
        val length = 1000
        val content = RandomStringUtils.randomAlphabetic(length)
        val sha256 = FileDigestUtils.fileSha256(listOf(content.byteInputStream()))
        // 获取uploadId
        val formBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("sha256", sha256)
                .addFormDataPart("overwrite", "true")
                .build()
        val request = Request.Builder().url("http://127.0.0.1:8001/upload/precheck/test/test/root/random1000.txt")
                .header(AUTH_HEADER_USER_ID, AUTH_HEADER_USER_ID_DEFAULT_VALUE)
                .header(HEADER_OVERWRITE, "true")
                .post(formBody)
                .build()

        val checkResponse = checkResponse(client.newCall(request).execute(), object: TypeReference<Response<UploadTransactionInfo>>(){})
        val uploadId = checkResponse?.uploadId

        // 分块上传
        val inputStream = content.byteInputStream()
        val blockCount = 10
        val blockSize = length/blockCount
        val buffer = ByteArray(blockSize)
        val sha256List = mutableListOf<String>()

        for(i in 1..blockCount) {
            inputStream.read(buffer)
            val byteArrayInputStream = ByteArrayInputStream(buffer)
            val blockSha256 = FileDigestUtils.fileSha256(listOf(byteArrayInputStream))
            sha256List.add(blockSha256)

            val blockRequest = Request.Builder().url("http://127.0.0.1:8001/upload/block/$uploadId/$i")
                    .header(AUTH_HEADER_USER_ID, AUTH_HEADER_USER_ID_DEFAULT_VALUE)
                    .header(HEADER_SHA256, blockSha256)
                    .header(HEADER_SIZE, blockSize.toString())
                    .put(RequestBody.create(MediaType.parse("application/octet-stream"), buffer))
                    .build()
            checkResponse(client.newCall(blockRequest).execute(), object: TypeReference<Response<Void>>(){})
        }

        // 完成上传
        val completeFormBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("blockSha256ListStr", sha256List.joinToString(separator = ","))
                .build()

        val completeRequest = Request.Builder().url("http://127.0.0.1:8001/upload/complete/$uploadId")
                .header(AUTH_HEADER_USER_ID, AUTH_HEADER_USER_ID_DEFAULT_VALUE)
                .post(completeFormBody)
                .build()

        checkResponse(client.newCall(completeRequest).execute(), object: TypeReference<Response<Void>>(){})
        // 查询分块
        val blockInfoRequest = Request.Builder().url("http://127.0.0.1:8001/download/info/test/test/root/random1000.txt")
                .header(AUTH_HEADER_USER_ID, AUTH_HEADER_USER_ID_DEFAULT_VALUE)
                .build()
        val blockList = checkResponse(client.newCall(blockInfoRequest).execute(), object: TypeReference<Response<List<BlockInfo>>>(){})

        blockList!!.forEach {
            val downloadRequest = Request.Builder().url("http://127.0.0.1:8001/download/block/test/test/root/random1000.txt?sequence=${it.sequence}")
                    .header(AUTH_HEADER_USER_ID, AUTH_HEADER_USER_ID_DEFAULT_VALUE)
                    .build()
            val downloadResponse = client.newCall(downloadRequest).execute()
            Assertions.assertTrue(downloadResponse.isSuccessful)
            val downloadContent = downloadResponse.body()?.string()
            Assertions.assertEquals(blockSize, downloadContent!!.length)
            Assertions.assertEquals(it.sha256, FileDigestUtils.fileSha256(listOf(downloadContent.byteInputStream())))
        }
    }

    private fun <T> checkResponse(response: okhttp3.Response, typeReference: TypeReference<Response<T>>): T? {
        val content = response.body()?.string()
        println(content)
        Assertions.assertTrue(response.isSuccessful)

        val responseData = mapper.readValue<Response<T>>(content, typeReference)
        Assertions.assertTrue(responseData.isOk())

        return responseData.data
    }

}
