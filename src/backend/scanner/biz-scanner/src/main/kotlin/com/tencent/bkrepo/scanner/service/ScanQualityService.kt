package com.tencent.bkrepo.scanner.service

import com.tencent.bkrepo.scanner.pojo.request.ScanQualityCreateRequest
import com.tencent.bkrepo.scanner.pojo.response.ScanQualityCheckedDetail
import com.tencent.bkrepo.scanner.pojo.response.ScanQualityResponse

interface ScanQualityService {
    fun getScanQuality(scanId: String): ScanQualityResponse

    fun createScanQuality(scanId: String, request: ScanQualityCreateRequest): Boolean

    fun updateScanQuality(scanId: String, request: ScanQualityCreateRequest): Boolean

    fun checkScanQualityRedLine(scanId: String, scanResultOverview: Map<String, Long>): Boolean

    fun checkScanQualityRedLineDetail(scanId: String, scanResultOverview: Map<String, Long>): ScanQualityCheckedDetail

}