package com.iuims.registrar.config;
import com.iuims.registrar.entity.Student;

import com.iuims.registrar.security.SessionCurrentUserBridgeFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.io.IOException;

@Configuration
public class SecurityConfig {

    private final SessionCurrentUserBridgeFilter sessionBridgeFilter;

    public SecurityConfig(SessionCurrentUserBridgeFilter sessionBridgeFilter) {
        this.sessionBridgeFilter = sessionBridgeFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/error", "/css/**", "/js/**", "/images/**",
                    "/webjars/**", "/favicon.ico").permitAll()
                .requestMatchers("/admin/admission-acceptance", "/admin/approve-admission",
                    "/admin/pre-reg/**", "/api/search-applicants", "/api/term-rollover/**")
                    .hasAnyRole("ADMIN", "REGISTRAR", "ADMISSION")
                .requestMatchers("/admin/demo-grades", "/admin/demo-grades/**")
                    .hasAnyRole("DEAN", "REGISTRAR")
                .requestMatchers("/admin/users", "/create-user", "/admin/update-user",
                    "/admin/delete-user", "/admin/toggle-status", "/admin/reset-password")
                    .hasRole("ADMIN")
                .requestMatchers("/admin/**", "/create-user", "/api/search-students",
                    "/api/schedule/**")
                    .hasAnyRole("ADMIN", "REGISTRAR")
                .requestMatchers("/grades/**", "/api/faculty/**")
                    .hasAnyRole("FACULTY", "DEAN", "ADMIN", "REGISTRAR")
                .requestMatchers("/faculty/submit-class", "/faculty/unsubmit-class",
                    "/faculty/request-change", "/faculty/request-extension")
                    .hasAnyRole("FACULTY", "ADMIN", "REGISTRAR")
                .requestMatchers("/faculty/**")
                    .hasAnyRole("FACULTY", "DEAN", "ADMIN", "REGISTRAR")
                .requestMatchers("/enrollment", "/my-grades", "/my-load", "/student/**")
                    .hasRole("STUDENT")
                .requestMatchers("/api/mcp/**", "/mcp/**")
                    .hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(registrarSuccessHandler())
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"))
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
            )
            .addFilterAfter(sessionBridgeFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationSuccessHandler registrarSuccessHandler() {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
            String target = "/";
            if (hasRole(authentication, "ROLE_DEAN")) {
                target = "/grades";
            } else if (hasRole(authentication, "ROLE_FACULTY")) {
                target = "/grades";
            } else if (hasRole(authentication, "ROLE_STUDENT")) {
                target = "/enrollment";
            }
            redirect(request, response, target);
        };
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(role::equals);
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response, String target)
            throws IOException {
        response.sendRedirect(request.getContextPath() + target);
    }
}
