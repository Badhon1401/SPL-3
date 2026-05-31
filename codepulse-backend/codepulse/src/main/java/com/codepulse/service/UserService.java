package com.codepulse.service;

import com.codepulse.dto.request.UpdateProfileRequest;
import com.codepulse.dto.response.UserProfileResponse;
import com.codepulse.entity.User;

import java.util.List;

public interface UserService {
    UserProfileResponse getProfile(Long userId);
    UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request);
    User getUserById(Long userId);
    List<UserProfileResponse> getAllUsers();
    void deleteUser(Long userId);
}
