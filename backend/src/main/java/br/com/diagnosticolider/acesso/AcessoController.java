package br.com.diagnosticolider.acesso;

import java.time.Duration;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.diagnosticolider.acesso.AcessoDtos.Entrar;
import br.com.diagnosticolider.acesso.AcessoDtos.Sessao;
import br.com.diagnosticolider.acesso.AcessoDtos.TrocarSenha;
import br.com.diagnosticolider.acesso.AcessoDtos.UsuarioSessao;
import br.com.diagnosticolider.compartilhado.RegraNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Entrar, sair, saber quem está logado e trocar a própria senha. */
@RestController
@RequestMapping("/api/auth")
class AcessoController {

	private final AuthenticationManager autenticacao;

	private final SecurityContextRepository contextos;

	private final UsuarioService usuarios;

	private final ProtecaoLogin protecao;

	private final ChangeSessionIdAuthenticationStrategy novaSessao = new ChangeSessionIdAuthenticationStrategy();

	AcessoController(AuthenticationManager autenticacao, SecurityContextRepository contextos, UsuarioService usuarios,
			ProtecaoLogin protecao) {
		this.autenticacao = autenticacao;
		this.contextos = contextos;
		this.usuarios = usuarios;
		this.protecao = protecao;
	}

	/** Quem está logado. Também entrega o cookie XSRF-TOKEN que o login vai precisar. */
	@GetMapping("/sessao")
	Sessao sessao(@AuthenticationPrincipal UsuarioLogado logado, CsrfToken csrf) {
		csrf.getToken();
		return new Sessao(logado == null ? null : UsuarioSessao.de(usuarios.buscar(logado.getId())));
	}

	@PostMapping("/entrar")
	UsuarioSessao entrar(@RequestBody @Validated Entrar pedido, HttpServletRequest request,
			HttpServletResponse response) {
		String email = Usuario.normalizarEmail(pedido.email());
		String ip = request.getRemoteAddr();
		protecao.bloqueio(email, ip).ifPresent(espera -> {
			throw bloqueado(espera);
		});

		Authentication autenticado;
		try {
			autenticado = autenticacao.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, pedido.senha()));
		}
		catch (AuthenticationException e) {
			protecao.falhou(email, ip);
			throw RegraNegocioException.comStatus(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
		}
		UsuarioLogado logado = (UsuarioLogado) autenticado.getPrincipal();
		if (!logado.isAtivo()) {
			throw RegraNegocioException.comStatus(HttpStatus.FORBIDDEN,
					"Este acesso foi desativado. Fale com quem administra o painel.");
		}
		protecao.entrou(email);
		iniciarSessao(autenticado, request, response);
		usuarios.registrarAcesso(logado.getId());
		return UsuarioSessao.de(usuarios.buscar(logado.getId()));
	}

	@PostMapping("/sair")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void sair(HttpServletRequest request, HttpServletResponse response) {
		new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
	}

	/** Troca a própria senha. As outras sessões desta pessoa caem; esta continua. */
	@PutMapping("/senha")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void trocarSenha(@AuthenticationPrincipal UsuarioLogado logado, @RequestBody @Validated TrocarSenha pedido,
			HttpServletRequest request, HttpServletResponse response) {
		Usuario usuario = usuarios.trocarPropriaSenha(logado.getId(), pedido.senhaAtual(), pedido.novaSenha());
		var renovado = UsernamePasswordAuthenticationToken.authenticated(UsuarioLogado.de(usuario), null, List.of());
		((UsuarioLogado) renovado.getPrincipal()).eraseCredentials();
		var contexto = SecurityContextHolder.createEmptyContext();
		contexto.setAuthentication(renovado);
		SecurityContextHolder.setContext(contexto);
		contextos.saveContext(contexto, request, response);
	}

	private void iniciarSessao(Authentication autenticado, HttpServletRequest request, HttpServletResponse response) {
		// Novo id de sessão no login: evita fixação de sessão.
		novaSessao.onAuthentication(autenticado, request, response);
		var contexto = SecurityContextHolder.createEmptyContext();
		contexto.setAuthentication(autenticado);
		SecurityContextHolder.setContext(contexto);
		contextos.saveContext(contexto, request, response);
	}

	private static RegraNegocioException bloqueado(Duration espera) {
		long minutos = Math.max(1, (espera.toSeconds() + 59) / 60);
		var erro = RegraNegocioException.comStatus(HttpStatus.TOO_MANY_REQUESTS,
				"Muitas tentativas. Por segurança, tente de novo em %d %s.".formatted(minutos,
						minutos == 1 ? "minuto" : "minutos"));
		erro.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(espera.toSeconds()));
		return erro;
	}

}
