package br.com.diagnosticolider.diagnostico;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Converte pontos em um índice de 0 a 100. A pior resposta possível vale 0 e a melhor
 * vale 100, então trocar a escala de pontos das opções não muda o resultado relativo.
 * Não conhece JPA: recebe só números, o que deixa a regra fácil de testar.
 */
@Component
public class CalculadoraResultado {

	/** Uma pergunta respondida: faixa de pontos possível e quanto o líder obteve. */
	public record Item(String dimensao, int minimo, int maximo, int obtido) {
	}

	public record Calculo(int geral, Map<String, Integer> porDimensao) {
	}

	public Calculo calcular(List<Item> itens) {
		Map<String, Integer> porDimensao = itens.stream()
			.filter(item -> item.dimensao() != null)
			.collect(Collectors.groupingBy(Item::dimensao, LinkedHashMap::new,
					Collectors.collectingAndThen(Collectors.toList(), CalculadoraResultado::indice)));
		return new Calculo(indice(itens), porDimensao);
	}

	static int indice(List<Item> itens) {
		Objects.requireNonNull(itens);
		int minimo = itens.stream().mapToInt(Item::minimo).sum();
		int maximo = itens.stream().mapToInt(Item::maximo).sum();
		int obtido = itens.stream().mapToInt(Item::obtido).sum();
		if (maximo == minimo) {
			return 100;
		}
		double percentual = 100.0 * (obtido - minimo) / (maximo - minimo);
		return (int) Math.round(Math.clamp(percentual, 0.0, 100.0));
	}

}
