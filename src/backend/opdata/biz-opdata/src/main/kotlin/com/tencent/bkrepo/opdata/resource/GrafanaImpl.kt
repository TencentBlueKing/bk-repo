package com.tencent.bkrepo.opdata.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.opdata.api.Grafana
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class GrafanaImpl : Grafana {
    override fun ping(): ResponseEntity<Any> {
        return ResponseEntity.ok().header("Content-Type", "application/json").body("{}")
    }

    override fun search(): ResponseEntity<Any> {
        return ResponseEntity.ok().header("Content-Type", "application/json").body("[\"projectNum\",\"nodeNum\"]")

    }

    override fun query(): ResponseEntity<Any> {
        return ResponseEntity.ok().header("Content-Type", "application/json").body("{\"columns\":[{\"text\":\"Time\",\"type\":\"time\"},{\"text\":\"Country\",\"type\":\"string\"},{\"text\":\"Number\",\"type\":\"number\"}],\"rows\":[[1234567,\"SE\",123],[1234567,\"DE\",231],[1234567,\"US\",321]],\"type\":\"table\"}")
    }

    override fun annotations(): ResponseEntity<Any> {
        return ResponseEntity.ok().header("Content-Type", "application/json").body("{}")
    }
}
