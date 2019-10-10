package com.tencent.bkrepo.common.api.enums

/**
 * 系统模块枚举
 *
 * @author: carrypan
 * @date: 2019-10-09
 */

enum class SystemModuleEnum(val code: String) {
    COMMON("00"), // 公共模块
    REPOSITORY("01"), // 仓库
    METADATA("02"); // 元数据

    companion object {
        fun getSystemModule(code: String): SystemModuleEnum {
            var module = COMMON
            values().forEach { if (it.code == code) {
                module = it
            } }
            return module
        }
    }
}
