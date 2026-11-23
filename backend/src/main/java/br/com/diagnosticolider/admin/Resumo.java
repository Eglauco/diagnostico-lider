package br.com.diagnosticolider.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.diagnosticolider.admin.AdminDtos.PontoCurva;
import br.com.diagnosticolider.diagnostico.LinhaResumo;
import br.com.diagnosticolider.diagnostico.StatusDiagnostico;

/** Os números de um conjunto de diagnósticos: do painel inteiro, de um link ou de uma empresa. */
record Resumo(long total, long concluidos, long emAndamento, Integer indiceMedio, Map<String, Long> porFaixa,
		List<PontoCurva> pontos) {

	private static final List<String> FAIXAS = List.of("TRAVADA", "PLATO", "ASCENSAO");

	static Resumo de(List<LinhaResumo> linhas) {
		List<PontoCurva> pontos = linhas.stream()
			.filter(l -> l.status() == StatusDiagnostico.CONCLUIDO && l.pontuacao() != null)
			.map(l -> new PontoCurva(l.pontuacao(), l.faixa()))
			.toList();
		long concluidos = linhas.stream().filter(l -> l.status() == StatusDiagnostico.CONCLUIDO).count();
		Integer media = pontos.isEmpty() ? null
				: (int) Math.round(pontos.stream().mapToInt(PontoCurva::indice).average().orElse(0));

		Map<String, Long> porFaixa = new LinkedHashMap<>();
		FAIXAS.forEach(f -> porFaixa.put(f, 0L));
		linhas.stream().filter(l -> l.faixa() != null).forEach(l -> porFaixa.merge(l.faixa(), 1L, Long::sum));

		return new Resumo(linhas.size(), concluidos, linhas.size() - concluidos, media, porFaixa, pontos);
	}

}
