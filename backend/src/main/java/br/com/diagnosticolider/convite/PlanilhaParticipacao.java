package br.com.diagnosticolider.convite;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.FontUnderline;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFHyperlink;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import br.com.diagnosticolider.compartilhado.Datas;
import br.com.diagnosticolider.convite.RelatorioParticipacao.Lider;

/**
 * A participação em Excel: um resumo com fórmulas no topo (recalcula se a empresa filtrar ou
 * apagar linhas) e a lista com filtro, uma linha por líder, com o link pessoal de quem falta.
 */
@Component
class PlanilhaParticipacao {

	private static final String[] COLUNAS = { "Nome", "Cargo", "E-mail", "WhatsApp", "Situação", "Convite enviado em",
			"Último acesso", "Respondeu em", "Link pessoal" };

	private static final int[] LARGURAS = { 34, 22, 34, 18, 24, 20, 20, 20, 56 };

	/** Linha (base 0) do cabeçalho da tabela; os líderes começam na seguinte. */
	private static final int CABECALHO = 6;

	private static final int COLUNA_SITUACAO = 4;

	/** Uma linha da aba "Auditoria": um evento da linha do tempo de um líder. */
	record LinhaAuditoria(String lider, String email, Instant quando, String evento, String detalhe, String por,
			String entrega) {
	}

	private static final String[] COLUNAS_AUDITORIA = { "Líder", "E-mail do líder", "Quando", "O que aconteceu",
			"Detalhe", "Por", "Entrega do e-mail" };

	private static final int[] LARGURAS_AUDITORIA = { 30, 32, 18, 30, 70, 22, 26 };

	byte[] gerar(String empresa, String atualizadoEm, String prazo, List<Lider> lideres, int inativos) {
		return gerar(empresa, atualizadoEm, prazo, lideres, inativos, List.of());
	}

	byte[] gerar(String empresa, String atualizadoEm, String prazo, List<Lider> lideres, int inativos,
			List<LinhaAuditoria> auditoria) {
		try (var pasta = new XSSFWorkbook(); var saida = new ByteArrayOutputStream()) {
			var e = new Estilos(pasta);
			XSSFSheet aba = pasta.createSheet("Participação");
			for (int c = 0; c < LARGURAS.length; c++) {
				aba.setColumnWidth(c, LARGURAS[c] * 256);
			}
			aba.setDisplayGridlines(false);

			celula(aba.createRow(0), 0, "Participação no Diagnóstico do Líder · " + empresa, e.titulo);
			aba.getRow(0).setHeightInPoints(24);
			String sobre = atualizadoEm + (prazo == null ? "" : ".  " + prazo)
					+ (inativos == 0 ? "" : "  " + inativos + (inativos == 1 ? " inativo fica" : " inativos ficam") + " fora da lista.");
			celula(aba.createRow(1), 0, sobre, e.suave);
			celula(aba.createRow(2), 0,
					"Só a participação: as respostas e o resultado de cada líder não são compartilhados com a empresa.",
					e.suave);

			int primeira = CABECALHO + 2;
			int ultima = Math.max(primeira, CABECALHO + 1 + lideres.size());
			String situacoes = "$E$%d:$E$%d".formatted(primeira, ultima);
			String nomes = "$A$%d:$A$%d".formatted(primeira, ultima);
			Row rotulos = aba.createRow(3);
			Row valores = aba.createRow(4);
			valores.setHeightInPoints(30);
			String[][] resumo = {
					{ "Líderes", "COUNTA(" + nomes + ")" },
					{ "Responderam", "COUNTIF(" + situacoes + ",\"" + SituacaoParticipacao.RESPONDEU.rotulo() + "\")" },
					{ "Abriram e não terminaram", "COUNTIF(" + situacoes + ",\"" + SituacaoParticipacao.COMECOU.rotulo()
							+ "\")+COUNTIF(" + situacoes + ",\"" + SituacaoParticipacao.ABRIU.rotulo() + "\")" },
					{ "Não abriram o link", "COUNTIF(" + situacoes + ",\"" + SituacaoParticipacao.NAO_ABRIU.rotulo() + "\")" },
					{ "Já responderam", "IF(A5=0,0,B5/A5)" } };
			for (int i = 0; i < resumo.length; i++) {
				celula(rotulos, i, resumo[i][0], e.rotuloResumo);
				Cell valor = valores.createCell(i);
				valor.setCellFormula(resumo[i][1]);
				valor.setCellStyle(i == resumo.length - 1 ? e.percentual : e.numeroResumo);
			}

			Row cabecalho = aba.createRow(CABECALHO);
			cabecalho.setHeightInPoints(22);
			for (int c = 0; c < COLUNAS.length; c++) {
				celula(cabecalho, c, COLUNAS[c], e.cabecalho);
			}
			int r = CABECALHO + 1;
			for (Lider l : lideres) {
				Row linha = aba.createRow(r++);
				linha.setHeightInPoints(19);
				celula(linha, 0, l.nome(), e.texto);
				celula(linha, 1, l.cargo(), e.texto);
				celula(linha, 2, l.email(), e.texto);
				celula(linha, 3, l.whatsapp(), e.texto);
				celula(linha, COLUNA_SITUACAO, l.situacao().rotulo(), e.situacao.get(l.situacao()));
				data(linha, 5, l.enviadoEm(), e);
				data(linha, 6, l.ultimoAcessoEm(), e);
				data(linha, 7, l.respondeuEm(), e);
				Cell link = celula(linha, 8, l.link(), l.link() == null ? e.texto : e.link);
				if (l.link() != null) {
					XSSFHyperlink destino = pasta.getCreationHelper().createHyperlink(HyperlinkType.URL);
					destino.setAddress(l.link());
					link.setHyperlink(destino);
				}
			}
			aba.setAutoFilter(new CellRangeAddress(CABECALHO, Math.max(CABECALHO + 1, r - 1), 0, COLUNAS.length - 1));
			aba.createFreezePane(1, CABECALHO + 1);

			var impressao = aba.getPrintSetup();
			impressao.setLandscape(true);
			impressao.setFitWidth((short) 1);
			impressao.setFitHeight((short) 0);
			aba.setFitToPage(true);
			aba.setRepeatingRows(new CellRangeAddress(CABECALHO, CABECALHO, -1, -1));

			if (!auditoria.isEmpty()) {
				auditoria(pasta, e, empresa, auditoria);
			}

			// O resumo já sai calculado (visualizadores de celular não recalculam fórmulas).
			XSSFFormulaEvaluator.evaluateAllFormulaCells(pasta);
			pasta.write(saida);
			return saida.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * A aba "Auditoria": tudo o que aconteceu com cada líder (convite, e-mails e a entrega deles,
	 * acessos, passos no diagnóstico, ações no painel e quem fez), com filtro para achar qualquer coisa.
	 */
	private static void auditoria(XSSFWorkbook pasta, Estilos e, String empresa, List<LinhaAuditoria> linhas) {
		XSSFSheet aba = pasta.createSheet("Auditoria");
		for (int c = 0; c < LARGURAS_AUDITORIA.length; c++) {
			aba.setColumnWidth(c, LARGURAS_AUDITORIA[c] * 256);
		}
		aba.setDisplayGridlines(false);
		celula(aba.createRow(0), 0, "Auditoria dos convites · " + empresa, e.titulo);
		aba.getRow(0).setHeightInPoints(24);
		celula(aba.createRow(1), 0, "Cada linha é um acontecimento na linha do tempo de um líder. Use o filtro do "
				+ "cabeçalho para ver um líder ou um tipo de evento.", e.suave);
		Row cabecalho = aba.createRow(3);
		cabecalho.setHeightInPoints(22);
		for (int c = 0; c < COLUNAS_AUDITORIA.length; c++) {
			celula(cabecalho, c, COLUNAS_AUDITORIA[c], e.cabecalho);
		}
		int r = 4;
		for (LinhaAuditoria l : linhas) {
			Row linha = aba.createRow(r++);
			celula(linha, 0, l.lider(), e.texto);
			celula(linha, 1, l.email(), e.texto);
			data(linha, 2, l.quando(), e);
			celula(linha, 3, l.evento(), e.texto);
			celula(linha, 4, l.detalhe(), e.texto);
			celula(linha, 5, l.por(), e.texto);
			celula(linha, 6, l.entrega(), e.texto);
		}
		aba.setAutoFilter(new CellRangeAddress(3, Math.max(4, r - 1), 0, COLUNAS_AUDITORIA.length - 1));
		aba.createFreezePane(1, 4);
		var impressao = aba.getPrintSetup();
		impressao.setLandscape(true);
		impressao.setFitWidth((short) 1);
		impressao.setFitHeight((short) 0);
		aba.setFitToPage(true);
		aba.setRepeatingRows(new CellRangeAddress(3, 3, -1, -1));
	}

	private static Cell celula(Row linha, int coluna, String valor, XSSFCellStyle estilo) {
		Cell celula = linha.createCell(coluna);
		if (valor != null) {
			celula.setCellValue(valor);
		}
		celula.setCellStyle(estilo);
		return celula;
	}

	/** Data de verdade no horário de Brasília (dá para ordenar e filtrar no Excel). */
	private static void data(Row linha, int coluna, Instant quando, Estilos e) {
		Cell celula = linha.createCell(coluna);
		if (quando != null) {
			celula.setCellValue(quando.atZone(Datas.FUSO).toLocalDateTime());
		}
		celula.setCellStyle(e.data);
	}

	private static final class Estilos {

		final XSSFCellStyle titulo;

		final XSSFCellStyle suave;

		final XSSFCellStyle rotuloResumo;

		final XSSFCellStyle numeroResumo;

		final XSSFCellStyle percentual;

		final XSSFCellStyle cabecalho;

		final XSSFCellStyle texto;

		final XSSFCellStyle data;

		final XSSFCellStyle link;

		final Map<SituacaoParticipacao, XSSFCellStyle> situacao = new EnumMap<>(SituacaoParticipacao.class);

		Estilos(XSSFWorkbook pasta) {
			XSSFColor abeto = cor(0x0F, 0x2B, 0x26);
			XSSFColor abeto2 = cor(0x4A, 0x63, 0x5C);
			XSSFColor linha = cor(0xD2, 0xDD, 0xD8);
			XSSFColor nevoa = cor(0xF5, 0xF8, 0xF6);

			titulo = estilo(pasta, fonte(pasta, 16, abeto, true));
			suave = estilo(pasta, fonte(pasta, 10, abeto2, false));
			rotuloResumo = estilo(pasta, fonte(pasta, 10, abeto2, true));
			rotuloResumo.setVerticalAlignment(VerticalAlignment.BOTTOM);
			numeroResumo = estilo(pasta, fonte(pasta, 20, abeto, true));
			numeroResumo.setAlignment(HorizontalAlignment.LEFT);
			percentual = estilo(pasta, fonte(pasta, 20, cor(0x1F, 0x8A, 0x5B), true));
			percentual.setAlignment(HorizontalAlignment.LEFT);
			percentual.setDataFormat(pasta.createDataFormat().getFormat("0%"));

			cabecalho = estilo(pasta, fonte(pasta, 11, cor(0xFF, 0xFF, 0xFF), true));
			cabecalho.setFillForegroundColor(abeto);
			cabecalho.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			cabecalho.setIndention((short) 1);

			texto = estilo(pasta, fonte(pasta, 11, abeto, false));
			borda(texto, linha);
			data = estilo(pasta, fonte(pasta, 11, abeto, false));
			data.setDataFormat(pasta.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
			data.setAlignment(HorizontalAlignment.LEFT);
			borda(data, linha);
			XSSFFont sublinhada = fonte(pasta, 11, cor(0x1D, 0x4A, 0x42), false);
			sublinhada.setUnderline(FontUnderline.SINGLE);
			link = estilo(pasta, sublinhada);
			borda(link, linha);

			situacao.put(SituacaoParticipacao.RESPONDEU, chip(pasta, cor(0xE2, 0xF2, 0xEA), cor(0x17, 0x71, 0x4A), linha));
			situacao.put(SituacaoParticipacao.COMECOU, chip(pasta, cor(0xFB, 0xF0, 0xDC), cor(0x8A, 0x57, 0x00), linha));
			situacao.put(SituacaoParticipacao.ABRIU, chip(pasta, cor(0xFB, 0xF0, 0xDC), cor(0x8A, 0x57, 0x00), linha));
			situacao.put(SituacaoParticipacao.NAO_ABRIU, chip(pasta, nevoa, abeto2, linha));
		}

		private static XSSFCellStyle chip(XSSFWorkbook pasta, XSSFColor fundo, XSSFColor tinta, XSSFColor linha) {
			XSSFCellStyle estilo = estilo(pasta, fonte(pasta, 11, tinta, true));
			estilo.setFillForegroundColor(fundo);
			estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			estilo.setIndention((short) 1);
			borda(estilo, linha);
			return estilo;
		}

		private static XSSFColor cor(int r, int g, int b) {
			return new XSSFColor(new byte[] { (byte) r, (byte) g, (byte) b }, null);
		}

		private static XSSFFont fonte(XSSFWorkbook pasta, int tamanho, XSSFColor cor, boolean negrito) {
			XSSFFont fonte = pasta.createFont();
			fonte.setFontName("Arial");
			fonte.setFontHeightInPoints((short) tamanho);
			fonte.setBold(negrito);
			fonte.setColor(cor);
			return fonte;
		}

		private static XSSFCellStyle estilo(XSSFWorkbook pasta, XSSFFont fonte) {
			XSSFCellStyle estilo = pasta.createCellStyle();
			estilo.setFont(fonte);
			estilo.setVerticalAlignment(VerticalAlignment.CENTER);
			return estilo;
		}

		private static void borda(XSSFCellStyle estilo, XSSFColor cor) {
			estilo.setBorderBottom(BorderStyle.THIN);
			estilo.setBottomBorderColor(cor);
		}

	}

}
