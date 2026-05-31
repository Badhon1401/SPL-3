package com.codepulse.service.impl;

import com.codepulse.dto.request.UpdateProfileRequest;
import com.codepulse.dto.response.UserProfileResponse;
import com.codepulse.entity.User;
import com.codepulse.exception.ResourceNotFoundException;
import com.codepulse.repository.UserRepository;
import com.codepulse.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public UserProfileResponse getProfile(Long userId) {
        return UserProfileResponse.fromUser(getUserById(userId));
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getUserById(userId);

        if (request.getFullName() != null)        user.setFullName(request.getFullName());
        if (request.getAvatarUrl() != null)        user.setAvatarUrl(request.getAvatarUrl());
        if (request.getCodeforcesHandle() != null) user.setCodeforcesHandle(nullIfBlank(request.getCodeforcesHandle()));
        if (request.getLeetcodeHandle() != null)   user.setLeetcodeHandle(nullIfBlank(request.getLeetcodeHandle()));
        if (request.getAtcoderHandle() != null)    user.setAtcoderHandle(nullIfBlank(request.getAtcoderHandle()));
        if (request.getCodechefHandle() != null)   user.setCodechefHandle(nullIfBlank(request.getCodechefHandle()));

        return UserProfileResponse.fromUser(userRepository.save(user));
    }

    @Override
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    @Override
    public List<UserProfileResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserProfileResponse::fromUser)
                .toList();
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = getUserById(userId);
        user.setActive(false);
        userRepository.save(user);
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}