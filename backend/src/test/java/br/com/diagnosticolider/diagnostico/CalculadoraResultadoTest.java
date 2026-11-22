package br.com.diagnosticolider.diagnostico;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.diagnosticolider.diagnostico.CalculadoraResultado.Item;

class CalculadoraResultadoTest {

	private final CalculadoraResultado calculadora = new CalculadoraResultado();

	@Test
	void piorRespostaEmTudoValeZeroEMelhorValeCem() {
		assertThat(calculadora.calcular(List.of(item("A", 1), item("B", 1), item("C", 1))).geral()).isZero();
		assertThat(calculadora.calcular(List.of(item("A", 4), item("B", 4), item("C", 4))).geral()).isEqualTo(100);
	}

	@Test
	void normalizaPelaFaixaDePontosPossivel() {
		// Obtido 3+2+3 = 8 numa escala de 3 a 12 → (8-3)/(12-3) = 55,6% → 56
		var calculo = calculadora.calcular(List.of(item("DIRECAO", 3), item("DESENVOLVIMENTO", 2), item("VISIBILIDADE", 3)));

		assertThat(calculo.geral()).isEqualTo(56);
		assertThat(calculo.porDimensao()).containsExactly(
				org.assertj.core.api.Assertions.entry("DIRECAO", 67),
				org.assertj.core.api.Assertions.entry("DESENVOLVIMENTO", 33),
				org.assertj.core.api.Assertions.entry("VISIBILIDADE", 67));
	}

	@Test
	void perguntaSemDimensaoContaSoNoGeral() {
		var calculo = calculadora.calcular(List.of(item("A", 4), item(null, 1)));

		assertThat(calculo.geral()).isEqualTo(50);
		assertThat(calculo.porDimensao()).containsOnlyKeys("A");
	}

	private static Item item(String dimensao, int obtido) {
		return new Item(dimensao, 1, 4, obtido);
	}

}
