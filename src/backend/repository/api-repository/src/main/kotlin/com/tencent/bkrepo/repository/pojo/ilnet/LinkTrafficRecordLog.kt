package com.tencent.bkrepo.repository.pojo.ilnet

class LinkTrafficRecordLog(
    request: LinkTrafficRequest,
    response: LinkTrafficResponse,
) {
    val linkTrafficTag: String = "IlnetLinkTrafficRecord"
    val mac: String = request.mac
    val ip: String? = request.ip
    val username: String? = request.username
    val linkSpeed: String? = request.linkSpeed
    val simple: Boolean? = request.simple
    val congestionThreshold: Float? = request.congestionThreshold
    val code: Int = response.code
    val msg: String = response.msg
    val elapsedMs: Int? = response.elapsedMs
    val data: LinkTrafficData? = response.data
}
