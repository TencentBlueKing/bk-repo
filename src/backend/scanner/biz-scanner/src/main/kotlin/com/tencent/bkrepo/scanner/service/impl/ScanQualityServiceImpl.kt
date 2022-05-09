package com.tencent.bkrepo.scanner.service.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.scanner.CRITICAL
import com.tencent.bkrepo.scanner.CVE_CRITICAL_COUNT
import com.tencent.bkrepo.scanner.CVE_HIGH_COUNT
import com.tencent.bkrepo.scanner.CVE_LOW_COUNT
import com.tencent.bkrepo.scanner.CVE_MEDIUM_COUNT
import com.tencent.bkrepo.scanner.FORBID_QUALITY_UNPASS
import com.tencent.bkrepo.scanner.FORBID_SCAN_UNFINISHED
import com.tencent.bkrepo.scanner.HIGH
import com.tencent.bkrepo.scanner.LOW
import com.tencent.bkrepo.scanner.MEDIUM
import com.tencent.bkrepo.scanner.dao.ScanPlanDao
import com.tencent.bkrepo.scanner.pojo.request.ScanQualityCreateRequest
import com.tencent.bkrepo.scanner.pojo.response.ScanQualityCheckedDetail
import com.tencent.bkrepo.scanner.pojo.response.ScanQualityResponse
import com.tencent.bkrepo.scanner.service.ScanQualityService
import org.springframework.stereotype.Service

@Service
class ScanQualityServiceImpl(
    private val scanPlanDao: ScanPlanDao
) : ScanQualityService {
    override fun getScanQuality(scanId: String): ScanQualityResponse {
        val scanPlan = scanPlanDao.get(scanId)
        return scanPlan.scanQuality.convertToScanQualityResponse()
    }

    override fun createScanQuality(scanId: String, request: ScanQualityCreateRequest): Boolean {
        scanPlanDao.get(scanId)
        val qualityMap = request.convertToMap().apply {
            if (this.isEmpty()) {
                return true
            }
        }
        scanPlanDao.updateScanPlanQuality(scanId, qualityMap)
        return true
    }

    override fun updateScanQuality(scanId: String, request: ScanQualityCreateRequest): Boolean {
        scanPlanDao.get(scanId)
        val qualityMap = request.convertToMap().apply {
            if (this.isEmpty()) {
                return true
            }
        }
        scanPlanDao.updateScanPlanQuality(scanId, qualityMap)
        return true
    }

    override fun checkScanQualityRedLine(planId: String, scanResultOverview: Map<String, Number>): Boolean {
        val scanQuality = getScanQuality(planId)
        scanQualityRedLineList.forEach { redLine ->
            val scanIndex = scanResultOverview[redLine]
            val qualityIndex = scanQuality.getScanQualityRedLineByLevel(redLine)
            if (scanIndex != null && qualityIndex != null
                && scanIndex.toInt() > qualityIndex) {
                return false
            }
        }
        return true
    }

    override fun checkScanQualityRedLineDetail(
        planId: String,
        scanResultOverview: Map<String, Number>
    ): ScanQualityCheckedDetail {
        val scanPlan = scanPlanDao.get(planId)
        val scanQuality = scanPlan.scanQuality.convertToScanQualityResponse()
        if (!scanQuality.forbidQualityUnPass) return ScanQualityCheckedDetail(qualityStatus = true)
        val detailsMap = mutableMapOf<String, ScanQualityCheckedDetail.ScanQualityCheckedStatus>()
        scanQualityRedLineList.forEach { redLine ->
            val scanIndex = scanResultOverview[redLine]
            val qualityIndex = scanQuality.getScanQualityRedLineByLevel(redLine)
            if (scanIndex != null && qualityIndex != null) {
                detailsMap[redLine] = ScanQualityCheckedDetail.ScanQualityCheckedStatus(
                    status = scanIndex.toInt() <= qualityIndex,
                    require = scanQuality.getScanQualityRedLineByLevel(redLine),
                    actual = scanIndex.toInt()
                )
            }
        }
        return ScanQualityCheckedDetail(
            criticalStatus = detailsMap[CVE_CRITICAL_COUNT],
            highStatus = detailsMap[CVE_HIGH_COUNT],
            mediumStatus = detailsMap[CVE_MEDIUM_COUNT],
            lowStatus = detailsMap[CVE_LOW_COUNT],
            qualityStatus = (
                detailsMap[CVE_CRITICAL_COUNT]?.status
                    ?: true
                    && detailsMap[CVE_HIGH_COUNT]?.status
                    ?: true
                    && detailsMap[CVE_MEDIUM_COUNT]?.status
                    ?: true
                    && detailsMap[CVE_LOW_COUNT]?.status
                    ?: true
                )
        )
    }

    companion object {
        val scanQualityRedLineList = listOf(CVE_CRITICAL_COUNT, CVE_HIGH_COUNT, CVE_MEDIUM_COUNT, CVE_LOW_COUNT)
        fun ScanQualityCreateRequest.convertToMap(): Map<String, Any?> {
            val map = mutableMapOf<String, Any?>()
            this.critical.let {
                if (it != null) {
                    require(it >= 0) { throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, CRITICAL) }
                }
                map[CRITICAL] = it
            }
            this.high.let {
                if (it != null) {
                    require(it >= 0) { throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, HIGH) }
                }
                map[HIGH] = it
            }
            this.medium.let {
                if (it != null) {
                    require(it >= 0) { throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, MEDIUM) }
                }
                map[MEDIUM] = it
            }
            this.low.let {
                if (it != null) {
                    require(it >= 0) { throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, LOW) }
                }
                map[LOW] = it
            }
            this.forbidScanUnFinished?.let { map[FORBID_SCAN_UNFINISHED] = it }
            this.forbidQualityUnPass?.let { map[FORBID_QUALITY_UNPASS] = it }
            return map
        }

        fun Map<String, Any>.convertToScanQualityResponse(): ScanQualityResponse {
            return ScanQualityResponse(
                critical = this[CRITICAL] as? Int,
                high = this[HIGH] as? Int,
                medium = this[MEDIUM] as? Int,
                low = this[LOW] as? Int,
                forbidScanUnFinished = this[FORBID_SCAN_UNFINISHED] as? Boolean ?: false,
                forbidQualityUnPass = this[FORBID_QUALITY_UNPASS] as? Boolean ?: false
            )
        }
    }
}