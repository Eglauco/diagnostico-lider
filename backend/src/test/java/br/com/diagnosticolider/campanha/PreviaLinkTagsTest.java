package br.com.diagnosticolider.campanha;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PreviaLinkTagsTest {

	private static final String IMAGEM = "https://exemplo.app/api/public/campanhas/demo/previa-d1-7-abc.jpg";

	@Test
	void linkDeEmpresaTemImagemGrandeEConvite() {
		String tags = PreviaLinkController.tags("https://exemplo.app/c/demo", "Construtora Horizonte", IMAGEM);

		assertThat(tags).contains("<meta property=\"og:image\" content=\"" + IMAGEM + "\">")
			.contains("<meta property=\"og:image:type\" content=\"image/jpeg\">")
			.contains("<meta property=\"og:image:width\" content=\"1200\">")
			.contains("<meta property=\"og:image:height\" content=\"630\">")
			.contains("<meta name=\"twitter:card\" content=\"summary_large_image\">")
			.contains("<meta property=\"og:url\" content=\"https://exemplo.app/c/demo\">")
			.contains("Construtora Horizonte convida você para o Diagnóstico de Carreira do Líder.")
			.contains("<meta property=\"og:title\" content=\"" + PreviaLinkController.TITULO + "\">");
	}

	@Test
	void nomeDaEmpresaNaoQuebraOHtml() {
		String tags = PreviaLinkController.tags("https://exemplo.app/c/x", "A&B \"Consultoria\" <Ltda>", IMAGEM);

		assertThat(tags).contains("A&amp;B &quot;Consultoria&quot; &lt;Ltda&gt; convida você")
			.doesNotContain("\"Consultoria\"")
			.doesNotContain("<Ltda>");
	}

	@Test
	void linkInexistenteFicaSemImagem() {
		String tags = PreviaLinkController.tags("https://exemplo.app/c/naoexiste", null, null);

		assertThat(tags).contains(PreviaLinkController.DESCRICAO_GERAL)
			.doesNotContain("og:image")
			.doesNotContain("twitter:card");
	}

}
