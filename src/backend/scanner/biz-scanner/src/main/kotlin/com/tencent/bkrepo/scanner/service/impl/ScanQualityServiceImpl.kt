package com.tencent.bkrepo.scanner.service.impl

import com.tencent.bkrepo.scanner.CVE_CRITICAL
import com.tencent.bkrepo.scanner.CVE_CRITICAL_COUNT
import com.tencent.bkrepo.scanner.CVE_HIGH
import com.tencent.bkrepo.scanner.CVE_HIGH_COUNT
import com.tencent.bkrepo.scanner.CVE_LOW
import com.tencent.bkrepo.scanner.CVE_LOW_COUNT
import com.tencent.bkrepo.scanner.CVE_MEDIUM_COUNT
import com.tencent.bkrepo.scanner.CVE_MID
import com.tencent.bkrepo.scanner.FORBID_QUALITY_UNPASS
import com.tencent.bkrepo.scanner.FORBID_SCAN_UNFINISHED
import com.tencent.bkrepo.scanner.dao.ScanPlanDao
import com.tencent.bkrepo.scanner.exception.ScannerNotFoundException
import com.tencent.bkrepo.scanner.pojo.request.ScanQualityCreateRequest
import com.tencent.bkrepo.scanner.pojo.response.ScanQualityResponse
import com.tencent.bkrepo.scanner.service.ScanQualityService
import org.springframework.stereotype.Service

@Service
class ScanQualityServiceImpl(
        private val scanPlanDao: ScanPlanDao
) : ScanQualityService {
    override fun getScanQuality(scanId: String): ScanQualityResponse {
        val scanPlan = scanPlanDao.findById(scanId)
                ?: throw ScannerNotFoundException("scan plan not found by id: $scanId")
        return scanPlan.scanQuality.convertToScanQualityResponse()
    }

    override fun createScanQuality(scanId: String, request: ScanQualityCreateRequest): Boolean {
        scanPlanDao.findById(scanId)
                ?: throw ScannerNotFoundException("scan plan not found by id: $scanId")
        val qualityMap = request.convertToMap().apply {
            if (this.isEmpty()) {
                return true
            }
        }
        scanPlanDao.updateScanPlanQuality(scanId, qualityMap)
        return true
    }

    override fun updateScanQuality(scanId: String, request: ScanQualityCreateRequest): Boolean {
        scanPlanDao.findById(scanId)
                ?: throw ScannerNotFoundException("scan plan not found by id: $scanId")
        val qualityMap = request.convertToMap().apply {
            if (this.isEmpty()) {
                return true
            }
        }
        scanPlanDao.updateScanPlanQuality(scanId, qualityMap)
        return true
    }

    override fun checkScanQualityRedLine(scanId: String, scanResultOverview: Map<String, Long>): Boolean {
        val scanPlan = scanPlanDao.findById(scanId)
                ?: throw ScannerNotFoundException("scan plan not found by id: $scanId")
        val scanQuality = scanPlan.scanQuality.convertToScanQualityResponse()
        if (!scanQuality.forbidQualityUnPass) return true
        scanQualityRedLineList.forEach { redLine ->
            scanResultOverview[redLine]?.let {
                if (it > scanQuality.getScanQualityRedLineByLevel(redLine)) {
                    return false
                } } }
        return true
    }

    companion object{
        val scanQualityRedLineList = listOf(CVE_CRITICAL_COUNT, CVE_HIGH_COUNT, CVE_MEDIUM_COUNT, CVE_LOW_COUNT)
        fun ScanQualityCreateRequest.convertToMap(): Map<String, Any?> {
            val map = mutableMapOf<String, Any?>()
            this.critical.let { map[CVE_CRITICAL] = it }
            this.high.let { map[CVE_HIGH] = it }
            this.medium.let { map[CVE_MID] = it }
            this.low.let { map[CVE_LOW] = it }
            this.forbidScanUnFinished?.let { map[FORBID_SCAN_UNFINISHED] = it }
            this.forbidQualityUnPass?.let { map[FORBID_QUALITY_UNPASS] = it }
            return map
        }

        fun Map<String, Any>.convertToScanQualityResponse(): ScanQualityResponse {
            return ScanQualityResponse(
                    cveCritical = this[CVE_CRITICAL] as? Int,
                    cveHigh = this[CVE_HIGH] as? Int,
                    cveMid =  this[CVE_MID] as? Int,
                    cveLow = this[CVE_LOW] as? Int,
                    forbidScanUnFinished = this[FORBID_SCAN_UNFINISHED] as? Boolean ?: false,
                    forbidQualityUnPass = this[FORBID_QUALITY_UNPASS] as? Boolean ?: false
            )
        }
    }
}