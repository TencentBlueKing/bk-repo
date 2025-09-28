package com.tencent.bkrepo.common.metadata.pojo.node

import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration

/**
 * 自动创建索引配置
 */
data class AutoIndexRepositorySettings(
    /**
     * 是否启用自动创建目录索引功能
     */
    val enabled: Boolean = true
) {


    companion object {
        /**
         * [com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration.settings]中的配置键
         */
        const val SETTINGS_KEY_AUTO_INDEX = "autoIndex"

        fun from(configuration: RepositoryConfiguration): AutoIndexRepositorySettings? {
            val autoIndexSettingsMap = configuration.getSetting<Map<String, Any>>(SETTINGS_KEY_AUTO_INDEX)
                ?: return null
            val enabled = autoIndexSettingsMap[AutoIndexRepositorySettings::enabled.name] as Boolean? ?: true
            return AutoIndexRepositorySettings(enabled = enabled)
        }
    }
}