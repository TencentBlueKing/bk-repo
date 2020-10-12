package com.tencent.bkrepo.dockerapi.client

import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bkrepo.common.api.util.JsonUtils.objectMapper
import com.tencent.bkrepo.dockerapi.config.SystemConfig
import com.tencent.bkrepo.dockerapi.util.HttpUtils
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class BkEsbClient @Autowired constructor(
    private val systemConfig: SystemConfig
) {

    fun getHarborApiKeyFromApigw(): PaasResponse<PublicKey> {
        logger.info("getHarborApiKeyFromApigw")
        val url = "${systemConfig.apigwServer}/apigw/managementapi/get_api_public_key/?api_name=harbor_api"
        logger.info("request url: $url")
        val httpRequest = Request.Builder().url(url)
            .header("Bk-App-Code", systemConfig.appCode!!)
            .header("Bk-App-Secret", systemConfig.appSecret!!)
            .get().build()
        val apiResponse = HttpUtils.doRequest(httpRequest, 2)
        return objectMapper.readValue(apiResponse.content)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BkEsbClient::class.java)
    }
}