package nl.salah.civicsignal.observability;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        String id = RequestIds.safe(request.getHeader(RequestIds.HEADER));
        response.setHeader(RequestIds.HEADER, id);
        try (var ignored = RequestIds.scope(id)) {
            try { chain.doFilter(request, response); }
            finally { LoggerFactory.getLogger(RequestIdFilter.class).info("HTTP request completed status={}", response.getStatus()); }
        }
    }
}
