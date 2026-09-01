package com.fleet.auth.service;

import com.fleet.auth.dto.LoginRequest;
import com.fleet.auth.dto.LoginResponse;
import com.fleet.auth.dto.CreateDriverUserRequest;
import com.fleet.auth.dto.RegisterRequest;
import com.fleet.auth.dto.RegisterResponse;
import com.fleet.auth.entity.User;
import com.fleet.auth.model.UserRole;
import com.fleet.auth.repository.UserRepository;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService
  ) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  public RegisterResponse register(RegisterRequest request) {
    return createUser(
        request.fullName(),
        request.email(),
        request.password(),
        request.matricule(),
        request.role()
    );
  }

  public RegisterResponse createDriverUser(CreateDriverUserRequest request) {
    return createUser(
        request.fullName(),
        request.email(),
        request.password(),
        request.matricule(),
        UserRole.DRIVER
    );
  }

  private RegisterResponse createUser(
      String fullName,
      String email,
      String password,
      String matricule,
      UserRole role
  ) {
    String normalizedEmail = normalizeEmail(email);
    String normalizedMatricule = normalizeMatricule(matricule);
    validateMatriculeRules(role, normalizedMatricule);

    if (userRepository.existsByEmail(normalizedEmail)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "email already used");
    }

    if (normalizedMatricule != null && userRepository.existsByMatricule(normalizedMatricule)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "matricule already used");
    }

    User user = User.builder()
        .fullName(fullName.trim())
        .email(normalizedEmail)
        .passwordHash(passwordEncoder.encode(password))
        .role(role)
        .matricule(normalizedMatricule)
        .build();

    User savedUser = userRepository.save(user);
    return new RegisterResponse(savedUser.getId(), savedUser.getRole(), savedUser.getMatricule());
  }

  public LoginResponse login(LoginRequest request) {
    String normalizedEmail = normalizeEmail(request.email());
    User user = userRepository.findByEmail(normalizedEmail)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
    }

    String token = jwtService.generateToken(user);
    return new LoginResponse(token, jwtService.getExpirationSeconds(), user.getRole());
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizeMatricule(String matricule) {
    if (matricule == null || matricule.isBlank()) {
      return null;
    }
    return matricule.trim().toUpperCase(Locale.ROOT);
  }

  private void validateMatriculeRules(UserRole role, String matricule) {
    boolean matriculeRequired = role == UserRole.DRIVER || role == UserRole.DISPATCHER;

    if (matriculeRequired && matricule == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "matricule is required for DRIVER and DISPATCHER"
      );
    }

    if (role == UserRole.CUSTOMER && matricule != null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "matricule is not allowed for CUSTOMER"
      );
    }
  }
}
