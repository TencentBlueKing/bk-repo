package com.tencent.bkrepo.scanner.controller.user

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.scanner.pojo.request.ScanQualityCreateRequest
import com.tencent.bkrepo.scanner.pojo.response.ScanQualityResponse
import com.tencent.bkrepo.scanner.service.ScanQualityService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/scan/quality")
class UserScanQualityController(
        private val scanQualityService: ScanQualityService
) {

    @GetMapping("/{scanId}")
    fun getScanQuality(
        @PathVariable("scanId") scanId: String
    ): Response<ScanQualityResponse> {
        return ResponseBuilder.success(scanQualityService.getScanQuality(scanId))
    }

    @PutMapping("/{scanId}")
    fun createScanQuality(
        @PathVariable("scanId") scanId: String,
        @RequestBody request: ScanQualityCreateRequest
    ): Response<Boolean> {
        return ResponseBuilder.success(scanQualityService.createScanQuality(scanId, request))
    }

    @PostMapping("/{scanId}")
    fun updateScanQuality(
            @PathVariable("scanId") scanId: String,
            @RequestBody request: ScanQualityCreateRequest
    ): Response<Boolean> {
        return ResponseBuilder.success(scanQualityService.updateScanQuality(scanId, request))
    }

}