package com.tencent.bkrepo.repository.pojo.schedule

enum class SchedulePlatformType {
    WINDOWS,
    MACOS,
    All;

    fun id() = this.name.toLowerCase()
}