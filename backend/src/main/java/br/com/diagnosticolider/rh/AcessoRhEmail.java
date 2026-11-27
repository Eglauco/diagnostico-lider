package br.com.diagnosticolider.rh;

import br.com.diagnosticolider.convite.ConviteEmail;
import br.com.diagnosticolider.empresa.PaletaMarca;

/**
 * O que o template email/acesso-rh.html usa. Público porque o Thymeleaf lê os campos por reflexão.
 *
 * @param empresa nome da empresa (null: a pessoa é gestora de mais de uma; vai a marca do Diagnóstico)
 * @param codigo  os 6 dígitos, já separados ("482 913"); null no convite mandado pelo painel
 * @param link    o botão que entra sem digitar
 * @param pagina  o endereço da página do RH, para entrar outro dia
 */
public record AcessoRhEmail(String empresa, String sigla, ConviteEmail.Logo logo, PaletaMarca.Tema tema,
		String codigo, String link, String pagina, String validade) {

	public boolean convite() {
		return codigo == null;
	}

}
