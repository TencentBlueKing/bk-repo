package com.tencent.bkrepo.common.storage.innercos.cl5

import com.qq.l5.L5sys
import com.qq.l5.L5sys.QosRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object CL5Utils {

    private val logger: Logger = LoggerFactory.getLogger(CL5Utils::class.java)
    private var instance: L5sys? = null
    private const val CL5_SUCCESS = 0

    fun route(cl5Info: CL5Info): RouteInfo {
        val request = QosRequest().apply {
            modId = cl5Info.modId
            cmdId = cl5Info.cmdId
        }
        val retCode = cl5Instance.ApiGetRoute(request, cl5Info.timeout)
        if (retCode != CL5_SUCCESS) {
            throw RuntimeException("Failed to get CL5 route info，return code: $retCode")
        }
        val routeInfo = RouteInfo(request.hostIp, request.hostPort)
        if (logger.isDebugEnabled) {
            logger.debug("Success to get cl5 route info: $cl5Info -> $routeInfo")
        }
        return routeInfo
    }

    private val cl5Instance: L5sys
        get() {
            if (instance == null) {
                instance = L5sys()
            }
            return instance!!
        }
}
