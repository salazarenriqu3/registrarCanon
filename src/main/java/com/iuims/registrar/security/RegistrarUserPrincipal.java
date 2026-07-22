package com.iuims.registrar.security;

import com.iuims.registrar.entity.SysUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RegistrarUserPrincipal implements UserDetails {

    private final SysUser user;
    private final String normalizedRole;
    private final List<GrantedAuthority> authorities;

    public RegistrarUserPrincipal(SysUser user) {
        this.user = user;
        this.normalizedRole = normalizeRole(user.getRole());
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + normalizedRole));
    }

    public Integer getUserId() {
        return user.getUserId();
    }

    public String getRole() {
        return user.getRole();
    }

    public String getProgramCode() {
        return user.getProgramCode();
    }

    public String getGrantedPermissions() {
        return user.getGrantedPermissions();
    }

    public Map<String, Object> toLegacySessionMap() {
        return new java.util.HashMap<>(Map.ofEntries(
            Map.entry("user_id", user.getUserId()),
            Map.entry("username", nullToBlank(user.getUsername())),
            Map.entry("real_name", nullToBlank(user.getRealName())),
            Map.entry("email", nullToBlank(user.getEmail())),
            Map.entry("role", nullToBlank(user.getRole())),
            Map.entry("password", nullToBlank(user.getPassword())),
            Map.entry("is_active", Boolean.TRUE.equals(user.getIsActive()) ? 1 : 0),
            Map.entry("program_code", nullToBlank(user.getProgramCode())),
            Map.entry("granted_permissions", nullToBlank(user.getGrantedPermissions()))
        ));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(user.getIsActive());
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "USER";
        }
        return role.trim().replaceAll("[^A-Za-z0-9]+", "_").toUpperCase(Locale.ROOT);
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
