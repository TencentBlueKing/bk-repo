package com.tencent.bkrepo.repository.pojo.stage

import com.tencent.bkrepo.common.api.constant.CharPool.AT
import com.tencent.bkrepo.common.api.constant.StringPool.COMMA
import com.tencent.bkrepo.common.api.constant.ensurePrefix

/**
 * 制品晋级阶段枚举类
 */
enum class ArtifactStageEnum(
    val tag: String
) {
    /**
     * 无
     */
    NONE(""),

    /**
     * 预发布
     */
    PRE_RELEASE("@prerelease"),

    /**
     * 发布
     */
    RELEASE("@release");

    /**
     * 晋级
     */
    @Throws(IllegalStateException::class)
    fun upgrade(newStage: ArtifactStageEnum): ArtifactStageEnum {
        if (newStage.ordinal <= this.ordinal) {
            throw IllegalStateException()
        }
        return newStage
    }

    /**
     * 下一个阶段
     */
    fun nextStage(): ArtifactStageEnum {
        if (this.ordinal == values().size - 1) {
            throw IllegalStateException()
        }
        return values()[this.ordinal + 1]
    }

    /**
     * 获取展示名称列表
     */
    fun getDisplayTag(): String {
        return values().filter { it.ordinal <= this.ordinal && it != NONE }.joinToString(COMMA) { it.tag }
    }

    companion object {
        /**
         * 根据[tag]反查[ArtifactStageEnum]
         */
        fun ofTag(tag: String?): ArtifactStageEnum? {
            if (tag == null) {
                return NONE
            }
            val normalizedTag = tag.ensurePrefix(AT)
            val lowerCase = normalizedTag.toLowerCase()
            return values().find { it.tag == lowerCase }
        }

        /**
         * 根据[tag]反查[ArtifactStageEnum]，不存在返回默认[NONE]
         */
        fun ofTagOrDefault(tag: String?): ArtifactStageEnum {
            return ofTag(tag) ?: NONE
        }

        /**
         * 根据[name]反查[ArtifactStageEnum]
         */
        fun ofName(name: String?): ArtifactStageEnum? {
            if (name == null) return NONE
            val upperCase = name.toUpperCase()
            return values().find { it.name == upperCase }
        }
    }
}