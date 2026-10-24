package com.tracex.service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.tracex.model.jpa.AppUser;
import com.tracex.repository.AppUserRepository;

/** Loads login accounts from the relational database. */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public CustomUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No such user: " + username));
        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole())
                .build();
    }
}
