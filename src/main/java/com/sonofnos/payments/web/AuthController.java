package com.sonofnos.payments.web;

import com.sonofnos.payments.security.JwtIssuerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Demo/testing token issuance for this service's self-issued JWTs. In a real
 * deployment, token issuance would sit behind real client credential
 * verification (or delegate to an actual IdP); this endpoint exists so the
 * Swagger UI and Postman collection are self-contained without an external
 * auth server.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Demo self-issued JWT token endpoint")
public class AuthController {

    private final JwtIssuerService jwtIssuerService;

    public AuthController(JwtIssuerService jwtIssuerService) {
        this.jwtIssuerService = jwtIssuerService;
    }

    public record TokenRequest(@NotBlank String subject, @NotEmpty List<String> roles) {
    }

    @PostMapping("/token")
    @Operation(summary = "Issue a demo JWT with the given roles (e.g. PAYMENTS_WRITE, PAYMENTS_READ)")
    public Map<String, String> issueToken(@Valid @RequestBody TokenRequest request) {
        return Map.of("access_token", jwtIssuerService.issue(request.subject(), request.roles()));
    }
}
