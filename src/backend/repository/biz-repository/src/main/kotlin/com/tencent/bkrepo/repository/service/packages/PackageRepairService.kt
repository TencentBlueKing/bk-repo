package com.tencent.bkrepo.repository.service.packages

interface PackageRepairService {

    /**
     * 修复npm历史版本数据
     */
    fun repairHistoryVersion()

    /**
     * 修正包的版本数
     */
    fun repairVersionCount()
}
