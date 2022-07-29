package com.tencent.bkrepo.scanner.service

import com.tencent.bkrepo.scanner.pojo.request.LicenseScanQualityUpdateRequest
import com.tencent.bkrepo.scanner.pojo.response.LicenseScanQualityResponse

interface LicenseScanQualityService {
    /**
     * 获取许可证扫描方案质量规则
     */
    fun getScanQuality(planId: String): LicenseScanQualityResponse

    /**
     * 更新方案质量规则
     */
    fun updateScanQuality(planId: String, request: LicenseScanQualityUpdateRequest): Boolean

    /**
     * 检查是否通过质量规则
     */
    fun checkLicenseScanQualityRedLine(scanQuality: Map<String, Any>, scanResultOverview: Map<String, Number>): Boolean
}
