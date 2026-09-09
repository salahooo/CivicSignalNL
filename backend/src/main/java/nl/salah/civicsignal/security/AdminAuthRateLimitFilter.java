package nl.salah.civicsignal.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.*;
import org.springframework.web.filter.OncePerRequestFilter;

public class AdminAuthRateLimitFilter extends OncePerRequestFilter {
    static final int LIMIT=10, MAX_ENTRIES=1000; static final Duration WINDOW=Duration.ofMinutes(1);
    private final Map<String,Attempt> attempts=new ConcurrentHashMap<>(); private final Clock clock; private final ObjectMapper json; private final MeterRegistry meters;
    record Attempt(int count,Instant expiresAt){}
    public AdminAuthRateLimitFilter(Clock clock,ObjectMapper json,MeterRegistry meters){this.clock=clock;this.json=json;this.meters=meters;}
    @Override protected boolean shouldNotFilter(HttpServletRequest r){String p=r.getRequestURI();return !(p.startsWith("/api/v1/admin/")||p.startsWith("/actuator/"))||r.getHeader(HttpHeaders.AUTHORIZATION)==null;}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
        Instant now=clock.instant();cleanup(now);String key=req.getRemoteAddr();Attempt current=attempts.get(key);if(current!=null&&current.count()>=LIMIT){limited(res,current,now);return;}
        chain.doFilter(req,res);if(res.getStatus()==401){meters.counter("civic_admin_auth_failure_total").increment();attempts.compute(key,(k,a)->a==null||!a.expiresAt().isAfter(now)?new Attempt(1,now.plus(WINDOW)):new Attempt(a.count()+1,a.expiresAt()));}else if(res.getStatus()<400){attempts.remove(key);meters.counter("civic_admin_auth_success_total").increment();}
    }
    private void cleanup(Instant now){attempts.entrySet().removeIf(e->!e.getValue().expiresAt().isAfter(now));if(attempts.size()>MAX_ENTRIES)attempts.clear();}
    private void limited(HttpServletResponse r,Attempt a,Instant now)throws IOException{meters.counter("civic_admin_rate_limited_total").increment();r.setStatus(429);r.setHeader(HttpHeaders.RETRY_AFTER,String.valueOf(Math.max(1,Duration.between(now,a.expiresAt()).toSeconds())));r.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");r.setContentType(MediaType.APPLICATION_JSON_VALUE);json.writeValue(r.getOutputStream(),Map.of("code","RATE_LIMITED","message","Te veel mislukte pogingen. Probeer later opnieuw."));}
    int entryCount(){return attempts.size();}
}
