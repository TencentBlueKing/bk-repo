package com.tencent.bkrepo.scanner.service

import com.tencent.bkrepo.scanner.pojo.request.ScanQualityCreateRequest
import com.tencent.bkrepo.scanner.pojo.response.ScanQualityCheckedDetail
import com.tencent.bkrepo.scanner.pojo.response.ScanQualityResponse

interface ScanQualityService {
    fun getScanQuality(scanId: String): ScanQualityResponse

    fun createScanQuality(scanId: String, request: ScanQualityCreateRequest): Boolean

    fun updateScanQuality(scanId: String, request: ScanQualityCreateRequest): Boolean

    /**
     * 检查是否通过质量规则
     */
    fun checkScanQualityRedLine(planId: String, scanResultOverview: Map<String, Number>): Boolean

    fun checkScanQualityRedLineDetail(planId: String, scanResultOverview: Map<String, Number>): ScanQualityCheckedDetail

}