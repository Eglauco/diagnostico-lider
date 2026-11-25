package br.com.diagnosticolider.relatorio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.CurvaGrupo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.EtiquetaMedia;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaService.Lider;

/** Capa do relatório da empresa: as bolinhas nunca cobrem a etiqueta da média nem saem do desenho. */
class CurvaCapaTest {

	private static List<Lider> lideres(int... indices) {
		return IntStream.of(indices).mapToObj(i -> new Lider(i, i >= 70 ? "ASCENSAO" : i >= 40 ? "PLATO" : "TRAVADA")).toList();
	}

	private static List<Lider> repetir(int indice, int vezes) {
		List<Lider> lista = new ArrayList<>();
		for (int i = 0; i < vezes; i++) {
			lista.add(new Lider(indice, "PLATO"));
		}
		return lista;
	}

	static List<Object[]> grupos() {
		return List.of(
				// O caso da tela: 22 líderes com pilhas de 4 em volta da média 54.
				new Object[] { "grupo real", lideres(86, 86, 80, 80, 80, 76, 76, 72, 66, 61, 61, 61, 61, 54, 54, 54, 54, 44,
						44, 44, 38, 38), 54 },
				new Object[] { "200 com o mesmo índice", repetir(54, 200), 54 },
				new Object[] { "média muito alta", lideres(100, 100, 98, 97, 97, 95), 98 },
				new Object[] { "média zero", lideres(0, 0, 0, 2, 5), 1 },
				new Object[] { "todos no topo da subida", repetir(100, 30), 100 });
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("grupos")
	void bolinhasNaoCobremAEtiquetaNemSaemDoDesenho(String caso, List<Lider> grupo, int media) {
		CurvaGrupo curva = RelatorioEmpresaService.curva(grupo, media);
		EtiquetaMedia m = curva.media();
		double esquerda = m.x() - RelatorioEmpresaService.ETIQUETA_LARGURA / 2;
		double direita = m.x() + RelatorioEmpresaService.ETIQUETA_LARGURA / 2;
		double topo = m.y();
		double baixo = m.y() + RelatorioEmpresaService.ETIQUETA_ALTURA;

		assertThat(curva.pontos()).hasSize(grupo.size());
		assertThat(esquerda).isGreaterThanOrEqualTo(0);
		assertThat(direita).isLessThanOrEqualTo(curva.desenho().largura());
		assertThat(baixo).isLessThanOrEqualTo(curva.desenho().base());
		for (var p : curva.pontos()) {
			// Dentro do desenho (acima dele vem o texto "N líderes concluíram…").
			assertThat(p.y() - curva.raio()).as("%s: bolinha saindo pelo alto", caso).isGreaterThanOrEqualTo(0);
			// Longe da etiqueta: o ponto do retângulo mais perto do centro fica fora do círculo.
			double cx = Math.clamp(p.x(), esquerda, direita);
			double cy = Math.clamp(p.y(), topo, baixo);
			assertThat(Math.hypot(p.x() - cx, p.y() - cy)).as("%s: bolinha em cima da etiqueta", caso)
				.isGreaterThan(curva.raio());
		}
		// Embaixo da curva, a etiqueta não pode cruzar a linha da curva.
		if (m.y() > 0) {
			for (double x = esquerda; x <= direita; x += 2) {
				assertThat(CurvaCarreira.yNoX(curva.desenho().largura(), curva.desenho().altura(), x))
					.as("%s: curva atravessando a etiqueta", caso)
					.isLessThan(topo);
			}
		}
	}

}
