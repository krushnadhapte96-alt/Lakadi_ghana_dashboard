package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByIdAndActive(Long id) {
        return userRepository.findByIdAndActiveTrue(id);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return findById(id)
                .orElseThrow(() -> new DomainException(404, "User not found"));
    }

    @Transactional(readOnly = true)
    public List<User> findByShopId(Long shopId) {
        return userRepository.findByShopIdAndActiveTrueOrderByIdAsc(shopId);
    }
}
