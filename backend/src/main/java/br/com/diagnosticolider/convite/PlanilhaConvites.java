package br.com.diagnosticolider.convite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import br.com.diagnosticolider.compartilhado.RegraNegocioException;

/**
 * A planilha dos convites: gera o modelo que a empresa preenche (com os cargos dela numa lista
 * suspensa) e lê o arquivo preenchido, em .xlsx (o modelo) ou .csv. As colunas são achadas pelo
 * cabeçalho, então a ordem pode mudar e a linha de título pode ficar.
 */
@Component
class PlanilhaConvites {

	static final int MAXIMO_LINHAS = 2000;

	static final String OUTRO_CARGO = "Outro cargo";

	/** Linhas preparadas para preencher no modelo. */
	private static final int LINHAS_MODELO = 500;

	private static final String EXEMPLO_NOME = "Ana Paula Souza (exemplo)";

	private static final String EXEMPLO_EMAIL = "ana.souza@empresa.com.br";

	enum Coluna {
		NOME, EMAIL, WHATSAPP, CARGO
	}

	/** Uma linha lida, como estava no arquivo (o número é o da linha no Excel). */
	record Linha(int numero, String nome, String email, String whatsapp, String cargo) {

		/** A linha de exemplo do modelo, que a empresa pode ter esquecido de apagar. */
		boolean exemplo() {
			return nome.contains("(exemplo)") || EXEMPLO_EMAIL.equalsIgnoreCase(email);
		}

	}

	// ------------------------------------------------------------------ leitura

	List<Linha> ler(String nomeArquivo, byte[] conteudo) {
		if (conteudo == null || conteudo.length == 0) {
			throw new RegraNegocioException("O arquivo está vazio. Use a planilha modelo preenchida.");
		}
		String nome = nomeArquivo == null ? "" : nomeArquivo.toLowerCase(Locale.ROOT);
		List<List<String>> tabela = nome.endsWith(".csv") || nome.endsWith(".txt") ? csv(conteudo) : excel(conteudo);
		return linhas(tabela);
	}

	private static List<List<String>> excel(byte[] conteudo) {
		try (Workbook pasta = WorkbookFactory.create(new ByteArrayInputStream(conteudo))) {
			Sheet aba = pasta.getSheet("Líderes") != null ? pasta.getSheet("Líderes") : pasta.getSheetAt(0);
			var formatador = new DataFormatter(Locale.forLanguageTag("pt-BR"));
			List<List<String>> tabela = new ArrayList<>();
			for (int r = 0; r <= aba.getLastRowNum(); r++) {
				Row linha = aba.getRow(r);
				List<String> celulas = new ArrayList<>();
				if (linha != null) {
					for (int c = 0; c < Math.max(0, linha.getLastCellNum()); c++) {
						celulas.add(texto(linha.getCell(c), formatador));
					}
				}
				tabela.add(celulas);
				if (tabela.size() > MAXIMO_LINHAS + 50) {
					break;
				}
			}
			return tabela;
		}
		catch (IOException | RuntimeException e) {
			if (e instanceof RegraNegocioException regra) {
				throw regra;
			}
			throw new RegraNegocioException(
					"Não conseguimos ler o arquivo. Envie a planilha modelo preenchida (.xlsx) ou um .csv.");
		}
	}

	/** Telefone digitado como número no Excel vira 11987654321, não 1,19877E+10. */
	private static String texto(Cell celula, DataFormatter formatador) {
		if (celula == null) {
			return "";
		}
		if (celula.getCellType() == CellType.NUMERIC) {
			return BigDecimal.valueOf(celula.getNumericCellValue()).stripTrailingZeros().toPlainString();
		}
		return formatador.formatCellValue(celula).strip();
	}

	/** CSV do Excel brasileiro: ponto e vírgula e Windows-1252; dos outros: vírgula e UTF-8. */
	private static List<List<String>> csv(byte[] conteudo) {
		String texto = decodificar(conteudo);
		if (texto.startsWith("﻿")) {
			texto = texto.substring(1);
		}
		String primeira = texto.lines().findFirst().orElse("");
		char separador = contar(primeira, ';') >= contar(primeira, ',') ? ';' : ',';
		if (contar(primeira, '\t') > contar(primeira, separador)) {
			separador = '\t';
		}
		List<List<String>> tabela = new ArrayList<>();
		List<String> linha = new ArrayList<>();
		var celula = new StringBuilder();
		boolean entreAspas = false;
		for (int i = 0; i < texto.length(); i++) {
			char ch = texto.charAt(i);
			if (entreAspas) {
				if (ch == '"' && i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
					celula.append('"');
					i++;
				}
				else if (ch == '"') {
					entreAspas = false;
				}
				else {
					celula.append(ch);
				}
			}
			else if (ch == '"') {
				entreAspas = true;
			}
			else if (ch == separador) {
				linha.add(celula.toString().strip());
				celula.setLength(0);
			}
			else if (ch == '\n' || ch == '\r') {
				if (ch == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
					i++;
				}
				linha.add(celula.toString().strip());
				celula.setLength(0);
				tabela.add(linha);
				linha = new ArrayList<>();
				if (tabela.size() > MAXIMO_LINHAS + 50) {
					return tabela;
				}
			}
			else {
				celula.append(ch);
			}
		}
		if (!celula.isEmpty() || !linha.isEmpty()) {
			linha.add(celula.toString().strip());
			tabela.add(linha);
		}
		return tabela;
	}

	private static String decodificar(byte[] conteudo) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(conteudo))
				.toString();
		}
		catch (CharacterCodingException e) {
			return new String(conteudo, Charset.forName("windows-1252"));
		}
	}

	private static int contar(String texto, char ch) {
		return (int) texto.chars().filter(c -> c == ch).count();
	}

	private static List<Linha> linhas(List<List<String>> tabela) {
		int indiceCabecalho = -1;
		Map<Coluna, Integer> colunas = new EnumMap<>(Coluna.class);
		for (int r = 0; r < Math.min(tabela.size(), 20) && indiceCabecalho < 0; r++) {
			Map<Coluna, Integer> achadas = new EnumMap<>(Coluna.class);
			List<String> celulas = tabela.get(r);
			for (int c = 0; c < celulas.size(); c++) {
				Coluna coluna = coluna(celulas.get(c));
				if (coluna != null) {
					achadas.putIfAbsent(coluna, c);
				}
			}
			if (achadas.containsKey(Coluna.NOME) && achadas.containsKey(Coluna.EMAIL)) {
				indiceCabecalho = r;
				colunas = achadas;
			}
		}
		if (indiceCabecalho < 0) {
			throw new RegraNegocioException(
					"Não achamos as colunas \"Nome completo\" e \"E-mail\". Use a planilha modelo e não mude os nomes das colunas.");
		}

		List<Linha> linhas = new ArrayList<>();
		for (int r = indiceCabecalho + 1; r < tabela.size(); r++) {
			List<String> celulas = tabela.get(r);
			String nome = valor(celulas, colunas.get(Coluna.NOME));
			String email = valor(celulas, colunas.get(Coluna.EMAIL));
			String whatsapp = valor(celulas, colunas.get(Coluna.WHATSAPP));
			String cargo = valor(celulas, colunas.get(Coluna.CARGO));
			if ((nome + email + whatsapp + cargo).isBlank()) {
				continue;
			}
			linhas.add(new Linha(r + 1, nome, email, whatsapp, cargo));
		}
		if (linhas.isEmpty()) {
			throw new RegraNegocioException("A planilha não tem nenhum líder. Preencha a partir da linha 3 e envie de novo.");
		}
		if (linhas.size() > MAXIMO_LINHAS) {
			throw new RegraNegocioException("Use até %d líderes por planilha.".formatted(MAXIMO_LINHAS));
		}
		return linhas;
	}

	private static String valor(List<String> celulas, Integer indice) {
		return indice == null || indice >= celulas.size() ? "" : celulas.get(indice).strip().replaceAll("\\s+", " ");
	}

	/** O cabeçalho do modelo, mas também variações comuns ("Telefone", "Função"…). Outras colunas são ignoradas. */
	static Coluna coluna(String cabecalho) {
		String t = normalizar(cabecalho);
		if (t.startsWith("nome")) {
			return Coluna.NOME;
		}
		if (t.contains("mail")) {
			return Coluna.EMAIL;
		}
		if (t.contains("whats") || t.contains("telefone") || t.contains("celular")) {
			return Coluna.WHATSAPP;
		}
		if (t.startsWith("cargo") || t.equals("funcao")) {
			return Coluna.CARGO;
		}
		return null;
	}

	/** Minúsculas, sem acento, sem "*" e sem o que está entre parênteses. */
	static String normalizar(String texto) {
		String semAcento = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return semAcento.toLowerCase(Locale.ROOT).replaceAll("\\(.*?\\)", "").replace("*", "").replaceAll("\\s+", " ").strip();
	}

	// ------------------------------------------------------------------ modelo

	byte[] modelo(String empresa, List<String> cargos) {
		List<String> lista = new ArrayList<>(cargos);
		lista.add(OUTRO_CARGO);
		try (var pasta = new XSSFWorkbook(); var saida = new ByteArrayOutputStream()) {
			var estilos = new Estilos(pasta);
			abaLideres(pasta, estilos, lista.size());
			abaComoPreencher(pasta, estilos, empresa);
			abaCargos(pasta, estilos, lista);
			pasta.setActiveSheet(0);
			pasta.write(saida);
			return saida.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static final String[][] COLUNAS_MODELO = {
			{ "Nome completo *", "32", "Como deve aparecer no convite e no relatório do líder. Obrigatório." },
			{ "E-mail *", "34", "E-mail de trabalho: é por onde chegam o convite (com um link só dele) e os lembretes. Obrigatório e sem repetir." },
			{ "WhatsApp (com DDD)", "21", "Ex.: (11) 98765-4321. Opcional: já vem preenchido para o líder só confirmar." },
			{ "Cargo *", "22", "Escolha na lista (são os cargos cadastrados para a sua empresa). Obrigatório." } };

	private static void abaLideres(XSSFWorkbook pasta, Estilos e, int totalCargos) {
		XSSFSheet aba = pasta.createSheet("Líderes");
		XSSFDrawing desenho = aba.createDrawingPatriarch();
		var fabrica = pasta.getCreationHelper();
		Row cabecalho = aba.createRow(0);
		cabecalho.setHeightInPoints(30);
		for (int c = 0; c < COLUNAS_MODELO.length; c++) {
			aba.setColumnWidth(c, Integer.parseInt(COLUNAS_MODELO[c][1]) * 256);
			Cell celula = cabecalho.createCell(c);
			celula.setCellValue(COLUNAS_MODELO[c][0]);
			celula.setCellStyle(e.cabecalho);
			ClientAnchor ancora = fabrica.createClientAnchor();
			ancora.setCol1(c);
			ancora.setCol2(c + 3);
			ancora.setRow1(0);
			ancora.setRow2(4);
			var nota = desenho.createCellComment(ancora);
			nota.setString(fabrica.createRichTextString(COLUNAS_MODELO[c][2]));
			celula.setCellComment(nota);
		}
		aba.setColumnWidth(4, 3 * 256);
		aba.setColumnWidth(5, 50 * 256);

		Row exemplo = aba.createRow(1);
		exemplo.setHeightInPoints(22);
		String[] valores = { EXEMPLO_NOME, EXEMPLO_EMAIL, "(11) 98765-4321", "Gerente" };
		for (int c = 0; c < valores.length; c++) {
			Cell celula = exemplo.createCell(c);
			celula.setCellValue(valores[c]);
			celula.setCellStyle(e.exemplo);
		}

		for (int r = 2; r < 2 + LINHAS_MODELO; r++) {
			Row linha = aba.createRow(r);
			linha.setHeightInPoints(22);
			for (int c = 0; c < COLUNAS_MODELO.length; c++) {
				linha.createCell(c).setCellStyle(c == 2 ? e.preencherTexto : e.preencher);
			}
		}

		DataValidationHelper ajuda = aba.getDataValidationHelper();
		var cargo = ajuda.createValidation(ajuda.createFormulaListConstraint("Cargos!$A$2:$A$" + (totalCargos + 1)),
				new CellRangeAddressList(2, 1 + LINHAS_MODELO, 3, 3));
		cargo.setErrorStyle(DataValidation.ErrorStyle.STOP);
		cargo.setShowErrorBox(true);
		cargo.createErrorBox("Cargo fora da lista", "Escolha um cargo da lista. Se nenhum servir, use \"Outro cargo\".");
		cargo.setShowPromptBox(true);
		cargo.createPromptBox("Cargo", "Escolha na lista.");
		aba.addValidationData(cargo);

		var email = ajuda.createValidation(
				ajuda.createCustomConstraint("OR(B3=\"\",AND(ISNUMBER(SEARCH(\"@\",B3)),ISNUMBER(SEARCH(\".\",B3))))"),
				new CellRangeAddressList(2, 1 + LINHAS_MODELO, 1, 1));
		email.setErrorStyle(DataValidation.ErrorStyle.WARNING);
		email.setShowErrorBox(true);
		email.createErrorBox("Confira o e-mail", "Parece faltar o @ ou o domínio (ex.: nome@empresa.com.br).");
		aba.addValidationData(email);

		Object[][] legenda = { { "Como preencher", e.cabecalho }, { "Amarelo: preencha aqui, uma linha por líder.", e.preencher },
				{ "Cinza: linha de exemplo (pode apagar).", e.exemplo }, { "* Campo obrigatório.", e.texto },
				{ "Não mude os nomes das colunas.", e.texto }, { "Instruções completas na aba \"Como preencher\".", e.texto } };
		for (int i = 0; i < legenda.length; i++) {
			Row linha = aba.getRow(i) != null ? aba.getRow(i) : aba.createRow(i);
			Cell celula = linha.createCell(5);
			celula.setCellValue((String) legenda[i][0]);
			celula.setCellStyle((XSSFCellStyle) legenda[i][1]);
		}
		aba.createFreezePane(0, 1);
	}

	private static void abaComoPreencher(XSSFWorkbook pasta, Estilos e, String empresa) {
		XSSFSheet aba = pasta.createSheet("Como preencher");
		aba.setColumnWidth(0, 5 * 256);
		aba.setColumnWidth(1, 100 * 256);
		Cell titulo = aba.createRow(0).createCell(1);
		titulo.setCellValue("Convite para o Diagnóstico de Carreira do Líder · " + empresa);
		titulo.setCellStyle(e.titulo);
		aba.getRow(0).setHeightInPoints(28);
		Cell subtitulo = aba.createRow(1).createCell(1);
		subtitulo.setCellValue(
				"Cada líder recebe por e-mail um link só dele, já com os dados abaixo: ele só confirma e responde. Leva 2 minutos.");
		subtitulo.setCellStyle(e.suave);

		String[] passos = {
				"Na aba \"Líderes\", preencha uma linha por líder a partir da linha 3 (a linha 2 é um exemplo).",
				"Nome completo: como deve aparecer no convite e no relatório do líder.",
				"E-mail: o de trabalho. É por onde o líder recebe o convite, com um link só dele, e os lembretes. Cada e-mail aparece uma vez só.",
				"WhatsApp (opcional): com DDD, ex.: (11) 98765-4321. Já vem preenchido para o líder só confirmar.",
				"Cargo: escolha na lista. Se nenhum servir, use \"Outro cargo\" (o líder escreve o cargo dele ao confirmar os dados).",
				"Não mude os nomes nem a ordem das colunas. Pode deixar linhas em branco no fim.",
				"Antes de enviar os convites, tudo é conferido numa tela de revisão, e qualquer linha pode ser corrigida ali mesmo." };
		for (int i = 0; i < passos.length; i++) {
			Row linha = aba.createRow(3 + i);
			linha.setHeightInPoints(30);
			Cell numero = linha.createCell(0);
			numero.setCellValue(i + 1);
			numero.setCellStyle(e.numero);
			Cell texto = linha.createCell(1);
			texto.setCellValue(passos[i]);
			texto.setCellStyle(e.paragrafo);
		}
		int r = 3 + passos.length + 1;
		Cell privacidade = aba.createRow(r).createCell(1);
		privacidade.setCellValue("Privacidade");
		privacidade.setCellStyle(e.negrito);
		Row explicacao = aba.createRow(r + 1);
		explicacao.setHeightInPoints(32);
		Cell texto = explicacao.createCell(1);
		texto.setCellValue(
				"A empresa acompanha quem já respondeu. As respostas e o resultado individual de cada líder não são compartilhados com a empresa: ela recebe só números do grupo.");
		texto.setCellStyle(e.paragrafo);
	}

	private static void abaCargos(XSSFWorkbook pasta, Estilos e, List<String> cargos) {
		XSSFSheet aba = pasta.createSheet("Cargos");
		aba.setColumnWidth(0, 28 * 256);
		aba.setColumnWidth(2, 70 * 256);
		Row cabecalho = aba.createRow(0);
		Cell titulo = cabecalho.createCell(0);
		titulo.setCellValue("Cargos da empresa");
		titulo.setCellStyle(e.cabecalho);
		Cell nota = cabecalho.createCell(2);
		nota.setCellValue("Escolha o cargo de cada líder na aba \"Líderes\": a lista suspensa usa estes nomes.");
		nota.setCellStyle(e.suave);
		for (int i = 0; i < cargos.size(); i++) {
			Cell celula = aba.createRow(i + 1).createCell(0);
			celula.setCellValue(cargos.get(i));
			celula.setCellStyle(e.borda);
		}
	}

	/** Estilos da identidade: abeto no cabeçalho, amarelo onde se preenche, cinza no exemplo. */
	private static final class Estilos {

		final XSSFCellStyle cabecalho;

		final XSSFCellStyle exemplo;

		final XSSFCellStyle preencher;

		final XSSFCellStyle preencherTexto;

		final XSSFCellStyle texto;

		final XSSFCellStyle borda;

		final XSSFCellStyle titulo;

		final XSSFCellStyle suave;

		final XSSFCellStyle numero;

		final XSSFCellStyle paragrafo;

		final XSSFCellStyle negrito;

		Estilos(XSSFWorkbook pasta) {
			XSSFColor abeto = cor(0x0F, 0x2B, 0x26);
			XSSFColor nevoa = cor(0xF5, 0xF8, 0xF6);
			XSSFColor linha = cor(0xD2, 0xDD, 0xD8);
			XSSFColor amarelo = cor(0xFF, 0xF6, 0xD5);

			cabecalho = estilo(pasta, fonte(pasta, false, 11, cor(0xFF, 0xFF, 0xFF), true));
			cabecalho.setFillForegroundColor(abeto);
			cabecalho.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			cabecalho.setIndention((short) 1);

			exemplo = estilo(pasta, fonte(pasta, true, 11, cor(0x7D, 0x91, 0x8B), false));
			exemplo.setFillForegroundColor(nevoa);
			exemplo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			bordas(exemplo, linha);

			preencher = estilo(pasta, fonte(pasta, false, 11, abeto, false));
			preencher.setFillForegroundColor(amarelo);
			preencher.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			bordas(preencher, linha);

			preencherTexto = estilo(pasta, fonte(pasta, false, 11, abeto, false));
			preencherTexto.cloneStyleFrom(preencher);
			preencherTexto.setDataFormat(pasta.createDataFormat().getFormat("@"));

			texto = estilo(pasta, fonte(pasta, false, 11, abeto, false));
			borda = estilo(pasta, fonte(pasta, false, 11, abeto, false));
			bordas(borda, linha);
			titulo = estilo(pasta, fonte(pasta, false, 16, abeto, true));
			suave = estilo(pasta, fonte(pasta, true, 11, cor(0x4A, 0x63, 0x5C), false));
			suave.setWrapText(true);
			numero = estilo(pasta, fonte(pasta, false, 11, cor(0xC2, 0x3A, 0x1C), true));
			numero.setAlignment(HorizontalAlignment.CENTER);
			numero.setVerticalAlignment(VerticalAlignment.TOP);
			paragrafo = estilo(pasta, fonte(pasta, false, 11, abeto, false));
			paragrafo.setWrapText(true);
			paragrafo.setVerticalAlignment(VerticalAlignment.TOP);
			negrito = estilo(pasta, fonte(pasta, false, 11, abeto, true));
		}

		private static XSSFColor cor(int r, int g, int b) {
			return new XSSFColor(new byte[] { (byte) r, (byte) g, (byte) b }, null);
		}

		private static XSSFFont fonte(XSSFWorkbook pasta, boolean italico, int tamanho,
				XSSFColor cor, boolean negrito) {
			XSSFFont fonte = pasta.createFont();
			fonte.setFontName("Arial");
			fonte.setFontHeightInPoints((short) tamanho);
			fonte.setItalic(italico);
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

		private static void bordas(XSSFCellStyle estilo, XSSFColor cor) {
			estilo.setBorderTop(BorderStyle.THIN);
			estilo.setBorderBottom(BorderStyle.THIN);
			estilo.setBorderLeft(BorderStyle.THIN);
			estilo.setBorderRight(BorderStyle.THIN);
			estilo.setTopBorderColor(cor);
			estilo.setBottomBorderColor(cor);
			estilo.setLeftBorderColor(cor);
			estilo.setRightBorderColor(cor);
		}

	}

}
