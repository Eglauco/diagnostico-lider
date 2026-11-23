package br.com.diagnosticolider.empresa;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Gera a paleta do questionário, dos PDFs e do e-mail a partir das cores da marca da empresa.
 *
 * <p>Trabalha em OKLCH (luminosidade percebida, croma e matiz): mudar só a luminosidade mantém
 * o "jeito" da cor. Cada tom é conferido pelo contraste do WCAG: textos com 4,5:1 e gráficos
 * (curva, foco) com 3:1. Quando a cor da marca não passa, ela é ajustada e o painel avisa.
 */
public final class PaletaMarca {

	/** Os tokens de cor da interface (mesmos nomes do front: ver tema-empresa.ts). */
	public record Tema(String acao, String acaoForte, String sobreAcao, String tinta, String tinta2, String tinta3,
			String fundo, String fundo2, String linha, String destaque, String destaqueTexto, String sobreDestaque,
			String destaqueSobreTinta, String tintaBrilho) {
	}

	/** O tema e o que precisou ser ajustado (mostrado no cadastro da empresa). */
	public record Resultado(Tema tema, List<String> ajustes) {
	}

	/** A identidade do Diagnóstico do Líder (link geral e empresas sem cor definida). */
	public static final Tema PADRAO = new Tema("#0F2B26", "#17403A", "#FFFFFF", "#0F2B26", "#4A635C", "#7D918B",
			"#EDF2EF", "#F5F8F6", "#D2DDD8", "#FF5B37", "#C23A1C", "#0F2B26", "#FF5B37", "#2E6B5F");

	private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})");

	private static final double TEXTO = 4.5;

	private static final double GRAFICO = 3.0;

	/** Cores das faixas: a marca não deve ser confundida com elas na curva. */
	private static final List<Object[]> FAIXAS = List.of(new Object[] { "#C2364B", "o vermelho", "Travada" },
			new Object[] { "#A86A00", "o âmbar", "Platô" }, new Object[] { "#1F8A5B", "o verde", "Ascensão" });

	private PaletaMarca() {
	}

	/** "#0057b8", "0057B8" ou "#05B" → "#0057B8"; inválida → null. */
	public static String normalizar(String cor) {
		if (cor == null || cor.isBlank()) {
			return null;
		}
		var m = HEX.matcher(cor.strip());
		if (!m.matches()) {
			return null;
		}
		String hex = m.group(1).toUpperCase(Locale.ROOT);
		if (hex.length() == 3) {
			hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
		}
		return "#" + hex;
	}

	public static Tema tema(String primaria, String secundaria) {
		return gerar(primaria, secundaria).tema();
	}

	public static Resultado gerar(String primaria, String secundaria) {
		String p = normalizar(primaria);
		if (p == null) {
			return new Resultado(PADRAO, List.of());
		}
		String s = normalizar(secundaria);
		List<String> ajustes = new ArrayList<>();
		Oklch marca = Oklch.deHex(p);
		double c = marca.c();
		double h = marca.h();

		Oklch tinta = new Oklch(0.255, Math.min(c * 0.35, 0.045), h).noGamut().arredondada();
		Oklch tinta2 = new Oklch(0.47, Math.min(c * 0.25, 0.035), h).noGamut().arredondada();
		Oklch tinta3 = new Oklch(0.615, Math.min(c * 0.2, 0.03), h).noGamut().arredondada();
		Oklch fundo = new Oklch(0.958, Math.min(c * 0.12, 0.014), h).noGamut().arredondada();
		Oklch fundo2 = new Oklch(0.979, Math.min(c * 0.08, 0.008), h).noGamut().arredondada();
		Oklch linha = new Oklch(0.885, Math.min(c * 0.18, 0.02), h).noGamut().arredondada();
		Oklch brilho = new Oklch(0.46, Math.min(c * 0.6, 0.08), h).noGamut().arredondada();

		// Botões: a cor da marca com texto branco; se não der leitura, texto escuro; senão, escurece.
		Oklch acao = marca;
		Oklch sobreAcao = Oklch.BRANCO;
		if (contraste(acao, Oklch.BRANCO) < TEXTO) {
			if (contraste(acao, tinta) >= TEXTO) {
				sobreAcao = tinta;
				ajustes.add("A cor principal é clara: os botões usam texto escuro para continuar legíveis.");
			}
			else {
				acao = escurecer(acao, cor -> contraste(cor, Oklch.BRANCO) >= TEXTO);
				ajustes.add("A cor principal foi escurecida um pouco nos botões para o texto continuar legível.");
			}
		}
		if (contraste(acao, fundo) < 1.25) {
			ajustes.add("A cor principal é muito clara e quase some no fundo: prefira um tom mais forte.");
		}
		Oklch acaoForte = new Oklch(Math.max(0, acao.l() - 0.06), acao.c(), acao.h()).noGamut().arredondada();

		// Curva e destaques: a cor secundária (ou a principal), visível sobre o fundo claro.
		Oklch destaqueBase = s == null ? marca : Oklch.deHex(s);
		String nomeDestaque = s == null ? "A cor principal" : "A cor secundária";
		Oklch destaque = destaqueBase;
		if (contraste(destaque, fundo) < GRAFICO) {
			destaque = escurecer(destaque, cor -> contraste(cor, fundo) >= GRAFICO);
			ajustes.add(nomeDestaque + " foi escurecida na curva da carreira para aparecer sobre o fundo claro.");
		}
		Oklch destaqueTexto = escurecer(destaqueBase, cor -> contraste(cor, fundo) >= TEXTO);
		Oklch sobreDestaque = contraste(destaque, Oklch.BRANCO) >= contraste(destaque, tinta) ? Oklch.BRANCO : tinta;
		Oklch destaqueSobreTinta = destaqueBase;
		if (contraste(destaqueSobreTinta, tinta) < GRAFICO) {
			destaqueSobreTinta = clarear(destaqueSobreTinta, cor -> contraste(cor, tinta) >= GRAFICO);
		}

		// Só importa a cor que vai para a curva, onde ficam os pontos coloridos das faixas.
		avisarSemelhanca(destaqueBase, nomeDestaque, ajustes);

		var tema = new Tema(acao.hex(), acaoForte.hex(), sobreAcao.hex(), tinta.hex(), tinta2.hex(), tinta3.hex(),
				fundo.hex(), fundo2.hex(), linha.hex(), destaque.hex(), destaqueTexto.hex(), sobreDestaque.hex(),
				destaqueSobreTinta.hex(), brilho.hex());
		return new Resultado(tema, List.copyOf(ajustes));
	}

	/** A curva mostra os líderes em vermelho, âmbar e verde: avisa se a marca se parece com eles. */
	private static void avisarSemelhanca(Oklch cor, String nome, List<String> ajustes) {
		if (cor.c() < 0.08) {
			return;
		}
		for (Object[] faixa : FAIXAS) {
			Oklch f = Oklch.deHex((String) faixa[0]);
			double diferenca = Math.abs(((cor.h() - f.h()) % 360 + 540) % 360 - 180);
			if (diferenca < 20) {
				ajustes.add("%s lembra %s da faixa “%s”: na curva, os pontos dos líderes dessa faixa podem se confundir com ela."
					.formatted(nome, faixa[1], faixa[2]));
				return;
			}
		}
	}

	private static Oklch escurecer(Oklch cor, Predicate<Oklch> ok) {
		Oklch atual = cor.noGamut().arredondada();
		for (double l = cor.l(); l >= 0; l -= 0.01) {
			atual = new Oklch(l, cor.c(), cor.h()).noGamut().arredondada();
			if (ok.test(atual)) {
				return atual;
			}
		}
		return atual;
	}

	private static Oklch clarear(Oklch cor, Predicate<Oklch> ok) {
		Oklch atual = cor.noGamut().arredondada();
		for (double l = cor.l(); l <= 1; l += 0.01) {
			atual = new Oklch(l, cor.c(), cor.h()).noGamut().arredondada();
			if (ok.test(atual)) {
				return atual;
			}
		}
		return atual;
	}

	/** Contraste do WCAG 2 entre duas cores (1 a 21). */
	public static double contraste(String a, String b) {
		return contraste(Oklch.deHex(a), Oklch.deHex(b));
	}

	static double contraste(Oklch a, Oklch b) {
		double la = a.luminancia();
		double lb = b.luminancia();
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	/** Uma cor em OKLCH (L 0–1, croma, matiz em graus). */
	record Oklch(double l, double c, double h) {

		static final Oklch BRANCO = deHex("#FFFFFF");

		static Oklch deHex(String hex) {
			int v = Integer.parseInt(hex.substring(1), 16);
			double r = linear(((v >> 16) & 0xFF) / 255.0);
			double g = linear(((v >> 8) & 0xFF) / 255.0);
			double b = linear((v & 0xFF) / 255.0);
			double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
			double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
			double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
			double ll = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
			double aa = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
			double bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;
			double croma = Math.hypot(aa, bb);
			double matiz = (Math.toDegrees(Math.atan2(bb, aa)) + 360) % 360;
			return new Oklch(ll, croma, matiz);
		}

		/** RGB linear (pode sair de 0–1 se a cor estiver fora do sRGB). */
		double[] rgbLinear() {
			double a = c * Math.cos(Math.toRadians(h));
			double b = c * Math.sin(Math.toRadians(h));
			double l1 = Math.pow(l + 0.3963377774 * a + 0.2158037573 * b, 3);
			double m1 = Math.pow(l - 0.1055613458 * a - 0.0638541728 * b, 3);
			double s1 = Math.pow(l - 0.0894841775 * a - 1.2914855480 * b, 3);
			return new double[] { 4.0767416621 * l1 - 3.3077115913 * m1 + 0.2309699292 * s1,
					-1.2684380046 * l1 + 2.6097574011 * m1 - 0.3413193965 * s1,
					-0.0041960863 * l1 - 0.7034186147 * m1 + 1.7076147010 * s1 };
		}

		boolean noSrgb() {
			for (double v : rgbLinear()) {
				if (v < -0.0005 || v > 1.0005) {
					return false;
				}
			}
			return true;
		}

		/** Reduz o croma até a cor existir no sRGB (mantém luminosidade e matiz). */
		Oklch noGamut() {
			if (noSrgb()) {
				return this;
			}
			double baixo = 0;
			double alto = c;
			for (int i = 0; i < 24; i++) {
				double meio = (baixo + alto) / 2;
				if (new Oklch(l, meio, h).noSrgb()) {
					baixo = meio;
				}
				else {
					alto = meio;
				}
			}
			return new Oklch(l, baixo, h);
		}

		double luminancia() {
			double[] rgb = rgbLinear();
			return 0.2126 * limitar(rgb[0]) + 0.7152 * limitar(rgb[1]) + 0.0722 * limitar(rgb[2]);
		}

		/** A cor como ela vai para a tela (hex de 8 bits por canal). */
		Oklch arredondada() {
			return deHex(hex());
		}

		String hex() {
			double[] rgb = rgbLinear();
			return "#%02X%02X%02X".formatted(canal(rgb[0]), canal(rgb[1]), canal(rgb[2]));
		}

		private static double linear(double v) {
			return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
		}

		private static int canal(double linear) {
			double v = limitar(linear);
			double srgb = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
			return (int) Math.round(Math.min(1, Math.max(0, srgb)) * 255);
		}

		private static double limitar(double v) {
			return Math.min(1, Math.max(0, v));
		}

	}

}
