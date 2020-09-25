package com.tencent.bkrepo.repository.pojo.stage

/**
 * 制品晋级阶段枚举类
 */
enum class ArtifactStageEnum(
    val tag: String
) {

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
         * 当找不到[tag]对应的值时返回[NONE]
         */
        @Throws(IllegalArgumentException::class)
        fun ofTagOrDefault(tag: String?): ArtifactStageEnum {
            if (tag.isNullOrBlank()) {
                return NONE
            }
            return values().find { stage -> stage.tag == tag } ?: NONE
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
            return values().find { stage -> stage.tag == tag } ?: throw IllegalArgumentException("Unknown tag")
        }

    }
}