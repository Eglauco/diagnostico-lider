package br.com.diagnosticolider.convite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import br.com.diagnosticolider.compartilhado.RegraNegocioException;

class PlanilhaConvitesTest {

	private final PlanilhaConvites planilha = new PlanilhaConvites();

	@Test
	void modeloTemOsCargosDaEmpresaMaisOutroCargoEALinhaDeExemplo() throws Exception {
		byte[] modelo = planilha.modelo("Construtora Horizonte", List.of("Supervisor", "Gerente"));

		try (var pasta = new XSSFWorkbook(new ByteArrayInputStream(modelo))) {
			assertThat(pasta.getSheetAt(0).getSheetName()).isEqualTo("Líderes");
			var cargos = pasta.getSheet("Cargos");
			assertThat(cargos.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Supervisor");
			assertThat(cargos.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Outro cargo");
			assertThat(pasta.getSheetAt(0).getDataValidations()).hasSize(2);
		}
		// Sem preencher nada, a única linha é o exemplo, e ele é reconhecido.
		var linhas = planilha.ler("modelo.xlsx", modelo);
		assertThat(linhas).singleElement().satisfies(l -> assertThat(l.exemplo()).isTrue());
	}

	@Test
	void leOModeloPreenchidoComTelefoneDigitadoComoNumero() throws Exception {
		byte[] modelo = planilha.modelo("Empresa", List.of("Gerente"));
		byte[] preenchido;
		try (var pasta = new XSSFWorkbook(new ByteArrayInputStream(modelo)); var saida = new ByteArrayOutputStream()) {
			Row linha = pasta.getSheet("Líderes").getRow(2);
			linha.getCell(0).setCellValue("  Bruno   Souza ");
			linha.getCell(1).setCellValue("bruno@empresa.com.br");
			linha.getCell(2).setCellValue(11987654321d);
			linha.getCell(3).setCellValue("Gerente");
			pasta.write(saida);
			preenchido = saida.toByteArray();
		}

		var linhas = planilha.ler("lideres.xlsx", preenchido);

		assertThat(linhas).hasSize(2);
		var bruno = linhas.get(1);
		assertThat(bruno.numero()).isEqualTo(3);
		assertThat(bruno.nome()).isEqualTo("Bruno Souza");
		assertThat(bruno.whatsapp()).isEqualTo("11987654321");
		assertThat(bruno.cargo()).isEqualTo("Gerente");
		assertThat(bruno.exemplo()).isFalse();
	}

	@Test
	void leCsvDoExcelBrasileiroComPontoEVirgulaEAcentosIgnorandoOutrasColunas() {
		String csv = "Nome completo;E-mail;Telefone;Função;Departamento\r\n\"Ana Lúcia\";ana@x.com;(11) 98765-4321;Diretora;\"Operações; Sul\"\r\n";
		byte[] conteudo = csv.getBytes(Charset.forName("windows-1252"));

		var linhas = planilha.ler("lideres.csv", conteudo);

		assertThat(linhas).singleElement().satisfies(l -> {
			assertThat(l.nome()).isEqualTo("Ana Lúcia");
			assertThat(l.email()).isEqualTo("ana@x.com");
			assertThat(l.whatsapp()).isEqualTo("(11) 98765-4321");
			assertThat(l.cargo()).isEqualTo("Diretora");
		});
	}

	@Test
	void semColunasDeNomeEEmailExplicaComoUsarOModelo() {
		byte[] csv = "Pessoa,Contato\nAna,ana@x.com\n".getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> planilha.ler("x.csv", csv)).isInstanceOf(RegraNegocioException.class)
			.hasMessageContaining("Nome completo");
	}

	@Test
	void whatsappSaiNoFormatoDoAppSemO55() {
		assertThat(ConviteService.whatsapp("5511987654321")).isEqualTo("(11) 98765-4321");
		assertThat(ConviteService.whatsapp("1133334444")).isEqualTo("(11) 3333-4444");
		assertThat(ConviteService.whatsapp("")).isEmpty();
		assertThat(ConviteService.nome("Ana Paula Souza (exemplo)")).isEqualTo("Ana Paula Souza");
	}

}
