package com.tencent.bkrepo.monitor.config

import de.codecentric.boot.admin.server.config.AdminServerProperties
import de.codecentric.boot.admin.server.web.client.HttpHeadersProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import java.util.UUID

@Configuration
@ConfigurationProperties("spring.boot.admin.auth")
class SecurityConfiguration : WebSecurityConfigurerAdapter() {

    @Autowired
    private lateinit var adminServerProperties: AdminServerProperties

    var username: String = "user"
    var password: String? = null

    override fun configure(http: HttpSecurity) {
        val adminContextPath = adminServerProperties.contextPath
        val successHandler = SavedRequestAwareAuthenticationSuccessHandler()
        successHandler.setTargetUrlParameter("redirectTo")
        successHandler.setDefaultTargetUrl("$adminContextPath/")

        http.authorizeRequests {
            it.antMatchers("$adminContextPath/assets/**").permitAll()
                .antMatchers("$adminContextPath/login").permitAll().anyRequest().authenticated()
        }.formLogin { it.loginPage("$adminContextPath/login").successHandler(successHandler) }
            .logout { it.logoutUrl("$adminContextPath/logout") }
            .httpBasic(Customizer.withDefaults())
            .csrf { csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers(
                        AntPathRequestMatcher("$adminContextPath/instances", HttpMethod.POST.toString()),
                        AntPathRequestMatcher("$adminContextPath/instances/*", HttpMethod.DELETE.toString()),
                        AntPathRequestMatcher("$adminContextPath/actuator/**")
                    )
            }
    }

    override fun configure(auth: AuthenticationManagerBuilder) {
        if (password.isNullOrEmpty()) {
            password = UUID.randomUUID().toString()
            logger.info("Using generated security password: $password")
        }
        auth.inMemoryAuthentication().withUser(username).password("{noop}$password").roles("")
    }

    @Bean
    fun customHttpHeadersProvider(): HttpHeadersProvider {
        return HttpHeadersProvider {
            HttpHeaders().apply { setBasicAuth(username, password.orEmpty()) }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SecurityConfiguration::class.java)
    }
}
