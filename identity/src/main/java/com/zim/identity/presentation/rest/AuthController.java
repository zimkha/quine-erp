package com.zim.identity.presentation.rest;

import com.zim.identity.application.command.LoginCommand;
import com.zim.identity.application.handler.LoginHandler;
import com.zim.identity.application.result.LoginResult;
import com.zim.identity.presentation.rest.request.LoginRequest;
import com.zim.identity.presentation.rest.response.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Public endpoint: it takes no tenant (the tenant comes out of the token it
 * issues), so it never consults a {@code CurrentTenantProvider}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final LoginHandler loginHandler;

  public AuthController(LoginHandler loginHandler) {
    this.loginHandler =
        Objects.requireNonNull(loginHandler, "Login handler cannot be null");
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest request
  ) {
    LoginResult result = loginHandler.handle(
        new LoginCommand(request.email(), request.password())
    );

    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new LoginResponse(
            result.accessToken(),
            result.tokenType(),
            result.expiresIn()
        ));
  }
}
