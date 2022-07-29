package com.tencent.bkrepo.scanner.controller.user

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.scanner.pojo.request.LicenseScanQualityUpdateRequest
import com.tencent.bkrepo.scanner.pojo.response.LicenseScanQualityResponse
import com.tencent.bkrepo.scanner.service.LicenseScanQualityService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/scan/license/quality")
class UserLicenseQualityController(
    private val licenseScanQualityService: LicenseScanQualityService
){
    @GetMapping("/{planId}")
    fun getScanQuality(
        @PathVariable("planId") planId: String
    ): Response<LicenseScanQualityResponse> {
        return ResponseBuilder.success(licenseScanQualityService.getScanQuality(planId))
    }

    @PutMapping("/{planId}")
    fun createScanQuality(
        @PathVariable("planId") planId: String,
        @RequestBody request: LicenseScanQualityUpdateRequest
    ): Response<Boolean> {
        return ResponseBuilder.success(licenseScanQualityService.updateScanQuality(planId, request))
    }

    @PostMapping("/{planId}")
    fun updateScanQuality(
        @PathVariable("planId") planId: String,
        @RequestBody request: LicenseScanQualityUpdateRequest
    ): Response<Boolean> {
        return ResponseBuilder.success(licenseScanQualityService.updateScanQuality(planId, request))
    }
}
