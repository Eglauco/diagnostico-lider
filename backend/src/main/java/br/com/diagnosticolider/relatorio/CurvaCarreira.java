package br.com.diagnosticolider.relatorio;

import java.util.Locale;
import java.util.function.DoubleUnaryOperator;

/**
 * A curva da carreira: sobe, estabiliza no platô e, se nada muda, começa a cair. Uma
 * segunda curva (tracejada) nasce no platô e representa o próximo salto.
 *
 * <p>É a mesma função usada no front (frontend/src/app/compartilhado/curva-carreira.ts):
 * se mudar aqui, mude lá, para o PDF e a tela mostrarem o mesmo desenho.
 */
public final class CurvaCarreira {

	/** Posição na curva (0–1) de quem tem índice 100 e de quem tem índice 0. */
	private static final double T_INDICE_100 = 0.28;

	private static final double T_INDICE_0 = 0.92;

	/** Altura máxima desenhada, com folga para a segunda curva passar do platô. */
	private static final double ALTURA_MAXIMA = 1.25;

	private static final int AMOSTRAS = 96;

	public record Ponto(double x, double y) {
	}

	public record Rotulo(String texto, double x, double y) {
	}

	public record Desenho(double largura, double altura, String curva, String proximaCurva, String area, Ponto voce,
			Rotulo subida, Rotulo plato, Rotulo queda, double base) {
	}

	private CurvaCarreira() {
	}

	static double altura(double t) {
		double subida = 1 / (1 + Math.exp(-11 * (t - 0.30)));
		double queda = t > 0.66 ? Math.pow((t - 0.66) / 0.34, 2) * 0.42 : 0;
		return subida - queda;
	}

	static double proxima(double t) {
		return 0.55 + 0.68 / (1 + Math.exp(-13 * (t - 0.80)));
	}

	/** Índice alto fica na subida; índice baixo, depois do platô, já perdendo altura. */
	public static double posicao(int indice) {
		return T_INDICE_0 - (indice / 100.0) * (T_INDICE_0 - T_INDICE_100);
	}

	public static Desenho desenhar(double largura, double altura, int indice) {
		double margemX = 10;
		double margemTopo = 14;
		double margemBase = 26;
		DoubleUnaryOperator x = t -> margemX + t * (largura - 2 * margemX);
		DoubleUnaryOperator y = v -> altura - margemBase - (v / ALTURA_MAXIMA) * (altura - margemTopo - margemBase);

		String curva = caminho(0, 1, CurvaCarreira::altura, x, y);
		String proximaCurva = caminho(0.55, 1, CurvaCarreira::proxima, x, y);
		double base = altura - margemBase;
		String area = curva + " L%s %s L%s %s Z".formatted(num(x.applyAsDouble(1)), num(base), num(x.applyAsDouble(0)),
				num(base));

		double t = posicao(indice);
		var voce = new Ponto(x.applyAsDouble(t), y.applyAsDouble(altura(t)));
		double yRotulo = altura - 8;
		return new Desenho(largura, altura, curva, proximaCurva, area, voce,
				new Rotulo("Subida", x.applyAsDouble(0.24), yRotulo), new Rotulo("Platô", x.applyAsDouble(0.62), yRotulo),
				new Rotulo("Queda", x.applyAsDouble(0.92), yRotulo), base);
	}

	private static String caminho(double inicio, double fim, DoubleUnaryOperator funcao, DoubleUnaryOperator x,
			DoubleUnaryOperator y) {
		var sb = new StringBuilder();
		for (int i = 0; i <= AMOSTRAS; i++) {
			double t = inicio + (fim - inicio) * i / AMOSTRAS;
			sb.append(i == 0 ? "M" : " L")
				.append(num(x.applyAsDouble(t)))
				.append(' ')
				.append(num(y.applyAsDouble(funcao.applyAsDouble(t))));
		}
		return sb.toString();
	}

	private static String num(double valor) {
		return String.format(Locale.ROOT, "%.1f", valor);
	}

}
