package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndActiveTrue(Long id);

    List<User> findByShopIdAndActiveTrueOrderByIdAsc(Long shopId);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);
}