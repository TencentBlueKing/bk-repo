package com.tencent.bkrepo.repository.service.ilnet

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.toJson
import com.tencent.bkrepo.repository.client.IlnetLinkTrafficClient
import com.tencent.bkrepo.repository.config.IlnetProperties
import com.tencent.bkrepo.repository.message.RepositoryMessageCode
import com.tencent.bkrepo.repository.pojo.ilnet.LinkTrafficRecordLog
import com.tencent.bkrepo.repository.pojo.ilnet.LinkTrafficRequest
import com.tencent.bkrepo.repository.pojo.ilnet.LinkTrafficResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class IlnetLinkTrafficService(
    private val properties: IlnetProperties,
    private val ilnetLinkTrafficClient: IlnetLinkTrafficClient,
) {
    fun queryTraffic(request: LinkTrafficRequest): LinkTrafficResponse {
        checkConfig()
        require(request.mac.isNotBlank()) { "mac is required" }
        request.congestionThreshold?.let { threshold ->
            require(threshold in 10f..100f) {
                "congestion_threshold must be between 10 and 100"
            }
        }
        val response = ilnetLinkTrafficClient.queryTraffic(request)
        if (properties.collectByLog) {
            logger.info(toJson(LinkTrafficRecordLog(request, response)))
        }
        return response
    }

    fun health() {
        checkHealthConfig()
        ilnetLinkTrafficClient.health()
    }

    private fun checkHealthConfig() {
        checkConfig(properties.healthPath)
    }

    private fun checkConfig() {
        checkConfig(properties.trafficPath)
    }

    private fun checkConfig(path: String) {
        val configValues = listOf(
            properties.server,
            properties.paasid,
            properties.token,
            path,
        )
        if (configValues.any { it.isBlank() }) {
            throw ErrorCodeException(RepositoryMessageCode.ILNET_CONFIG_ERROR)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(IlnetLinkTrafficService::class.java)
    }
}
