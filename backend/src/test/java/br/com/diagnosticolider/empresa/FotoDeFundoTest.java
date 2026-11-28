package br.com.diagnosticolider.empresa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** As fotos da página do RH (entrada e topo): versões (o endereço muda a cada troca) e o ponto de foco. */
class FotoDeFundoTest {

	@Test
	void cadaFotoNovaGanhaUmaVersaoEDevolveAAnterior() {
		FotoDaEmpresa foto = new FotoDaEmpresa();

		assertThat(foto.trocar(2000, 1250, 38, 47)).isNull();
		assertThat(foto.getVersao()).isEqualTo(1);
		assertThat(foto.trocar(1600, 900, 50, 50)).isEqualTo(1);
		assertThat(foto.getVersao()).isEqualTo(2);
		assertThat(foto.getLargura()).isEqualTo(1600);
	}

	@Test
	void oFocoFicaEntreZeroECem() {
		FotoDaEmpresa foto = new FotoDaEmpresa();
		foto.trocar(2000, 1250, -10, 140);

		assertThat(foto.getFocoX()).isZero();
		assertThat(foto.getFocoY()).isEqualTo(100);
	}

	@Test
	void removerApagaAFotoEVoltaOFocoAoCentro() {
		FotoDaEmpresa foto = new FotoDaEmpresa();
		foto.trocar(2000, 1250, 20, 80);

		assertThat(foto.remover()).isEqualTo(1);
		assertThat(foto.existe()).isFalse();
		assertThat(foto.getFocoX()).isEqualTo(50);
		assertThat(foto.getFocoY()).isEqualTo(50);
	}

	@Test
	void aEntradaEOTopoSaoFotosSeparadas() {
		Empresa empresa = new Empresa("Construtora");
		empresa.getCapa().trocar(2400, 800, 50, 30);

		assertThat(empresa.getCapa().existe()).isTrue();
		assertThat(empresa.getFundo().existe()).isFalse();
	}

	@Test
	void oCaminhoNoBucketTemOTipoEAVersao() {
		assertThat(FotoDeFundoService.caminho(FotoDeFundoService.Tipo.FUNDO, 5L, 3)).isEqualTo("empresas/5/fundo-v3.jpg");
		assertThat(FotoDeFundoService.caminho(FotoDeFundoService.Tipo.CAPA, 5L, 1)).isEqualTo("empresas/5/capa-v1.jpg");
	}

}
