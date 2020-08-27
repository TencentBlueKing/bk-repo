package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.auth.api.ServiceRoleResource
import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.common.security.http.HttpAuthProperties
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.repository.UT_USER
import com.tencent.bkrepo.repository.config.RepositoryProperties
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

@Import(
    HttpAuthProperties::class,
    StorageProperties::class,
    RepositoryProperties::class
)
@ComponentScan("com.tencent.bkrepo.repository.service")
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
abstract class ServiceBaseTest {

    @MockBean
    lateinit var roleResource: ServiceRoleResource

    @MockBean
    lateinit var userResource: ServiceUserResource

    fun initMock() {
        Mockito.`when`(roleResource.createRepoManage(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).then {
            ResponseBuilder.success(UT_USER)
        }

        Mockito.`when`(roleResource.createProjectManage(ArgumentMatchers.anyString())).thenReturn(
            ResponseBuilder.success(UT_USER)
        )

        Mockito.`when`(userResource.addUserRole(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).thenReturn(
            ResponseBuilder.success()
        )
    }
}
