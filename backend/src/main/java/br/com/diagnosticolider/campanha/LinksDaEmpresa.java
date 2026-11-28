package br.com.diagnosticolider.campanha;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import br.com.diagnosticolider.config.AppProperties;

/**
 * Os endereços de uma empresa, pelo código do link dela: os líderes respondem em /c/{codigo} e os
 * gestores acompanham em /acompanhar/{codigo}. Assim o e-mail ou o link copiado abre direto a
 * empresa certa, mesmo para quem acompanha mais de uma.
 */
@Component
public class LinksDaEmpresa {

	private final CampanhaRepository campanhas;

	private final String urlPublica;

	LinksDaEmpresa(CampanhaRepository campanhas, AppProperties propriedades) {
		this.campanhas = campanhas;
		this.urlPublica = propriedades.urlPublica().replaceAll("/+$", "");
	}

	/** O código do link principal da empresa (o primeiro criado), ou null se ela não tiver link. */
	public String codigo(Long empresaId) {
		return codigos(List.of(empresaId)).get(empresaId);
	}

	/** O código do link principal de cada empresa (as sem link ficam de fora). */
	public Map<Long, String> codigos(Collection<Long> empresas) {
		Map<Long, String> codigos = new LinkedHashMap<>();
		if (empresas.isEmpty()) {
			return codigos;
		}
		campanhas.findByEmpresaIdInOrderByIdAsc(empresas)
			.forEach(c -> codigos.putIfAbsent(c.getEmpresa().getId(), c.getCodigo()));
		return codigos;
	}

	/** A página do RH da empresa: /acompanhar/{codigo} (sem link, a página geral, que pergunta qual). */
	public String paginaDoRh(Long empresaId) {
		return paginaDoRh(codigo(empresaId));
	}

	public String paginaDoRh(String codigo) {
		return urlPublica + "/acompanhar" + (codigo == null ? "" : "/" + codigo);
	}

}
