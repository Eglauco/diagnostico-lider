package br.com.diagnosticolider.notificacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.diagnosticolider.notificacao.EmailSender.Anexo;
import br.com.diagnosticolider.notificacao.EmailSender.Mensagem;

/** A logo vai dentro do e-mail (cid:), para abrir no Outlook corporativo e no Gmail sem buscar nada fora. */
class LogoEmbutidaTest {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G' };

	private static final String URL = "https://diagnostico.exemplo.com/api/public/empresas/7/logo-v3.png";

	private final LogoEmbutida embutida = new LogoEmbutida(
			(empresa, versao) -> empresa == 7 && versao == 3 ? Optional.of(PNG) : Optional.empty(),
			"https://diagnostico.exemplo.com/");

	private static Mensagem mensagem(String html, Anexo... anexos) {
		return new Mensagem("ana@empresa.com.br", "Convite", html, List.of(anexos));
	}

	@Test
	void trocaOEnderecoPelaImagemEmbutida() {
		var pdf = new Anexo("diagnostico.pdf", new byte[] { 1 });
		String html = "<img src=\"" + URL + "\" width=\"120\"><p>Oi</p><img src=\"" + URL + "\">";

		Mensagem enviada = embutida.embutir(mensagem(html, pdf));

		assertThat(enviada.html()).isEqualTo("<img src=\"cid:logo-7-v3\" width=\"120\"><p>Oi</p><img src=\"cid:logo-7-v3\">");
		// A mesma logo duas vezes vira um anexo só; o PDF continua lá.
		assertThat(enviada.anexos()).hasSize(2);
		assertThat(enviada.anexos().getFirst()).satisfies(logo -> {
			assertThat(logo.nome()).isEqualTo("logo.png");
			assertThat(logo.cid()).isEqualTo("logo-7-v3");
			assertThat(logo.conteudo()).isEqualTo(PNG);
		});
		assertThat(enviada.anexos().get(1)).isSameAs(pdf);
		assertThat(enviada.para()).containsExactly("ana@empresa.com.br");
	}

	@Test
	void semLogoOuSemArquivoOEmailSaiComoEstava() {
		var semLogo = mensagem("<p>Oi</p>");
		assertThat(embutida.embutir(semLogo)).isSameAs(semLogo);

		// Versão que não existe mais: fica o endereço.
		var antiga = mensagem("<img src=\"https://diagnostico.exemplo.com/api/public/empresas/7/logo-v2.png\">");
		assertThat(embutida.embutir(antiga)).isSameAs(antiga);

		// Endereço de outro site não é tocado.
		var deFora = mensagem("<img src=\"https://outro.com/api/public/empresas/7/logo-v3.png\">");
		assertThat(embutida.embutir(deFora)).isSameAs(deFora);
	}

	@Test
	void bucketForaDoArNaoSegura() {
		var instavel = new LogoEmbutida((empresa, versao) -> {
			throw new IllegalStateException("bucket fora do ar");
		}, "https://diagnostico.exemplo.com");
		var original = mensagem("<img src=\"" + URL + "\">");

		assertThat(instavel.embutir(original)).isSameAs(original);
	}

}
