package com.xqt.saas.auth;

import com.xqt.saas.customerapi.CustomerApiAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain customerApiSecurityFilterChain(HttpSecurity http, CustomerApiAuthFilter customerApiAuthFilter) throws Exception {
        return http
            .securityMatcher("/api/customer-api/**")
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/api/customer-api/**").permitAll()
                .anyRequest().hasRole("CUSTOMER_API")
            )
            .addFilterBefore(customerApiAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, BearerAuthFilter bearerAuthFilter) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/me",
                    "/api/auth/logout",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password",
                    "/api/customer-register",
                    "/api/health/deep",
                    "/api/health/db",
                    "/api/health/metrics",
                    "/api/health",
                    "/api/public/**",
                    "/api/device/scale/**",
                    "/api/acc/dws",          // DWS 实物分拣推送，使用自己的 md5 token 校验
                    "/actuator/health/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                // ═══ ACC 业务权限（对齐 acc/User.php Permissions 设计）═══
                // 制单/订单 — operation.order.read/write
                .requestMatchers(HttpMethod.POST, "/api/acc/orders/**").hasAnyAuthority("operation.order.write", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.PUT,  "/api/acc/orders/**").hasAnyAuthority("operation.order.write", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/acc/orders/**").hasAnyAuthority("operation.order.write", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.GET,  "/api/acc/orders/**").hasAnyAuthority("operation.order.read", "operation.order.write", "ROLE_ADMIN")
                // 财务工作台 / 核算工作台 — finance.*
                .requestMatchers(HttpMethod.POST, "/api/acc/finance-workbench/**").hasAnyAuthority("finance.receivable.write", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/acc/settlement-workbench/**").hasAnyAuthority("finance.payable.write", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.GET,  "/api/acc/finance-workbench/**").hasAnyAuthority("finance.receivable.read", "finance.receivable.write", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.GET,  "/api/acc/settlement-workbench/**").hasAnyAuthority("finance.payable.read", "finance.payable.write", "ROLE_ADMIN")
                // 财务单据 (charges/costs/bills/payments/receiveds)
                .requestMatchers(HttpMethod.POST, "/api/acc/charges/**", "/api/acc/bills/**", "/api/acc/receiveds/**").hasAnyAuthority("finance.receivable.write", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/acc/costs/**", "/api/acc/payments/**").hasAnyAuthority("finance.payable.write", "ROLE_ADMIN")
                // 罚款 / 调账 / 退款 / 返利 (acc_fines / acc_finance_txns)
                .requestMatchers(HttpMethod.POST, "/api/acc/customer-fines/**", "/api/acc/customer-adjusts/**", "/api/acc/customer-refunds/**", "/api/acc/customer-rebates/**", "/api/acc/customer-sponsors/**")
                    .hasAnyAuthority("finance.adjust.approve", "finance.receivable.write", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/acc/supplier-fines/**", "/api/acc/supplier-adjusts/**", "/api/acc/supplier-refunds/**", "/api/acc/supplier-rebates/**", "/api/acc/supplier-sponsors/**")
                    .hasAnyAuthority("finance.adjust.approve", "finance.payable.write", "ROLE_ADMIN")
                // 资金账户 / 银行 / 转账 / 分红
                .requestMatchers(HttpMethod.POST, "/api/acc/banks/**", "/api/acc/transfers/**", "/api/acc/dividends/**", "/api/acc/assets/**")
                    .hasAnyAuthority("finance.account.write", "ROLE_ADMIN")
                // 配载 / 出货 — warehouse + flow
                .requestMatchers(HttpMethod.POST, "/api/acc/shipments/**", "/api/acc/stowages/**", "/api/acc/transits/**", "/api/acc/dispatches/**")
                    .hasAnyAuthority("operation.order.write", "warehouse.scan.write", "ROLE_ADMIN")
                // HR — admin-only (社保/公积金/工资/借款/考勤/员工)
                .requestMatchers(HttpMethod.POST, "/api/acc/wages/**", "/api/acc/borrowings/**", "/api/acc/employees/**", "/api/acc/socials/**", "/api/acc/funds/**", "/api/acc/attendances/**")
                    .hasAnyAuthority("admin.user.write", "ROLE_ADMIN")
                // API 对接 — admin
                .requestMatchers(HttpMethod.POST, "/api/acc/api-credentials/**", "/api/acc/webhook-endpoints/**", "/api/acc/scheduled-tasks/**", "/api/acc/logistics-interfaces/**")
                    .hasAnyAuthority("admin.user.write", "ROLE_ADMIN")
                // 价目表 / 渠道 / 燃油 / 偏远 / 国家 / 邮编 — 主数据
                .requestMatchers(HttpMethod.POST, "/api/acc/channels/**", "/api/acc/channel-accounts/**", "/api/acc/products/**", "/api/acc/product-items/**", "/api/acc/fuels/**", "/api/acc/remotes/**", "/api/acc/countries/**", "/api/acc/postcodes/**", "/api/acc/districts/**", "/api/acc/hscodes/**", "/api/acc/ports/**", "/api/acc/bank-names/**", "/api/acc/currencies/**", "/api/acc/branches/**", "/api/acc/departments/**", "/api/acc/fee-types/**", "/api/acc/fee-item-types/**", "/api/acc/expense-categories/**", "/api/acc/fees/**", "/api/acc/customers/**", "/api/acc/customer-groups/**", "/api/acc/potentials/**", "/api/acc/sold-tos/**", "/api/acc/notices/**", "/api/acc/importer-templates/**", "/api/acc/message-templates/**")
                    .hasAnyAuthority("finance.rate.write", "operation.order.write", "ROLE_ADMIN")
                .requestMatchers("/api/finance/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(bearerAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}