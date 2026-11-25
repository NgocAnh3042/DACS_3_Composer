package com.example.backend.auth;

import com.example.backend.user.UserAccount;
import com.example.backend.user.UserAccountRepository;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {
    private final FirebaseTokenVerifier tokenVerifier;
    private final UserAccountRepository userRepository;
    private final ObjectMapper objectMapper;

    public FirebaseAuthenticationFilter(FirebaseTokenVerifier tokenVerifier,
            UserAccountRepository userRepository, ObjectMapper objectMapper) {
        this.tokenVerifier = tokenVerifier;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        if ("GET".equalsIgnoreCase(request.getMethod())
                && ("/api".equals(path) || "/api/".equals(path))) return true;
        if ("GET".equalsIgnoreCase(request.getMethod()) && "/actuator/health".equals(path)) return true;
        return "POST".equalsIgnoreCase(request.getMethod()) && "/api/payos/webhook".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                || authorization.substring(7).isBlank()) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Thiếu Firebase ID token");
            return;
        }

        FirebaseTokenVerifier.FirebaseClaims claims;
        try {
            claims = tokenVerifier.verify(authorization.substring(7).trim());
        } catch (FirebaseCertificateUnavailableException exception) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Không thể xác minh chứng thư Firebase");
            return;
        } catch (FirebaseTokenException exception) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Firebase ID token không hợp lệ");
            return;
        }

        UserAccount user = userRepository.findByFirebaseUid(claims.uid()).orElse(null);
        if (user == null) {
            if (!isProfileBootstrap(request)) {
                writeError(response, HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy hồ sơ tài khoản");
                return;
            }
            authenticate(request, principal(claims, null, null, null), List.of());
            chain.doFilter(request, response);
            return;
        }
        if (user.getDeletedAt() != null || !"active".equals(user.getStatus())) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Tài khoản không hoạt động");
            return;
        }

        String androidRole;
        try {
            androidRole = RoleMapper.toAndroidRole(user.getRole());
        } catch (IllegalArgumentException exception) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Vai trò tài khoản không hợp lệ");
            return;
        }
        FirebasePrincipal principal = principal(claims, user.getId(), user.getRole(), androidRole);
        authenticate(request, principal,
                List.of(
                        new SimpleGrantedAuthority("ROLE_" + user.getRole().toUpperCase(java.util.Locale.ROOT)),
                        new SimpleGrantedAuthority("ROLE_" + androidRole.toUpperCase(java.util.Locale.ROOT))));
        chain.doFilter(request, response);
    }

    private FirebasePrincipal principal(FirebaseTokenVerifier.FirebaseClaims claims,
            Long userId, String databaseRole, String androidRole) {
        Object emailClaim = claims.values().get("email");
        return new FirebasePrincipal(claims.uid(), userId, databaseRole, androidRole,
                emailClaim instanceof String email ? email : null,
                Boolean.TRUE.equals(claims.values().get("email_verified")));
    }

    private boolean isProfileBootstrap(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        return ("/api/users".equals(path) && "POST".equalsIgnoreCase(method))
                || ("/api/users/me".equals(path)
                    && List.of("GET", "POST", "PUT", "PATCH").contains(method.toUpperCase(java.util.Locale.ROOT)));
    }

    private void authenticate(HttpServletRequest request, FirebasePrincipal principal,
            List<SimpleGrantedAuthority> authorities) {
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute("firebaseUid", principal.firebaseUid());
        request.setAttribute("firebaseClaims", principal);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), java.util.Map.of("error", message));
    }
}
