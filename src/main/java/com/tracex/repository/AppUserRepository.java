package com.tracex.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tracex.model.jpa.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);
}
