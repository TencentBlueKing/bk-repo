package com.tencent.bkrepo.scanner.controller.user

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.scanner.pojo.license.SpdxLicenseInfo
import com.tencent.bkrepo.scanner.service.SpdxLicenseService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Api("许可证接口")
@RestController
@RequestMapping("/api/license")
class UserLicenseController(
    private val licenseService: SpdxLicenseService
) {
    @ApiOperation("导入许可证数据")
    @PostMapping("/import")
    fun importLicense(
        @RequestParam path: String
    ): Response<Boolean> {
        return ResponseBuilder.success(licenseService.importLicense(path))
    }

    @ApiOperation("分页查询许可证信息")
    @GetMapping("/list")
    fun listLicensePage(
        @ApiParam(value = "许可证唯一标识或许可证名称")
        @RequestParam name: String?,
        @ApiParam(value = "是否可信")
        @RequestParam isTrust: Boolean?,
        @ApiParam("页数", required = false, defaultValue = "1")
        @RequestParam(required = false, defaultValue = DEFAULT_PAGE_NUMBER.toString())
        pageNumber: Int = DEFAULT_PAGE_NUMBER,
        @ApiParam("每页数量", required = false, defaultValue = "20")
        @RequestParam(required = false, defaultValue = DEFAULT_PAGE_SIZE.toString())
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Response<Page<SpdxLicenseInfo>> {
        return ResponseBuilder.success(
            licenseService.listLicensePage(
                name,
                isTrust,
                pageNumber,
                pageSize
            )
        )
    }

    @ApiOperation("列表查询许可证信息")
    @GetMapping("/all")
    fun listLicense(): Response<List<SpdxLicenseInfo>> {
        return ResponseBuilder.success(licenseService.listLicense())
    }

    @ApiOperation("根据许可证唯一标识查询许可证信息")
    @GetMapping("/info")
    fun getLicenseInfo(
        @ApiParam(value = "许可证唯一标识", required = true)
        @RequestParam licenseId: String
    ): Response<SpdxLicenseInfo?> {
        return ResponseBuilder.success(licenseService.getLicenseInfo(licenseId))
    }

    @ApiOperation("切换许可证合规/不合规")
    @PostMapping("/{licenseId}")
    fun update(
        @ApiParam(value = "许可证唯一标识")
        @PathVariable licenseId: String
    ): Response<Void> {
        licenseService.toggleStatus(licenseId)
        return ResponseBuilder.success()
    }
}
