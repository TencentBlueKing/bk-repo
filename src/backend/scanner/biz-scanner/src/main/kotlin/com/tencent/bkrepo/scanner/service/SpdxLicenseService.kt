package com.tencent.bkrepo.scanner.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.scanner.pojo.license.SpdxLicenseInfo

/**
 * 许可证服务
 */
interface SpdxLicenseService {
    /**
     * 导入 SPDX 许可证列表
     * @param path SPDX 许可证列表 JSON 文件
     */
    fun importLicense(path: String): Boolean

    /**
     * 分页查询许可证信息
     */
    fun listLicensePage(
        name: String?,
        isTrust: Boolean?,
        pageNumber: Int,
        pageSize: Int
    ): Page<SpdxLicenseInfo>

    /**
     * 查询所有许可证信息
     */
    fun listLicense(): List<SpdxLicenseInfo>

    /**
     * 查询许可证详细信息
     */
    fun getLicenseInfo(licenseId: String): SpdxLicenseInfo?

    /**
     * 根据许可证唯一标识切换合规状态
     */
    fun toggleStatus(licenseId: String)

    /**
     * 根据唯一标识集合查询许可证信息（scancode使用）
     */
    fun listLicenseByIds(licenseIds: List<String>): Map<String, SpdxLicenseInfo>
}
