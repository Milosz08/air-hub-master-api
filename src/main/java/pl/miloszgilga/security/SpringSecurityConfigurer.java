/*
 * Copyright (c) 2023 by MILOSZ GILGA <http://miloszgilga.pl>
 *
 * File name: SpringSecurityConfigurer.java
 * Last modified: 17/05/2023, 20:11
 * Project name: air-hub-master-server
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 *
 *     <http://www.apache.org/license/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the license.
 */

package pl.miloszgilga.security;

import lombok.RequiredArgsConstructor;
import org.jmpsl.security.SecurityUtil;
import org.jmpsl.security.filter.MiddlewareExceptionFilter;
import org.jmpsl.security.resolver.AccessDeniedResolverForRest;
import org.jmpsl.security.resolver.AuthResolverForRest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;

import static pl.miloszgilga.config.ApiReferenceConstant.PREFIX;

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@Configuration
@RequiredArgsConstructor
public class SpringSecurityConfigurer {

    private final Environment environment;
    private final AuthResolverForRest authResolverForRest;
    private final MiddlewareExceptionFilter middlewareExceptionFilter;
    private final AccessDeniedResolverForRest accessDeniedResolverForRest;

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        SecurityUtil.enableH2ConsoleForDev(httpSecurity, environment);

        httpSecurity
            .sessionManagement(options -> options.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(middlewareExceptionFilter, LogoutFilter.class)
            .formLogin().disable()
            .httpBasic().disable()
            .csrf().disable()
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authResolverForRest)
                .accessDeniedHandler(accessDeniedResolverForRest)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/error").permitAll()
                .requestMatchers(PREFIX + "/auth/login").permitAll()
                .requestMatchers(PREFIX + "/auth/register").permitAll()
                .requestMatchers(PREFIX + "/auth/activate-account").permitAll()
                .requestMatchers(PREFIX + "/renew-password/request").permitAll()
                .requestMatchers(PREFIX + "/renew-password/change").permitAll()
                .anyRequest().authenticated()
            );
        return httpSecurity.build();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

}
