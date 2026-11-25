package br.com.diagnosticolider.relatorio;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renderiza o template Thymeleaf do relatório e converte em PDF, com as fontes embutidas
 * ({@link FontesEmbutidas}).
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
		this.fontes = FontesEmbutidas.CSS;
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

	/** Outro relatório (ex.: participação dos convites): o template recebe os dados em "r". */
	public byte[] gerar(String template, Object dados) {
		return renderer.renderizar(processar(template, dados));
	}

	public String html(String template, Object dados) {
		return processar(template, dados);
	}

	private String processar(String template, Object dados) {
		var contexto = new Context(PT_BR);
		contexto.setVariable("r", dados);
		contexto.setVariable("fontes", fontes);
		return templates.process(template, contexto);
	}

}
