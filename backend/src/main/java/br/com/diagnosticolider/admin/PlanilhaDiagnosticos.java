package br.com.diagnosticolider.admin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import br.com.diagnosticolider.admin.ListaExportada.Registro;
import br.com.diagnosticolider.compartilhado.Datas;

/**
 * A lista do painel em Excel: um resumo com fórmulas no topo (recalcula se a pessoa filtrar ou
 * apagar linhas) e uma linha por diagnóstico, com filtro, datas de verdade e a pontuação de
 * cada dimensão.
 */
@Component
class PlanilhaDiagnosticos {

	static final String CONCLUIDO = "Concluído";

	static final String EM_ANDAMENTO = "Em andamento";

	/** Linha (base 0) do cabeçalho da tabela; os diagnósticos começam na seguinte. */
	static final int CABECALHO = 6;

	private record Coluna(String titulo, int largura) {
	}

	byte[] gerar(String escopo, String titulo, String filtros, String exportadoEm, List<Registro> registros,
			boolean inativos) {
		Set<String> dimensoes = new LinkedHashSet<>();
		registros.forEach(r -> dimensoes.addAll(r.dimensoes().keySet()));

		List<Coluna> colunas = new ArrayList<>(List.of(new Coluna("Nome", 32), new Coluna("E-mail", 32),
				new Coluna("WhatsApp", 17), new Coluna("Empresa", 24), new Coluna("Link", 24), new Coluna("Cargo", 22),
				new Coluna("Tempo no cargo", 18), new Coluna("Situação", 16), new Coluna("Respondidas", 13),
				new Coluna("Índice", 10), new Coluna("Faixa", 13)));
		int primeiraDimensao = colunas.size();
		dimensoes.forEach(d -> colunas.add(new Coluna(d, Math.clamp(d.length() + 4L, 14, 28))));
		colunas.add(new Coluna("Iniciado em", 18));
		colunas.add(new Coluna("Concluído em", 18));
		if (inativos) {
			colunas.add(new Coluna("Inativado em", 18));
		}
		int colSituacao = 7;
		int colIndice = 9;
		int colFaixa = 10;

		try (var pasta = new XSSFWorkbook(); var saida = new ByteArrayOutputStream()) {
			var e = new Estilos(pasta);
			XSSFSheet aba = pasta.createSheet("Diagnósticos");
			for (int c = 0; c < colunas.size(); c++) {
				aba.setColumnWidth(c, colunas.get(c).largura() * 256);
			}
			aba.setDisplayGridlines(false);

			celula(aba.createRow(0), 0, titulo + " · " + escopo, e.titulo);
			aba.getRow(0).setHeightInPoints(24);
			celula(aba.createRow(1), 0, (filtros == null ? "Sem outros filtros." : filtros + ".") + "  " + exportadoEm + ".",
					e.suave);
			celula(aba.createRow(2), 0, "Documento interno do painel: traz o nome e o resultado de cada líder.", e.suave);

			int primeira = CABECALHO + 2;
			int ultima = Math.max(primeira, CABECALHO + 1 + registros.size());
			String nomes = faixa(0, primeira, ultima);
			String situacoes = faixa(colSituacao, primeira, ultima);
			String indices = faixa(colIndice, primeira, ultima);
			String faixas = faixa(colFaixa, primeira, ultima);
			String[][] resumo = {
					{ "Diagnósticos", "COUNTA(" + nomes + ")" },
					{ "Concluídos", "COUNTIF(" + situacoes + ",\"" + CONCLUIDO + "\")" },
					{ "Em andamento", "COUNTIF(" + situacoes + ",\"" + EM_ANDAMENTO + "\")" },
					{ "Índice médio", "IF(COUNT(" + indices + ")=0,\"—\",ROUND(AVERAGE(" + indices + "),0))" },
					{ "Travada", "COUNTIF(" + faixas + ",\"Travada\")" },
					{ "Platô", "COUNTIF(" + faixas + ",\"Platô\")" },
					{ "Ascensão", "COUNTIF(" + faixas + ",\"Ascensão\")" } };
			Row rotulos = aba.createRow(3);
			Row valores = aba.createRow(4);
			valores.setHeightInPoints(30);
			for (int i = 0; i < resumo.length; i++) {
				celula(rotulos, i, resumo[i][0], e.rotuloResumo);
				Cell valor = valores.createCell(i);
				valor.setCellFormula(resumo[i][1]);
				valor.setCellStyle(i >= 4 ? e.numeroFaixa.get(resumo[i][0]) : e.numeroResumo);
			}

			Row cabecalho = aba.createRow(CABECALHO);
			cabecalho.setHeightInPoints(22);
			for (int c = 0; c < colunas.size(); c++) {
				celula(cabecalho, c, colunas.get(c).titulo(), e.cabecalho);
			}

			int r = CABECALHO + 1;
			for (Registro d : registros) {
				Row linha = aba.createRow(r++);
				linha.setHeightInPoints(19);
				celula(linha, 0, d.nome(), e.texto);
				celula(linha, 1, d.email(), e.texto);
				celula(linha, 2, d.whatsapp(), e.texto);
				celula(linha, 3, d.empresa(), e.texto);
				celula(linha, 4, d.link(), e.texto);
				celula(linha, 5, d.cargo(), e.texto);
				celula(linha, 6, d.tempoNoCargo(), e.texto);
				celula(linha, colSituacao, d.concluido() ? CONCLUIDO : EM_ANDAMENTO,
						d.concluido() ? e.concluido : e.andamento);
				celula(linha, 8, d.respondidas() + " de " + d.perguntas(), e.texto);
				numero(linha, colIndice, d.indice(), e.numero);
				celula(linha, colFaixa, d.faixaRotulo(), d.faixa() == null ? e.texto : e.faixa.getOrDefault(d.faixa(), e.texto));
				int c = primeiraDimensao;
				for (String dimensao : dimensoes) {
					numero(linha, c++, d.dimensoes().get(dimensao), e.numero);
				}
				data(linha, c++, d.iniciadoEm(), e);
				data(linha, c++, d.concluidoEm(), e);
				if (inativos) {
					data(linha, c, d.inativadoEm(), e);
				}
			}
			aba.setAutoFilter(new CellRangeAddress(CABECALHO, Math.max(CABECALHO + 1, r - 1), 0, colunas.size() - 1));
			aba.createFreezePane(1, CABECALHO + 1);

			var impressao = aba.getPrintSetup();
			impressao.setLandscape(true);
			impressao.setFitWidth((short) 1);
			impressao.setFitHeight((short) 0);
			aba.setFitToPage(true);
			aba.setRepeatingRows(new CellRangeAddress(CABECALHO, CABECALHO, -1, -1));

			// O resumo já sai calculado (visualizadores de celular não recalculam fórmulas).
			XSSFFormulaEvaluator.evaluateAllFormulaCells(pasta);
			pasta.write(saida);
			return saida.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static String faixa(int coluna, int primeira, int ultima) {
		String letra = CellReference.convertNumToColString(coluna);
		return "$%s$%d:$%s$%d".formatted(letra, primeira, letra, ultima);
	}

	private static Cell celula(Row linha, int coluna, String valor, XSSFCellStyle estilo) {
		Cell celula = linha.createCell(coluna);
		if (valor != null) {
			celula.setCellValue(valor);
		}
		celula.setCellStyle(estilo);
		return celula;
	}

	private static void numero(Row linha, int coluna, Integer valor, XSSFCellStyle estilo) {
		Cell celula = linha.createCell(coluna);
		if (valor != null) {
			celula.setCellValue(valor);
		}
		celula.setCellStyle(estilo);
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

		final Map<String, XSSFCellStyle> numeroFaixa = new HashMap<>();

		final XSSFCellStyle cabecalho;

		final XSSFCellStyle texto;

		final XSSFCellStyle numero;

		final XSSFCellStyle data;

		final XSSFCellStyle concluido;

		final XSSFCellStyle andamento;

		final Map<String, XSSFCellStyle> faixa = new HashMap<>();

		Estilos(XSSFWorkbook pasta) {
			XSSFColor abeto = cor(0x0F, 0x2B, 0x26);
			XSSFColor abeto2 = cor(0x4A, 0x63, 0x5C);
			XSSFColor linha = cor(0xD2, 0xDD, 0xD8);

			titulo = estilo(pasta, fonte(pasta, 16, abeto, true));
			suave = estilo(pasta, fonte(pasta, 10, abeto2, false));
			rotuloResumo = estilo(pasta, fonte(pasta, 10, abeto2, true));
			rotuloResumo.setVerticalAlignment(VerticalAlignment.BOTTOM);
			numeroResumo = estilo(pasta, fonte(pasta, 20, abeto, true));
			numeroResumo.setAlignment(HorizontalAlignment.LEFT);
			// Tintas das faixas com contraste AA (as mesmas dos selos do painel).
			numeroFaixa.put("Travada", numeroColorido(pasta, cor(0xA5, 0x2B, 0x40)));
			numeroFaixa.put("Platô", numeroColorido(pasta, cor(0x85, 0x53, 0x00)));
			numeroFaixa.put("Ascensão", numeroColorido(pasta, cor(0x17, 0x71, 0x4A)));

			cabecalho = estilo(pasta, fonte(pasta, 11, cor(0xFF, 0xFF, 0xFF), true));
			cabecalho.setFillForegroundColor(abeto);
			cabecalho.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			cabecalho.setIndention((short) 1);

			texto = estilo(pasta, fonte(pasta, 11, abeto, false));
			borda(texto, linha);
			numero = estilo(pasta, fonte(pasta, 11, abeto, false));
			numero.setAlignment(HorizontalAlignment.CENTER);
			borda(numero, linha);
			data = estilo(pasta, fonte(pasta, 11, abeto, false));
			data.setDataFormat(pasta.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
			data.setAlignment(HorizontalAlignment.LEFT);
			borda(data, linha);

			concluido = chip(pasta, cor(0xE2, 0xF2, 0xEA), cor(0x17, 0x71, 0x4A), linha);
			andamento = chip(pasta, cor(0xFB, 0xF0, 0xDC), cor(0x85, 0x53, 0x00), linha);
			faixa.put("TRAVADA", chip(pasta, cor(0xF8, 0xE6, 0xE9), cor(0xA5, 0x2B, 0x40), linha));
			faixa.put("PLATO", chip(pasta, cor(0xFB, 0xF0, 0xDC), cor(0x85, 0x53, 0x00), linha));
			faixa.put("ASCENSAO", chip(pasta, cor(0xE2, 0xF2, 0xEA), cor(0x17, 0x71, 0x4A), linha));
		}

		private static XSSFCellStyle numeroColorido(XSSFWorkbook pasta, XSSFColor tinta) {
			XSSFCellStyle estilo = estilo(pasta, fonte(pasta, 20, tinta, true));
			estilo.setAlignment(HorizontalAlignment.LEFT);
			return estilo;
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
