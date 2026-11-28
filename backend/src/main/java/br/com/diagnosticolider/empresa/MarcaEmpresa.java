package br.com.diagnosticolider.empresa;

/**
 * O que as telas do líder precisam saber da empresa do link: o nome (em destaque, com
 * "por Diagnóstico do Líder" discreto) e a paleta. "personalizada" diz se a empresa tem
 * cores próprias ou usa a identidade do Diagnóstico.
 */
public record MarcaEmpresa(String nome, String sigla, boolean personalizada, PaletaMarca.Tema tema, Logo logo,
		Foto fundo, Foto capa) {

	/**
	 * Uma foto da página do RH (null sem foto): fundo é a da entrada (em pé ou quadrada), capa é a
	 * do topo da página e da faixa do celular (deitada). O endereço (assinado, do bucket, ou o do
	 * backend), o tamanho e o ponto de foco (0 a 100), que vira o object-position do corte.
	 */
	public record Foto(String url, int largura, int altura, int focoX, int focoY) {
	}

	/**
	 * Logomarca enviada no cadastro e o tamanho, para o layout. A url é direta do bucket
	 * (assinada, vale por dias) ou, sem bucket, a do backend. Monte com LogomarcaService.logo.
	 */
	public record Logo(String url, int largura, int altura) {

		/** Logo "quadrada" (só o símbolo) pede o nome ao lado; logo larga já traz o nome. */
		public boolean pedeNome() {
			return largura < altura * 1.8;
		}

	}

	/** Palavras que não entram nas iniciais ("Grupo de Ação" → GA, não GD). */
	private static final java.util.Set<String> CONECTIVOS = java.util.Set.of("de", "da", "do", "das", "dos", "e", "a",
			"o", "the", "of", "and", "s/a", "sa", "ltda", "me", "eireli");

	/** Iniciais para o monograma (o "logo" enquanto a empresa não tem logo): "Construtora Horizonte" → CH. */
	public static String siglaDe(String nome) {
		var palavras = java.util.Arrays.stream(nome.strip().split("[\\s&.,/-]+"))
			.filter(p -> !p.isBlank() && Character.isLetterOrDigit(p.codePointAt(0)))
			.filter(p -> !CONECTIVOS.contains(p.toLowerCase(java.util.Locale.ROOT)))
			.toList();
		if (palavras.isEmpty()) {
			return "?";
		}
		String sigla = palavras.size() == 1 ? palavras.getFirst().substring(0, Math.min(2, palavras.getFirst().length()))
				: "" + palavras.get(0).charAt(0) + palavras.get(1).charAt(0);
		return sigla.toUpperCase(java.util.Locale.ROOT);
	}

	/** Use LogomarcaService.marca, que já resolve o endereço da logo. */
	static MarcaEmpresa de(Empresa empresa, Logo logo, Foto fundo, Foto capa) {
		return new MarcaEmpresa(empresa.getNome(), siglaDe(empresa.getNome()), empresa.getCorPrimaria() != null,
				empresa.tema(), logo, fundo, capa);
	}

	/** Tema para quem não tem empresa (link geral): a identidade do Diagnóstico. */
	public static PaletaMarca.Tema temaDe(Empresa empresa) {
		return empresa == null ? PaletaMarca.PADRAO : empresa.tema();
	}

}
