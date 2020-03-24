package com.tencent.bkrepo.helm.api

import io.swagger.annotations.Api
import org.springframework.web.bind.annotation.GetMapping

@Api("helm仓库获取tgz包")
interface ChartRepositoryResource {
    @GetMapping("/index.yaml")
    fun getIndexYaml()
}
