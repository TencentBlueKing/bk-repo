package com.tencent.bkrepo.scanner.service

import com.tencent.bkrepo.scanner.pojo.request.ScanQualityUpdateRequest
import com.tencent.bkrepo.scanner.pojo.response.ScanQualityCheckedDetail
import com.tencent.bkrepo.scanner.pojo.response.ScanQualityResponse

interface ScanQualityService {
    /**
     * 获取方案质量规则
     */
    fun getScanQuality(planId: String): ScanQualityResponse

    /**
     * 更新方案质量规则
     */
    fun updateScanQuality(planId: String, request: ScanQualityUpdateRequest): Boolean

    /**
     * 检查是否通过质量规则
     */
    fun checkScanQualityRedLine(planId: String, scanResultOverview: Map<String, Number>): Boolean

    fun checkScanQualityRedLineDetail(planId: String, scanResultOverview: Map<String, Number>): ScanQualityCheckedDetail
}
