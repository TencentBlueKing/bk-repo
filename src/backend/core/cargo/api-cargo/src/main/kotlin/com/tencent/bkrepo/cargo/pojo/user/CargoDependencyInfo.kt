package com.tencent.bkrepo.cargo.pojo.user

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "cargo版本依赖信息")
data class CargoDependencyInfo(
    @get:Schema(title = "依赖包key")
    val packageKey: String,
    @get:Schema(title = "依赖包名")
    val packageName: String,
    @get:Schema(title = "依赖版本约束")
    val versionReq: String,
    @get:Schema(title = "依赖类型")
    val kind: String? = null,
    @get:Schema(title = "是否可选")
    val optional: Boolean = false,
    @get:Schema(title = "依赖target")
    val target: String? = null,
    @get:Schema(title = "是否启用default features")
    val defaultFeatures: Boolean = true,
    @get:Schema(title = "启用的features")
    val features: List<String> = emptyList()
)

@Schema(title = "依赖当前版本的包信息")
data class CargoDependentInfo(
    @get:Schema(title = "依赖方包key")
    val packageKey: String,
    @get:Schema(title = "依赖方包名")
    val packageName: String,
    @get:Schema(title = "依赖方版本")
    val version: String,
    @get:Schema(title = "依赖方声明的版本约束")
    val versionReq: String,
    @get:Schema(title = "依赖类型")
    val kind: String? = null,
    @get:Schema(title = "是否可选")
    val optional: Boolean = false,
    @get:Schema(title = "依赖target")
    val target: String? = null
)
