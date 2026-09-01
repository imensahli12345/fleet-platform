package com.fleet.auth.controller;

import com.fleet.auth.dto.LoginRequest;
import com.fleet.auth.dto.LoginResponse;
import com.fleet.auth.dto.CreateDriverUserRequest;
import com.fleet.auth.dto.RegisterRequest;
import com.fleet.auth.dto.RegisterResponse;
import com.fleet.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
    return authService.register(request);
  }

  @PostMapping("/users/drivers")
  @ResponseStatus(HttpStatus.CREATED)
  public RegisterResponse createDriverUser(@Valid @RequestBody CreateDriverUserRequest request) {
    return authService.createDriverUser(request);
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }
}
