package com.tencent.bkrepo.common.storage.innercos.cl5

data class RouteInfo(val ip: String, val port: Int) {
    override fun toString(): String = "$ip:$port"
}
