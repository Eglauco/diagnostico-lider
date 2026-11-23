package br.com.diagnosticolider.relatorio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Locale;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renderiza o template Thymeleaf do relatório e converte em PDF. As fontes vão embutidas
 * em base64 no HTML: o PDF sai idêntico no Chrome local e no Gotenberg, sem depender de rede.
 */
@Service
public class RelatorioPdfService {

	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	private final ITemplateEngine templates;

	private final PdfRenderer renderer;

	private final String fontes;

	RelatorioPdfService(ITemplateEngine templates, PdfRenderer renderer) {
		this.templates = templates;
		this.renderer = renderer;
		this.fontes = fonte("Bricolage Grotesque", "bricolage-grotesque.woff2", "200 800", "75% 100%")
				+ fonte("Instrument Sans", "instrument-sans.woff2", "400 700", "100%");
	}

	public byte[] gerar(RelatorioDados dados) {
		return renderer.renderizar(html(dados));
	}

	/** HTML do relatório; útil para depurar o layout direto no navegador. */
	public String html(RelatorioDados dados) {
		return processar("relatorio/diagnostico", dados);
	}

	/** Relatório consolidado de uma empresa (sem nomes). */
	public byte[] gerar(RelatorioEmpresaDados dados) {
		return renderer.renderizar(html(dados));
	}

	public String html(RelatorioEmpresaDados dados) {
		return processar("relatorio/empresa", dados);
	}

	private String processar(String template, Object dados) {
		var contexto = new Context(PT_BR);
		contexto.setVariable("r", dados);
		contexto.setVariable("fontes", fontes);
		return templates.process(template, contexto);
	}

	private static String fonte(String familia, String arquivo, String pesos, String larguras) {
		try {
			byte[] bytes = new ClassPathResource("relatorio/fontes/" + arquivo).getContentAsByteArray();
			return """
					@font-face { font-family: '%s'; font-weight: %s; font-stretch: %s; font-style: normal;
					  src: url(data:font/woff2;base64,%s) format('woff2'); }
					""".formatted(familia, pesos, larguras, Base64.getEncoder().encodeToString(bytes));
		}
		catch (IOException e) {
			throw new UncheckedIOException("Fonte do relatório não encontrada: " + arquivo, e);
		}
	}

}
