package br.com.diagnosticolider.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import br.com.diagnosticolider.admin.ExportacaoDiagnosticos.Filtros;
import br.com.diagnosticolider.admin.ListaExportada.Linha;
import br.com.diagnosticolider.admin.ListaExportada.Pagina;
import br.com.diagnosticolider.admin.ListaExportada.Registro;
import br.com.diagnosticolider.diagnostico.StatusDiagnostico;

/** A exportação leva a lista inteira com os filtros da tela, não só a página aberta. */
class ExportacaoDiagnosticosTest {

	private static final Linha LINHA = new Linha("Ana", null, "ana@x.com", true, "PLATO", "Platô", 58, null, "Concluiu 03/11/2026");

	@Test
	void paginaComAberturaNaPrimeiraEOResto18PorPagina() {
		List<Pagina> comCurva = ExportacaoDiagnosticos.paginar(Collections.nCopies(40, LINHA), true);
		assertThat(comCurva).extracting(p -> p.linhas().size()).containsExactly(7, 18, 15);
		assertThat(comCurva.getFirst().primeira()).isTrue();
		assertThat(comCurva.get(1).primeira()).isFalse();

		List<Pagina> semCurva = ExportacaoDiagnosticos.paginar(Collections.nCopies(14, LINHA), false);
		assertThat(semCurva).extracting(p -> p.linhas().size()).containsExactly(13, 1);
	}

	@Test
	void listaVaziaAindaTemAPrimeiraPagina() {
		assertThat(ExportacaoDiagnosticos.paginar(List.of(), false)).singleElement()
			.satisfies(p -> assertThat(p.linhas()).isEmpty());
	}

	@Test
	void tituloDizQuantosEQualSituacao() {
		assertThat(ExportacaoDiagnosticos.titulo(37, filtros(null, false))).isEqualTo("37 diagnósticos");
		assertThat(ExportacaoDiagnosticos.titulo(1, filtros(StatusDiagnostico.CONCLUIDO, false))).isEqualTo("1 diagnóstico concluído");
		assertThat(ExportacaoDiagnosticos.titulo(1200, filtros(StatusDiagnostico.EM_ANDAMENTO, false)))
			.isEqualTo("1.200 diagnósticos em andamento");
		assertThat(ExportacaoDiagnosticos.titulo(3, filtros(null, true))).isEqualTo("3 diagnósticos inativos");
		assertThat(ExportacaoDiagnosticos.titulo(0, filtros(StatusDiagnostico.CONCLUIDO, false))).isEqualTo("Nenhum diagnóstico concluído");
	}

	@Test
	void whatsappLegivelComoNoPainel() {
		assertThat(ExportacaoDiagnosticos.whatsapp("5518988172192")).isEqualTo("(18) 98817-2192");
		assertThat(ExportacaoDiagnosticos.whatsapp("1134567890")).isEqualTo("(11) 3456-7890");
		assertThat(ExportacaoDiagnosticos.whatsapp("+1 415 555 0100")).isEqualTo("+1 415 555 0100");
		assertThat(ExportacaoDiagnosticos.whatsapp(" ")).isNull();
	}

	@Test
	void planilhaTemResumoCalculadoDimensoesEDatas() throws Exception {
		Map<String, Integer> dimensoes = new LinkedHashMap<>();
		dimensoes.put("Visibilidade", 40);
		dimensoes.put("Aprendizado", 72);
		Instant inicio = Instant.parse("2026-11-03T12:00:00Z");
		List<Registro> registros = List.of(
				new Registro("Ana Souza", "ana@x.com", "(11) 98765-4321", "Tera", "Tera", "Gerente", "1 a 3 anos", true,
						30, 30, 58, "PLATO", "Platô", dimensoes, inicio, inicio.plusSeconds(600), null),
				new Registro("Bruno Lima", "bruno@x.com", null, null, "Link geral", null, null, false, 12, 30, null, null,
						null, Map.of(), inicio, null, null));

		byte[] arquivo = new PlanilhaDiagnosticos().gerar("Todos os links", "2 diagnósticos", "Filtros: busca “a”",
				"Exportado em 26 de novembro de 2026, às 9h", registros, false);

		try (var pasta = new XSSFWorkbook(new ByteArrayInputStream(arquivo))) {
			XSSFSheet aba = pasta.getSheet("Diagnósticos");
			var texto = new DataFormatter();
			XSSFRow valores = aba.getRow(4);
			assertThat(valores.getCell(0).getNumericCellValue()).isEqualTo(2);
			assertThat(valores.getCell(1).getNumericCellValue()).isEqualTo(1);
			assertThat(valores.getCell(2).getNumericCellValue()).isEqualTo(1);
			assertThat(valores.getCell(3).getNumericCellValue()).isEqualTo(58);
			assertThat(valores.getCell(5).getNumericCellValue()).isEqualTo(1);

			XSSFRow cabecalho = aba.getRow(PlanilhaDiagnosticos.CABECALHO);
			assertThat(cabecalho.getCell(11).getStringCellValue()).isEqualTo("Visibilidade");
			assertThat(cabecalho.getCell(12).getStringCellValue()).isEqualTo("Aprendizado");
			assertThat(cabecalho.getCell(13).getStringCellValue()).isEqualTo("Iniciado em");
			assertThat(cabecalho.getLastCellNum()).isEqualTo((short) 15);

			XSSFRow ana = aba.getRow(PlanilhaDiagnosticos.CABECALHO + 1);
			assertThat(ana.getCell(7).getStringCellValue()).isEqualTo("Concluído");
			assertThat(ana.getCell(12).getNumericCellValue()).isEqualTo(72);
			// Horário de Brasília: 12h UTC são 9h.
			assertThat(texto.formatCellValue(ana.getCell(13))).isEqualTo("03/11/2026 09:00");
			XSSFRow bruno = aba.getRow(PlanilhaDiagnosticos.CABECALHO + 2);
			assertThat(bruno.getCell(7).getStringCellValue()).isEqualTo("Em andamento");
			assertThat(bruno.getCell(8).getStringCellValue()).isEqualTo("12 de 30");
		}
	}

	private static Filtros filtros(StatusDiagnostico status, boolean inativos) {
		return new Filtros(null, status, null, null, null, inativos);
	}

}
