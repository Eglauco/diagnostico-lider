package br.com.diagnosticolider.acesso;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Segurança do painel. O líder continua sem login (/, /c/*, /resultado/*, /api/public/**);
 * só /api/admin/** exige sessão. A sessão vive num cookie HttpOnly (o JavaScript não lê) e
 * as chamadas que alteram dados levam o token CSRF que o Angular envia sozinho
 * (cookie XSRF-TOKEN → cabeçalho X-XSRF-TOKEN).
 */
@Configuration
class SegurancaConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		// BCrypt hoje, com prefixo {bcrypt}: dá para trocar o algoritmo no futuro sem invalidar senhas.
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	UserDetailsService usuariosDoPainel(UsuarioRepository usuarios) {
		return email -> usuarios.findByEmailIgnoreCase(Usuario.normalizarEmail(email))
			.map(UsuarioLogado::de)
			.orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService usuarios, PasswordEncoder senhas) {
		var provedor = new DaoAuthenticationProvider(usuarios);
		provedor.setPasswordEncoder(senhas);
		return new ProviderManager(provedor);
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	SecurityFilterChain seguranca(HttpSecurity http, UsuarioRepository usuarios,
			SecurityContextRepository contextos) throws Exception {
		http.authorizeHttpRequests(regras -> regras.requestMatchers("/api/admin/**")
			.authenticated()
			.requestMatchers(HttpMethod.PUT, "/api/auth/senha")
			.authenticated()
			.anyRequest()
			.permitAll())
			// O questionário público não usa sessão: CSRF só faz sentido onde há cookie de login.
			.csrf(csrf -> csrf.spa().ignoringRequestMatchers("/api/public/**"))
			.securityContext(contexto -> contexto.securityContextRepository(contextos))
			.requestCache(cache -> cache.disable())
			.exceptionHandling(erros -> erros.authenticationEntryPoint(naoAutenticado()))
			// Cache-Control fica com cada resposta: os arquivos do site continuam em cache e a API
			// do painel sai com no-store (SessaoValidaFilter).
			.headers(cabecalhos -> cabecalhos.cacheControl(cache -> cache.disable()))
			.addFilterAfter(new SessaoValidaFilter(usuarios), SecurityContextHolderFilter.class);
		return http.build();
	}

	/** 401 em JSON (problem+json), no mesmo formato dos outros erros da API. */
	private static AuthenticationEntryPoint naoAutenticado() {
		return (request, response, erro) -> {
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
			response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			response.getWriter()
				.write("{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Entre no painel para continuar.\"}");
		};
	}

}
