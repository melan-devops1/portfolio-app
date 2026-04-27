package com.portfolio.product.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 분산 추적용 Request ID 필터.
 *
 * <p>요청 처리 흐름:
 * <ol>
 *   <li>{@code X-Request-Id} 헤더가 있으면 그 값을 사용 (다운스트림 서비스에서 전파된 ID)</li>
 *   <li>없거나 유효하지 않으면 새 UUID 생성</li>
 *   <li>SLF4J MDC에 {@code requestId}로 저장 → 모든 로그 라인에 자동 포함</li>
 *   <li>응답 헤더에도 동일 ID를 echo back → 클라이언트가 자신의 요청 ID 확인 가능</li>
 *   <li>요청 종료 시 {@link MDC#clear()} → 다음 요청에 ID가 새지 않도록</li>
 * </ol>
 *
 * <p><b>보안 고려사항 (Log Injection 방어)</b>:
 * 외부에서 받은 헤더 값을 그대로 신뢰하면 안 된다. 악의적 사용자가
 * 개행 문자나 매우 긴 문자열을 보내면 로그가 변조되거나 디스크/메모리를
 * 폭주시킬 수 있다. 따라서 다음 규칙을 적용한다:
 * <ul>
 *   <li>최대 길이 128자 제한</li>
 *   <li>제어 문자(개행, 탭, NULL 등) 포함 시 거부 → 새 UUID 사용</li>
 * </ul>
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)}로 다른 필터보다 먼저 실행되어
 * 이후의 모든 로그가 requestId를 포함하도록 보장한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String MDC_KEY = "requestId";
    private static final int MAX_LENGTH = 128;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));

        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 요청 종료 시 반드시 정리. 안 하면 Tomcat 스레드 재사용 시
            // 다음 요청 로그에 이전 requestId가 섞여 들어간다.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * 들어온 헤더 값이 안전하면 그대로 반환, 아니면 새 UUID 생성.
     *
     * @param incoming 클라이언트가 보낸 X-Request-Id 헤더 값 (null 가능)
     * @return 안전한 request id
     */
    private String resolveRequestId(String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return UUID.randomUUID().toString();
        }
        if (incoming.length() > MAX_LENGTH) {
            return UUID.randomUUID().toString();
        }
        // 제어 문자 (0x00 ~ 0x1F) 검사 — 개행, 탭, NULL 등으로 로그 변조 방지
        if (incoming.chars().anyMatch(c -> c < 0x20)) {
            return UUID.randomUUID().toString();
        }
        return incoming;
    }
}