package com.tencent.bkrepo.repository.pojo.stage

import com.tencent.bkrepo.common.api.constant.StringPool

/**
 * 制品晋级阶段枚举类
 */
enum class ArtifactStageEnum(
    val value: String
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
     * 判断是否当前阶段是否能晋级
     */
    fun canUpgrade(): Boolean {
        return this != RELEASE
    }

    /**
     * 判断是否当前阶段是否能降级
     */
    fun canDowngrade(): Boolean {
        return this != PRE_RELEASE && this != NONE
    }

    /**
     * 晋级
     */
    fun upgrade(): ArtifactStageEnum {
        return when(this) {
            NONE -> PRE_RELEASE
            PRE_RELEASE -> RELEASE
            RELEASE -> throw IllegalStateException()
        }
    }

    /**
     * 降级
     */
    fun downgrade(): ArtifactStageEnum {
        return when(this) {
            NONE -> throw IllegalStateException()
            PRE_RELEASE -> throw IllegalStateException()
            RELEASE -> PRE_RELEASE
        }
    }

    /**
     * 获取展示名称列表
     */
    fun getDisplayTag(): String {
        return when(this) {
            NONE -> StringPool.EMPTY
            PRE_RELEASE -> PRE_RELEASE.value
            RELEASE -> listOf(PRE_RELEASE.value, RELEASE.value).joinToString { StringPool.COMMA }
        }
    }

    companion object {

        /**
         * 根据[value]反查[ArtifactStageEnum]
         */
        fun of(value: String?): ArtifactStageEnum {
            if (value == null) return NONE
            val upperCase = value.toUpperCase()
            return values().find { it.name == upperCase } ?: NONE
        }
    }
}