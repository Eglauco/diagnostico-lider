package br.com.diagnosticolider.empresa;

import static br.com.diagnosticolider.empresa.PaletaMarca.contraste;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class PaletaMarcaTest {

	@Test
	void normalizaOsFormatosDeHex() {
		assertThat(PaletaMarca.normalizar("#0057b8")).isEqualTo("#0057B8");
		assertThat(PaletaMarca.normalizar("0057B8")).isEqualTo("#0057B8");
		assertThat(PaletaMarca.normalizar(" #05b ")).isEqualTo("#0055BB");
		assertThat(PaletaMarca.normalizar("azul")).isNull();
		assertThat(PaletaMarca.normalizar("")).isNull();
	}

	@Test
	void semCorUsaAIdentidadeDoDiagnostico() {
		assertThat(PaletaMarca.tema(null, null)).isEqualTo(PaletaMarca.PADRAO);
		assertThat(PaletaMarca.gerar(null, "#FF0000").ajustes()).isEmpty();
	}

	@Test
	void conversaoDeIdaEVoltaPreservaACor() {
		for (String hex : new String[] { "#0057B8", "#FFD100", "#E30613", "#111111", "#FF5B37" }) {
			assertThat(PaletaMarca.Oklch.deHex(hex).hex()).isEqualTo(hex);
		}
	}

	/** Qualquer cor da marca precisa gerar uma interface legível. */
	@ParameterizedTest
	@CsvSource({ "#0057B8,", "#FFD100,", "#E30613,#111111", "#00A859,#FFD100", "#111111,", "#F5F5F5,#F0F0F0",
			"#7FFFD4,", "#6A1B9A,#FF6F00", "#000000,#FFFFFF" })
	void todaPaletaTemContrasteSuficiente(String primaria, String secundaria) {
		var t = PaletaMarca.tema(primaria, secundaria);
		assertThat(contraste(t.acao(), t.sobreAcao())).as("botão").isGreaterThanOrEqualTo(4.5);
		assertThat(contraste(t.tinta(), t.fundo())).as("texto").isGreaterThanOrEqualTo(10);
		assertThat(contraste(t.tinta2(), t.fundo())).as("texto secundário").isGreaterThanOrEqualTo(4.5);
		assertThat(contraste(t.destaque(), t.fundo())).as("curva").isGreaterThanOrEqualTo(3);
		assertThat(contraste(t.destaqueTexto(), t.fundo())).as("rótulo em destaque").isGreaterThanOrEqualTo(4.5);
		assertThat(contraste(t.destaqueSobreTinta(), t.tinta())).as("curva no fundo escuro").isGreaterThanOrEqualTo(3);
	}

	@Test
	void corClaraGanhaTextoEscuroNosBotoes() {
		var r = PaletaMarca.gerar("#FFD100", null);
		assertThat(r.tema().acao()).isEqualTo("#FFD100");
		assertThat(r.tema().sobreAcao()).isEqualTo(r.tema().tinta());
		assertThat(r.ajustes()).anyMatch(a -> a.contains("texto escuro"));
	}

	@Test
	void corForteFicaComoEstaNosBotoes() {
		var r = PaletaMarca.gerar("#0057B8", null);
		assertThat(r.tema().acao()).isEqualTo("#0057B8");
		assertThat(r.tema().sobreAcao()).isEqualTo("#FFFFFF");
	}

	@Test
	void avisaQuandoACurvaLembraUmaFaixa() {
		assertThat(PaletaMarca.gerar("#0057B8", "#D32F2F").ajustes()).anyMatch(a -> a.contains("Travada"));
		assertThat(PaletaMarca.gerar("#0057B8", "#1E9E5A").ajustes()).anyMatch(a -> a.contains("Ascensão"));
		assertThat(PaletaMarca.gerar("#0057B8", "#7E57C2").ajustes()).noneMatch(a -> a.contains("faixa"));
	}

}
