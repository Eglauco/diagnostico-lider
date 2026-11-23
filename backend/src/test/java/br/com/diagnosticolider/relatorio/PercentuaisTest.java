package br.com.diagnosticolider.relatorio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** No relatório da empresa, cada distribuição precisa somar exatamente 100%. */
class PercentuaisTest {

	@Test
	void somaCemMesmoComDizimasIguais() {
		assertThat(RelatorioEmpresaService.percentuais(List.of(1, 1, 1))).containsExactly(34, 33, 33);
		assertThat(RelatorioEmpresaService.percentuais(List.of(1, 2, 3))).containsExactly(17, 33, 50);
	}

	@Test
	void valoresExatosNaoMudam() {
		assertThat(RelatorioEmpresaService.percentuais(List.of(2, 5, 3))).containsExactly(20, 50, 30);
	}

	@Test
	void semRespostasFicaTudoZero() {
		assertThat(RelatorioEmpresaService.percentuais(List.of(0, 0, 0))).containsExactly(0, 0, 0);
	}

}
