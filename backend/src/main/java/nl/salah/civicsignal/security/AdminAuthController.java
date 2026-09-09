package nl.salah.civicsignal.security;
import java.util.List;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/admin/auth") public class AdminAuthController { @GetMapping("/me") public Me me(Authentication a){return new Me(true,a.getName(),List.of("ADMIN"),"BASIC");} public record Me(boolean authenticated,String username,List<String> roles,String authenticationType){} }
