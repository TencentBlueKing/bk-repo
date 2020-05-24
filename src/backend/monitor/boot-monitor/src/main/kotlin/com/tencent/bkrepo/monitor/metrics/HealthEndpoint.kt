package com.tencent.bkrepo.monitor.metrics

enum class HealthEndpoint(val healthName: String) {

    MONGO("mongo"),
    STORAGE("storage"),
    HYSTRIX("hystrix");

    fun getEndpoint() = "health/$healthName"

    companion object {
        fun ofHealthName(healthName: String): HealthEndpoint {
            return values().first { it.healthName == healthName }
        }
    }
}
