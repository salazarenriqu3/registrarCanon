package com.iuims.registrar.security;

import com.iuims.registrar.repository.SysUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class RegistrarUserDetailsService implements UserDetailsService {

    private final SysUserRepository users;

    public RegistrarUserDetailsService(SysUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findByUsername(username)
            .map(RegistrarUserPrincipal::new)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
