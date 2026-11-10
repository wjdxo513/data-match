package kr.kicc.datamatch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import kr.kicc.datamatch.error.ErrorCode;
import kr.kicc.datamatch.web.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로컬 웹 서버 보안 필터(BE-01, 기술 설계서 TD-19, 9.3절).
 *
 * <ul>
 *   <li>Host 헤더가 localhost, 127.0.0.1, [::1] 이 아니면 거절한다(DNS 리바인딩 방지).</li>
 *   <li>Origin 헤더가 있으면 같은 로컬 호스트만 허용한다(다른 웹 페이지의 요청 방지).</li>
 *   <li>변경성 요청(POST, PUT, PATCH, DELETE)은 application/json 만 받는다.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalOnlyFilter extends OncePerRequestFilter {

    static final Set<String> ALLOWED_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ObjectMapper objectMapper;

    public LocalOnlyFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isAllowedHost(request.getHeader("Host")) || !isAllowedOrigin(request.getHeader("Origin"))) {
            reject(response, ErrorCode.FORBIDDEN_REQUEST);
            return;
        }
        if (MUTATING_METHODS.contains(request.getMethod()) && !isJson(request.getContentType())) {
            reject(response, ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean isAllowedHost(String hostHeader) {
        if (hostHeader == null || hostHeader.isBlank()) {
            return false;
        }
        String host = hostHeader.trim().toLowerCase(Locale.ROOT);
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            if (end < 0) {
                return false;
            }
            host = host.substring(0, end + 1);
        } else {
            int colon = host.indexOf(':');
            if (colon >= 0) {
                host = host.substring(0, colon);
            }
        }
        return ALLOWED_HOSTS.contains(host);
    }

    static boolean isAllowedOrigin(String origin) {
        if (origin == null) {
            return true;
        }
        try {
            URI uri = URI.create(origin.trim());
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            if (host.equals("::1")) {
                host = "[::1]";
            }
            return ALLOWED_HOSTS.contains(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            return MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void reject(HttpServletResponse response, ErrorCode code) throws IOException {
        logger.warn("request rejected: code=" + code);
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(code));
    }
}
