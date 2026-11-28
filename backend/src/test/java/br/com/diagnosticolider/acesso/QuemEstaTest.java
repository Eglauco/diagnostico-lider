package br.com.diagnosticolider.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** O autor das ações (auditoria): o administrador no painel, o gestor ("RH · e-mail") na página do RH. */
class QuemEstaTest {

	private final QuemEsta quemEsta = new QuemEsta(mock(UsuarioRepository.class));

	@AfterEach
	void limpar() {
		SecurityContextHolder.clearContext();
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void reconheceQuemAgiuPelaPaginaDoRh() {
		String autor = QuemEsta.PREFIXO_RH + "rh@empresa.com.br";

		assertThat(QuemEsta.doRh(autor)).isTrue();
		assertThat(QuemEsta.emailDoRh(autor)).isEqualTo("rh@empresa.com.br");
	}

	@Test
	void administradorELiderNaoSaoDoRh() {
		assertThat(QuemEsta.doRh("Ana Souza")).isFalse();
		assertThat(QuemEsta.emailDoRh("Ana Souza")).isNull();
		assertThat(QuemEsta.doRh(null)).isFalse();
		assertThat(QuemEsta.emailDoRh(null)).isNull();
	}

	@Test
	void noPainelEOAdministrador() {
		entrarNoPainel();
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

		assertThat(quemEsta.nome()).isEqualTo("ana@empresa.com.br");
	}

	@Test
	void naPaginaDoRhEOGestorMesmoComOPainelAbertoNoMesmoNavegador() {
		entrarNoPainel();
		var requisicao = new MockHttpServletRequest();
		requisicao.setAttribute(QuemEsta.AUTOR_RH, QuemEsta.PREFIXO_RH + "rh@empresa.com.br");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requisicao));

		assertThat(quemEsta.nome()).isEqualTo("RH · rh@empresa.com.br");
	}

	@Test
	void foraDeUmaRequisicaoNinguem() {
		assertThat(quemEsta.nome()).isNull();
	}

	private static void entrarNoPainel() {
		var logado = UsuarioLogado.de(new Usuario("Ana", "ana@empresa.com.br", "{noop}x", Instant.now()));
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(logado, null, List.of()));
	}

}
