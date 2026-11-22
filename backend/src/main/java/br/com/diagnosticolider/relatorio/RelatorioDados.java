package br.com.diagnosticolider.relatorio;

import java.util.List;
import java.util.UUID;

/** Tudo o que o PDF e o e-mail precisam, já resolvido (sem entidades JPA). */
public record RelatorioDados(UUID id, String nome, String primeiroNome, String email, String data, int pontuacao,
		Faixa faixa, List<Dimensao> dimensoes, List<RespostaItem> respostas, CurvaCarreira.Desenho curva,
		String urlResultado) {

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
