package br.com.diagnosticolider.relatorio;

import java.util.List;
import java.util.UUID;

import br.com.diagnosticolider.empresa.MarcaEmpresa;
import br.com.diagnosticolider.empresa.PaletaMarca;

/**
 * Tudo o que o PDF e o e-mail precisam, já resolvido (sem entidades JPA). "empresa" vem quando o
 * líder respondeu pelo link de uma empresa; "tema" é a paleta (da empresa ou a do Diagnóstico).
 */
public record RelatorioDados(UUID id, String nome, String primeiroNome, String email, String data, int pontuacao,
		Faixa faixa, List<Dimensao> dimensoes, List<RespostaItem> respostas, CurvaCarreira.Desenho curva,
		String urlResultado, MarcaEmpresa empresa, PaletaMarca.Tema tema, Logo logo) {

	/**
	 * Logomarca da empresa: embutida no PDF (dataUri) e por endereço público no e-mail (url; no envio
	 * o Correio troca pela imagem embutida, ver LogoEmbutida).
	 * pedeNome: logo só com o símbolo, então o nome da empresa vai ao lado.
	 * larguraEmail/alturaEmail: tamanho exato no e-mail (o Outlook estica imagem sem largura).
	 */
	public record Logo(String dataUri, String url, boolean pedeNome, int larguraEmail, int alturaEmail) {

		static final int ALTURA_EMAIL = 34;

		static final int LARGURA_MAX_EMAIL = 200;

		static Logo de(String dataUri, String url, MarcaEmpresa.Logo logo) {
			double escala = Math.min((double) ALTURA_EMAIL / logo.altura(), (double) LARGURA_MAX_EMAIL / logo.largura());
			return new Logo(dataUri, url, logo.pedeNome(), (int) Math.round(logo.largura() * escala),
					(int) Math.round(logo.altura() * escala));
		}
	}

	public record Faixa(String codigo, String titulo, String resumo, String descricao, List<String> recomendacoes) {
	}

	public record Dimensao(String nome, String descricao, int pontuacao) {
	}

	public record RespostaItem(String dimensao, String enunciado, String resposta) {
	}

	public String nomeArquivo() {
		String base = java.text.Normalizer.normalize(primeiroNome, java.text.Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.replaceAll("[^A-Za-z0-9]+", "-")
			.toLowerCase(java.util.Locale.ROOT);
		return "diagnostico-carreira-" + (base.isBlank() ? "lider" : base) + ".pdf";
	}

}
