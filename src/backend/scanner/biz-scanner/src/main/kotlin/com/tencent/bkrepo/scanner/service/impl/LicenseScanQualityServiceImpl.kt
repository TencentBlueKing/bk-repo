package com.tencent.bkrepo.scanner.service.impl

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.scanner.pojo.scanner.LicenseNature
import com.tencent.bkrepo.common.scanner.pojo.scanner.LicenseOverviewKey
import com.tencent.bkrepo.scanner.component.ScannerPermissionCheckHandler
import com.tencent.bkrepo.scanner.dao.ScanPlanDao
import com.tencent.bkrepo.scanner.pojo.request.LicenseScanQualityUpdateRequest
import com.tencent.bkrepo.scanner.pojo.response.LicenseScanQualityResponse
import com.tencent.bkrepo.scanner.service.LicenseScanQualityService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LicenseScanQualityServiceImpl(
    private val permissionCheckHandler: ScannerPermissionCheckHandler,
    private val scanPlanDao: ScanPlanDao
) : LicenseScanQualityService {
    override fun getScanQuality(planId: String): LicenseScanQualityResponse {
        val scanPlan = scanPlanDao.get(planId)
        permissionCheckHandler.checkProjectPermission(scanPlan.projectId, PermissionAction.MANAGE)
        return LicenseScanQualityResponse.create(scanPlan.scanQuality)
    }

    override fun updateScanQuality(planId: String, request: LicenseScanQualityUpdateRequest): Boolean {
        val scanPlan = scanPlanDao.get(planId)
        permissionCheckHandler.checkProjectPermission(scanPlan.projectId, PermissionAction.MANAGE)
        val qualityMap = request.toMap().ifEmpty { return true }
        scanPlanDao.updateScanPlanQuality(planId, qualityMap)
        return true
    }

    override fun checkLicenseScanQualityRedLine(
        scanQuality: Map<String, Any>,
        scanResultOverview: Map<String, Number>
    ): Boolean {
        LicenseNature.values().forEach {
            if (checkRedLine(it, scanResultOverview, scanQuality)) {
                return false
            }
        }
        return true
    }

    /**
     * 判断扫描结果中对应的数量是否符合质量规则中的设定
     * 质量规则设定为 true，对应扫描结果为 0 才通过
     */
    private fun checkRedLine(
        overviewKey: LicenseNature,
        overview: Map<String, Number>,
        quality: Map<String, Any>
    ): Boolean {
        val count = overview[LicenseOverviewKey.overviewKeyOf(overviewKey.natureName)]?.toLong() ?: 0L
        val redLine = quality[overviewKey.level] as Boolean
        if (logger.isDebugEnabled) {
            logger.debug(
                "overviewKey:[$overviewKey] count:[$count] redLine:[$redLine] result:[${count != 0L && redLine}]"
            )
        }
        return count != 0L && redLine
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LicenseScanQualityServiceImpl::class.java)
    }
}
