package com.tencent.bkrepo.common.checker.util

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.checker.pojo.DependencyInfo
import net.canway.devops.bkrepo.dependencycheck.ScanUtils

object DependencyCheckerUtils {
    /**
     * 扫描指定路径下制品
     * @param path 指定路径
     * @return 字符串返回
     */
    fun scan(path: String): String {
        return ScanUtils.startScan(path)
    }

    /**
     * 扫描指定路径下制品
     * @param path 指定路径
     * @return DependencyInfo
     */
    fun scanWithInfo(path: String): DependencyInfo {
        return ScanUtils.startScan(path).readJsonString()
    }
}
