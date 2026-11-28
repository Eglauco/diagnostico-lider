package br.com.diagnosticolider.acesso;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Quem está fazendo a ação, para a auditoria ("Editado por Ana"): o administrador logado no painel
 * ou, na página do RH, o gestor da empresa ("RH · rh@empresa.com.br"). Null fora dessas
 * requisições: o líder no link pessoal, o agendador, o envio em segundo plano.
 */
@Component
public class QuemEsta {

	/** Atributo da requisição com o gestor do RH que está agindo (gravado ao reconhecer o cookie dele). */
	public static final String AUTOR_RH = QuemEsta.class.getName() + ".rh";

	/** Como o gestor aparece na auditoria: "RH · rh@empresa.com.br". */
	public static final String PREFIXO_RH = "RH · ";

	private final UsuarioRepository usuarios;

	QuemEsta(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	public String nome() {
		// Na página do RH, quem age é o gestor, mesmo que a mesma pessoa esteja com o painel do
		// administrador aberto no mesmo navegador (o cookie da sessão do painel vai junto).
		RequestAttributes requisicao = RequestContextHolder.getRequestAttributes();
		Object gestor = requisicao == null ? null : requisicao.getAttribute(AUTOR_RH, RequestAttributes.SCOPE_REQUEST);
		if (gestor instanceof String autor) {
			return autor;
		}
		Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioLogado logado) {
			return usuarios.findById(logado.getId()).map(Usuario::getNome).orElse(logado.getUsername());
		}
		return null;
	}

	/** A ação foi de um gestor pela página do RH (e não do administrador). */
	public static boolean doRh(String autor) {
		return autor != null && autor.startsWith(PREFIXO_RH);
	}

	/** "RH · rh@empresa.com.br" → "rh@empresa.com.br". */
	public static String emailDoRh(String autor) {
		return doRh(autor) ? autor.substring(PREFIXO_RH.length()) : null;
	}

}
