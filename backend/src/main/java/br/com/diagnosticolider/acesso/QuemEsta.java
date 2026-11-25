package br.com.diagnosticolider.acesso;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Quem está fazendo a ação no painel, para a auditoria ("Editado por Ana"). Null fora de uma
 * requisição do painel: o líder no link pessoal, o agendador, o envio em segundo plano.
 */
@Component
public class QuemEsta {

	private final UsuarioRepository usuarios;

	QuemEsta(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	public String nome() {
		Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacao == null || !(autenticacao.getPrincipal() instanceof UsuarioLogado logado)) {
			return null;
		}
		return usuarios.findById(logado.getId()).map(Usuario::getNome).orElse(logado.getUsername());
	}

}
