package br.com.diagnosticolider.empresa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A foto de fundo da empresa: versões (para o endereço mudar a cada troca) e o ponto de foco. */
class FotoDeFundoTest {

	@Test
	void cadaFotoNovaGanhaUmaVersaoEDevolveAAnterior() {
		Empresa empresa = new Empresa();

		assertThat(empresa.trocarFundo(2000, 1250, 38, 47)).isNull();
		assertThat(empresa.getFundoVersao()).isEqualTo(1);
		assertThat(empresa.trocarFundo(1600, 900, 50, 50)).isEqualTo(1);
		assertThat(empresa.getFundoVersao()).isEqualTo(2);
		assertThat(empresa.getFundoLargura()).isEqualTo(1600);
	}

	@Test
	void ofocoFicaEntreZeroECem() {
		Empresa empresa = new Empresa();
		empresa.trocarFundo(2000, 1250, -10, 140);

		assertThat(empresa.getFundoFocoX()).isZero();
		assertThat(empresa.getFundoFocoY()).isEqualTo(100);
	}

	@Test
	void removerApagaAFotoEVoltaOFocoAoCentro() {
		Empresa empresa = new Empresa();
		empresa.trocarFundo(2000, 1250, 20, 80);

		assertThat(empresa.removerFundo()).isEqualTo(1);
		assertThat(empresa.getFundoVersao()).isNull();
		assertThat(empresa.getFundoFocoX()).isEqualTo(50);
		assertThat(empresa.getFundoFocoY()).isEqualTo(50);
	}

	@Test
	void oCaminhoNoBucketTemAVersao() {
		assertThat(FotoDeFundoService.caminho(5L, 3)).isEqualTo("empresas/5/fundo-v3.jpg");
	}

}
