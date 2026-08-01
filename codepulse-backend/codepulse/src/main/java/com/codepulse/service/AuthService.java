package com.codepulse.service;

import com.codepulse.dto.request.LoginRequest;
import com.codepulse.dto.request.RegisterRequest;
import com.codepulse.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
