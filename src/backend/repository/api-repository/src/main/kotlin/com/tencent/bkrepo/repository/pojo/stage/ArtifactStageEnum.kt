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
    @Throws(IllegalArgumentException::class)
    fun upgrade(toStage: ArtifactStageEnum?): ArtifactStageEnum {
        val newStage = toStage ?: this.nextStage()
        require(newStage.ordinal > this.ordinal) { "Illegal stage" }
        return newStage
    }

    /**
     * 下一个阶段
     */
    fun nextStage(): ArtifactStageEnum {
        require(this.ordinal < values().size - 1) { "Illegal stage" }
        return values()[this.ordinal + 1]
    }

    companion object {
        /**
         * 根据[tag]反查[ArtifactStageEnum]
         *
         * [tag]支持传入多个，以逗号分隔，返回最新的Stage
         * [tag]为null则返回[NONE]
         */
        fun ofTag(tag: String?): ArtifactStageEnum? {
            if (tag.isNullOrBlank()) {
                return NONE
            }
            val lastTag = tag.split(COMMA).lastOrNull()?.ensurePrefix(AT)?.toLowerCase()
            return values().find { stage -> stage.tag == lastTag }
        }

        /**
         * 根据[tag]反查[ArtifactStageEnum]，[tag]为空或不存在返回默认[NONE]
         */
        fun ofTagOrDefault(tag: String?): ArtifactStageEnum {
            return ofTag(tag) ?: NONE
        }

        /**
         * 根据[tag]反查[ArtifactStageEnum]
         *
         * [tag]为`null`则返回`null`
         * [tag]不存在抛[IllegalArgumentException]
         */
        @Throws(IllegalArgumentException::class)
        fun ofTagOrNull(tag: String?): ArtifactStageEnum? {
            if (tag.isNullOrBlank()) {
                return null
            }
            return ofTag(tag) ?: throw IllegalArgumentException("Unknown tag")
        }
    }
}