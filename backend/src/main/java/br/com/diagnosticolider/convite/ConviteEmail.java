package br.com.diagnosticolider.convite;

import br.com.diagnosticolider.empresa.Empresa;
import br.com.diagnosticolider.empresa.LogomarcaService;
import br.com.diagnosticolider.empresa.PaletaMarca;

/**
 * O que o template do e-mail de convite (email/convite.html) usa. Público porque o Thymeleaf
 * lê os campos por reflexão. lembrete: null no convite; no lembrete antes do prazo, a chamada
 * ("Faltam 3 dias para responder"). continuar: o líder já começou (o botão retoma de onde parou).
 */
public record ConviteEmail(String primeiroNome, String empresa, String sigla, Logo logo, PaletaMarca.Tema tema,
		String link, String prazo, String lembrete, boolean continuar) {

	/** Altura da logo no e-mail e largura máxima (logos largas). */
	private static final int ALTURA_LOGO = 34;

	private static final int LARGURA_MAXIMA_LOGO = 200;

	/** Logo pelo endereço permanente do backend, já no tamanho do e-mail. */
	public record Logo(String url, int largura, int altura, boolean pedeNome) {
	}

	/**
	 * A logo da empresa para qualquer e-mail (convite, lembrete, relatório ao gestor), pelo
	 * endereço permanente do backend: o e-mail é aberto dias depois. Null sem logo.
	 */
	public static Logo logoDe(Empresa empresa, String urlPublica) {
		String caminho = LogomarcaService.urlNoBackend(empresa);
		if (caminho == null || empresa.getLogoLargura() == null || empresa.getLogoAltura() == null) {
			return null;
		}
		double escala = Math.min((double) ALTURA_LOGO / empresa.getLogoAltura(),
				(double) LARGURA_MAXIMA_LOGO / empresa.getLogoLargura());
		int largura = (int) Math.round(empresa.getLogoLargura() * escala);
		int altura = (int) Math.round(empresa.getLogoAltura() * escala);
		return new Logo(urlPublica.replaceAll("/+$", "") + caminho, largura, altura,
				empresa.getLogoLargura() < empresa.getLogoAltura() * 1.8);
	}

}
