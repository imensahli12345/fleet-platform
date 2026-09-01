package com.fleet.auth.service;

import com.fleet.auth.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final SecretKey secretKey;
  private final long expirationSeconds;

  public JwtService(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.expiration}") long expirationMillis
  ) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationSeconds = expirationMillis / 1000;
  }

  public String generateToken(User user) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(expirationSeconds);

    var builder = Jwts.builder()
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("role", user.getRole().name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .signWith(secretKey);

    if (user.getMatricule() != null) {
      builder.claim("matricule", user.getMatricule());
    }

    return builder.compact();
  }

  public long getExpirationSeconds() {
    return expirationSeconds;
  }
}
