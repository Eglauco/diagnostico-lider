package br.com.diagnosticolider.relatorio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.diagnosticolider.diagnostico.TempoCargo;
import br.com.diagnosticolider.relatorio.AnaliseCargos.DimensaoInfo;
import br.com.diagnosticolider.relatorio.AnaliseCargos.Linha;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.FaixaResumo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.GrupoCargo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.PorCargo;

/** A análise por cargo nunca pode expor um grupo com menos líderes que o mínimo. */
class AnaliseCargosTest {

	private static final List<FaixaResumo> FAIXAS = List.of(
			new FaixaResumo("TRAVADA", "Carreira travada", null, 0, 39, 0, 0, List.of()),
			new FaixaResumo("PLATO", "Carreira no platô", null, 40, 69, 0, 0, List.of()),
			new FaixaResumo("ASCENSAO", "Carreira em ascensão", null, 70, 100, 0, 0, List.of()));

	private static final List<DimensaoInfo> DIMENSOES = List.of(new DimensaoInfo("DIR", "Direção"),
			new DimensaoInfo("VIS", "Visibilidade"));

	@Test
	void mostraCadaCargoComOMinimoNaOrdemDaHierarquia() {
		List<Linha> linhas = new ArrayList<>();
		linhas.addAll(lideres(2L, "Gerente", 1, 3, 20));
		linhas.addAll(lideres(1L, "Coordenador", 0, 4, 80));

		PorCargo pc = AnaliseCargos.montar(linhas, FAIXAS, DIMENSOES, 3);

		assertThat(pc.grupos()).extracting(GrupoCargo::nome).containsExactly("Coordenador", "Gerente");
		assertThat(pc.grupos()).extracting(GrupoCargo::lideres).containsExactly(4, 3);
		assertThat(pc.notaAnonimato()).isNull();
	}

	@Test
	void cargoPequenoEOutroCargoViramOutrosQuandoSomamOMinimo() {
		List<Linha> linhas = new ArrayList<>();
		linhas.addAll(lideres(1L, "Coordenador", 0, 3, 50));
		linhas.addAll(lideres(9L, "Diretor", 3, 1, 90));
		linhas.add(outro(30));
		linhas.add(outro(35));

		PorCargo pc = AnaliseCargos.montar(linhas, FAIXAS, DIMENSOES, 3);

		assertThat(pc.grupos()).extracting(GrupoCargo::nome).containsExactly("Coordenador", AnaliseCargos.OUTROS);
		assertThat(pc.grupos().getLast().lideres()).isEqualTo(3);
		assertThat(pc.grupos()).noneMatch(g -> g.nome().equals("Diretor"));
		assertThat(pc.notaAnonimato()).contains("Outro cargo").contains("menos de 3");
	}

	@Test
	void outrosAbaixoDoMinimoFicamDeForaComAviso() {
		List<Linha> linhas = new ArrayList<>();
		linhas.addAll(lideres(1L, "Coordenador", 0, 3, 50));
		linhas.addAll(lideres(9L, "Diretor", 3, 1, 90));

		PorCargo pc = AnaliseCargos.montar(linhas, FAIXAS, DIMENSOES, 3);

		assertThat(pc.grupos()).extracting(GrupoCargo::nome).containsExactly("Coordenador");
		assertThat(pc.notaAnonimato()).startsWith("1 líder").contains("fica fora");
	}

	@Test
	void semNenhumCargoComOMinimoNaoHaSecao() {
		List<Linha> linhas = new ArrayList<>();
		linhas.addAll(lideres(1L, "Coordenador", 0, 2, 50));
		linhas.addAll(lideres(2L, "Gerente", 1, 2, 50));

		assertThat(AnaliseCargos.montar(linhas, FAIXAS, DIMENSOES, 3)).isNull();
	}

	@Test
	void maisTravadoEOCargoComMaisLideresNaFaixaMaisBaixa() {
		List<Linha> linhas = new ArrayList<>();
		linhas.addAll(lideres(1L, "Coordenador", 0, 3, 75));
		linhas.addAll(lideres(2L, "Gerente", 1, 3, 25));

		PorCargo pc = AnaliseCargos.montar(linhas, FAIXAS, DIMENSOES, 3);

		assertThat(pc.maisTravado().nome()).isEqualTo("Gerente");
		assertThat(pc.maisTravado().travados()).isEqualTo(100);
		assertThat(pc.maisTravado().dimensoes()).extracting(c -> c.faixa()).containsOnly("TRAVADA");
	}

	@Test
	void faixaDeTempoAbaixoDoMinimoApareceSemNumeros() {
		List<Linha> linhas = new ArrayList<>(lideres(1L, "Coordenador", 0, 3, 50));
		linhas.add(new Linha(1L, "Coordenador", 0, false, TempoCargo.MAIS_DE_5, 20, "TRAVADA", Map.of()));

		PorCargo pc = AnaliseCargos.montar(linhas, FAIXAS, DIMENSOES, 3);

		var maisDe5 = pc.tempos().getLast();
		assertThat(maisDe5.oculto()).isTrue();
		assertThat(maisDe5.media()).isNull();
		assertThat(pc.tempos().get(1).media()).isEqualTo(50);
	}

	private static List<Linha> lideres(Long cargo, String nome, int ordem, int quantos, int pontuacao) {
		String faixa = pontuacao < 40 ? "TRAVADA" : pontuacao < 70 ? "PLATO" : "ASCENSAO";
		return java.util.stream.IntStream.range(0, quantos)
			.mapToObj(i -> new Linha(cargo, nome, ordem, false, TempoCargo.DE_1_A_2, pontuacao, faixa,
					Map.of("DIR", pontuacao, "VIS", pontuacao)))
			.toList();
	}

	private static Linha outro(int pontuacao) {
		return new Linha(null, null, 0, true, TempoCargo.MENOS_DE_1, pontuacao, pontuacao < 40 ? "TRAVADA" : "PLATO",
				Map.of("DIR", pontuacao));
	}

}
