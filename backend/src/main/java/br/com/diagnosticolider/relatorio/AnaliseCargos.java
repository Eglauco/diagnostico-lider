package br.com.diagnosticolider.relatorio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import br.com.diagnosticolider.diagnostico.TempoCargo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.Celula;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.FaixaResumo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.GrupoCargo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.ParteFaixa;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.PorCargo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.TempoResumo;

/**
 * Os líderes da empresa por cargo e por tempo no cargo, sem identificar ninguém: cargo com menos
 * do mínimo de líderes entra em "Outros cargos" (com quem escolheu "Outro cargo"); se nem assim
 * chegar ao mínimo, fica de fora. Faixas de tempo abaixo do mínimo aparecem sem números.
 */
final class AnaliseCargos {

	static final String OUTROS = "Outros cargos";

	/** Um diagnóstico concluído, só com o que a análise usa. */
	record Linha(Long cargoId, String cargoNome, int cargoOrdem, boolean outro, TempoCargo tempo, int pontuacao,
			String faixa, Map<String, Integer> dimensoes) {

		boolean temCargo() {
			return cargoId != null || outro;
		}

	}

	/** Dimensão do questionário (código e nome), na ordem do relatório. */
	record DimensaoInfo(String codigo, String nome) {
	}

	private AnaliseCargos() {
	}

	/** Nulo quando não há o que mostrar (ninguém informou cargo, ou nenhum cargo chega ao mínimo). */
	static PorCargo montar(List<Linha> linhas, List<FaixaResumo> faixas, List<DimensaoInfo> dimensoes, int minimo) {
		List<Linha> comCargo = linhas.stream().filter(Linha::temCargo).toList();
		if (comCargo.isEmpty()) {
			return null;
		}

		// Cargos na ordem da hierarquia (ordem e, no empate, id).
		Map<Long, List<Linha>> porCargo = comCargo.stream()
			.filter(l -> l.cargoId() != null)
			.sorted(Comparator.comparingInt(Linha::cargoOrdem).thenComparing(Linha::cargoId))
			.collect(Collectors.groupingBy(Linha::cargoId, LinkedHashMap::new, Collectors.toList()));

		List<GrupoCargo> grupos = new ArrayList<>();
		List<Linha> resto = new ArrayList<>(comCargo.stream().filter(Linha::outro).toList());
		int cargosPequenos = 0;
		for (List<Linha> doCargo : porCargo.values()) {
			if (doCargo.size() >= minimo) {
				grupos.add(grupo(doCargo.getFirst().cargoNome(), false, doCargo, faixas, dimensoes));
			}
			else {
				resto.addAll(doCargo);
				cargosPequenos++;
			}
		}
		if (grupos.isEmpty()) {
			return null;
		}

		boolean escolheramOutro = comCargo.stream().anyMatch(Linha::outro);
		String notaAnonimato = null;
		if (!resto.isEmpty() && resto.size() >= minimo) {
			grupos.add(grupo(OUTROS, true, resto, faixas, dimensoes));
			notaAnonimato = notaOutros(cargosPequenos, escolheramOutro, minimo);
		}
		else if (!resto.isEmpty()) {
			notaAnonimato = "%s em cargos com menos de %d respostas %s desta análise, para preservar o anonimato."
				.formatted(lideres(resto.size()), minimo, resto.size() == 1 ? "fica fora" : "ficam fora");
		}

		int semCargo = linhas.size() - comCargo.size();
		String notaSemCargo = semCargo == 0 ? null
				: "%s feito%s antes da lista de cargos não %s nesta análise.".formatted(
						semCargo == 1 ? "1 diagnóstico" : semCargo + " diagnósticos", semCargo == 1 ? "" : "s",
						semCargo == 1 ? "entra" : "entram");

		return new PorCargo(grupos, maisTravado(grupos), dimensoes.stream().map(DimensaoInfo::nome).toList(),
				tempos(comCargo, faixas, minimo), notaAnonimato, notaSemCargo);
	}

	/**
	 * O cargo com mais líderes na faixa mais baixa (no empate, o de menor média). Só faz sentido
	 * comparando dois cargos de verdade ou mais, e se alguém estiver nessa faixa.
	 */
	private static GrupoCargo maisTravado(List<GrupoCargo> grupos) {
		List<GrupoCargo> cargos = grupos.stream().filter(g -> !g.outros()).toList();
		if (cargos.size() < 2) {
			return null;
		}
		GrupoCargo pior = cargos.stream()
			.max(Comparator.comparingInt(GrupoCargo::travados).thenComparing(GrupoCargo::media, Comparator.reverseOrder()))
			.orElseThrow();
		return pior.travados() > 0 ? pior : null;
	}

	private static String notaOutros(int cargosPequenos, boolean escolheramOutro, int minimo) {
		String quem = cargosPequenos > 0 && escolheramOutro
				? "os cargos com menos de %d respostas e quem escolheu “Outro cargo”".formatted(minimo)
				: cargosPequenos > 0 ? "os cargos com menos de %d respostas".formatted(minimo)
						: "quem escolheu “Outro cargo”";
		return "“%s” junta %s, para ninguém ser identificado.".formatted(OUTROS, quem);
	}

	private static List<TempoResumo> tempos(List<Linha> linhas, List<FaixaResumo> faixas, int minimo) {
		List<Linha> comTempo = linhas.stream().filter(l -> l.tempo() != null).toList();
		if (comTempo.size() < minimo) {
			return List.of();
		}
		List<TempoResumo> tempos = new ArrayList<>();
		for (TempoCargo tempo : TempoCargo.values()) {
			List<Linha> daFaixa = comTempo.stream().filter(l -> l.tempo() == tempo).toList();
			if (daFaixa.size() < minimo) {
				tempos.add(new TempoResumo(tempo.texto(), daFaixa.size(), !daFaixa.isEmpty(), null, null, List.of()));
				continue;
			}
			int media = media(daFaixa);
			tempos.add(new TempoResumo(tempo.texto(), daFaixa.size(), false, media, faixaDe(media, faixas),
					partes(daFaixa, faixas)));
		}
		return tempos;
	}

	private static GrupoCargo grupo(String nome, boolean outros, List<Linha> linhas, List<FaixaResumo> faixas,
			List<DimensaoInfo> dimensoes) {
		int media = media(linhas);
		List<ParteFaixa> partes = partes(linhas, faixas);
		List<Celula> celulas = dimensoes.stream().map(d -> {
			List<Integer> valores = linhas.stream().map(l -> l.dimensoes().get(d.codigo())).filter(Objects::nonNull).toList();
			if (valores.isEmpty()) {
				return new Celula(null, null);
			}
			int m = (int) Math.round(valores.stream().mapToInt(Integer::intValue).average().orElse(0));
			return new Celula(m, faixaDe(m, faixas));
		}).toList();
		return new GrupoCargo(nome, outros, linhas.size(), media, faixaDe(media, faixas), partes,
				partes.isEmpty() ? 0 : partes.getFirst().percentual(), celulas);
	}

	/** Quantos líderes e que percentual em cada faixa, da mais baixa para a mais alta. */
	private static List<ParteFaixa> partes(List<Linha> linhas, List<FaixaResumo> faixas) {
		Map<String, Long> contagem = linhas.stream().collect(Collectors.groupingBy(Linha::faixa, Collectors.counting()));
		List<Integer> quantidades = faixas.stream().map(f -> contagem.getOrDefault(f.codigo(), 0L).intValue()).toList();
		List<Integer> percentuais = RelatorioEmpresaService.percentuais(quantidades);
		List<ParteFaixa> partes = new ArrayList<>();
		for (int i = 0; i < faixas.size(); i++) {
			partes.add(new ParteFaixa(faixas.get(i).codigo(), faixas.get(i).rotuloCurto(), quantidades.get(i),
					percentuais.get(i)));
		}
		return partes;
	}

	private static String faixaDe(int valor, List<FaixaResumo> faixas) {
		return faixas.stream()
			.filter(f -> valor >= f.minimo() && valor <= f.maximo())
			.map(FaixaResumo::codigo)
			.findFirst()
			.orElse(faixas.isEmpty() ? null : faixas.getFirst().codigo());
	}

	private static int media(List<Linha> linhas) {
		return (int) Math.round(linhas.stream().mapToInt(Linha::pontuacao).average().orElse(0));
	}

	private static String lideres(int n) {
		return n == 1 ? "1 líder" : n + " líderes";
	}

}
