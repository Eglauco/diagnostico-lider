package br.com.diagnosticolider.acesso;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Confere, a cada chamada da API, se o usuário da sessão continua valendo: se foi desativado
 * ou teve a senha trocada, a sessão cai na hora (e a chamada segue como anônima, recebendo 401
 * nas rotas protegidas). Também impede que o navegador guarde respostas do painel em cache.
 */
class SessaoValidaFilter extends OncePerRequestFilter {

	private final UsuarioRepository usuarios;

	SessaoValidaFilter(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String caminho = request.getRequestURI();
		return !caminho.startsWith("/api/admin/") && !caminho.startsWith("/api/auth/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioLogado logado) {
			boolean valida = usuarios.findById(logado.getId())
				.filter(Usuario::isAtivo)
				.filter(u -> u.getCredenciaisEm().equals(logado.getCredenciaisEm()))
				.isPresent();
			if (!valida) {
				SecurityContextHolder.clearContext();
				HttpSession sessao = request.getSession(false);
				if (sessao != null) {
					sessao.invalidate();
				}
			}
		}
		chain.doFilter(request, response);
	}

}
