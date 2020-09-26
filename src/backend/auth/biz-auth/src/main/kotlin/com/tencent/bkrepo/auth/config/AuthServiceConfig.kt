package com.tencent.bkrepo.auth.config

import com.tencent.bkrepo.auth.repository.AccountRepository
import com.tencent.bkrepo.auth.repository.ClusterRepository
import com.tencent.bkrepo.auth.repository.PermissionRepository
import com.tencent.bkrepo.auth.repository.RoleRepository
import com.tencent.bkrepo.auth.repository.UserRepository
import com.tencent.bkrepo.auth.service.AccountService
import com.tencent.bkrepo.auth.service.ClusterService
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.auth.service.RoleService
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.auth.service.bkiam.BkiamPermissionServiceImpl
import com.tencent.bkrepo.auth.service.bkiam.BkiamService
import com.tencent.bkrepo.auth.service.local.AccountServiceImpl
import com.tencent.bkrepo.auth.service.local.ClusterServiceImpl
import com.tencent.bkrepo.auth.service.local.PermissionServiceImpl
import com.tencent.bkrepo.auth.service.local.RoleServiceImpl
import com.tencent.bkrepo.auth.service.local.UserServiceImpl
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.data.mongodb.core.MongoTemplate

@Configuration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
class AuthServiceConfig {

    @Bean
    @ConditionalOnMissingBean(AccountService::class)
    fun accountService(
        @Autowired accountRepository: AccountRepository,
        @Autowired mongoTemplate: MongoTemplate
    ) = AccountServiceImpl(accountRepository, mongoTemplate)

    @Bean
    @ConditionalOnMissingBean(ClusterService::class)
    fun clusterService(
        @Autowired clusterRepository: ClusterRepository,
        @Autowired mongoTemplate: MongoTemplate
    ) = ClusterServiceImpl(clusterRepository, mongoTemplate)

    @Bean
    @ConditionalOnMissingBean(PermissionService::class)
    fun permissionService(
        @Autowired userRepository: UserRepository,
        @Autowired roleRepository: RoleRepository,
        @Autowired permissionRepository: PermissionRepository,
        @Autowired mongoTemplate: MongoTemplate,
        @Autowired repositoryClient: RepositoryClient
    ) = PermissionServiceImpl(userRepository, roleRepository, permissionRepository, mongoTemplate, repositoryClient)

    @Bean
    @ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "bkiam")
    fun bkiamPermissionService(
        @Autowired userRepository: UserRepository,
        @Autowired roleRepository: RoleRepository,
        @Autowired permissionRepository: PermissionRepository,
        @Autowired mongoTemplate: MongoTemplate,
        @Autowired repositoryClient: RepositoryClient,
        @Autowired bkiamService: BkiamService
    ) = BkiamPermissionServiceImpl(userRepository, roleRepository, permissionRepository, mongoTemplate, repositoryClient, bkiamService)

    @Bean
    @ConditionalOnMissingBean(RoleService::class)
    fun roleService(@Autowired roleRepository: RoleRepository) = RoleServiceImpl(roleRepository)

    @Bean
    @ConditionalOnMissingBean(UserService::class)
    fun userService(
        @Autowired userRepository: UserRepository,
        @Autowired roleRepository: RoleRepository,
        @Autowired mongoTemplate: MongoTemplate
    ) = UserServiceImpl(userRepository, roleRepository, mongoTemplate)
}
