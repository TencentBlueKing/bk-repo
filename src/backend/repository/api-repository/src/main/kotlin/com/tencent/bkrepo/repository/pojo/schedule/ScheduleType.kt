package com.tencent.bkrepo.repository.pojo.schedule

enum class ScheduleType {
    USER,
    PROJECT;

    fun id() = this.name.toLowerCase()
}