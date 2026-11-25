package br.com.diagnosticolider.convite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import br.com.diagnosticolider.convite.RelatorioParticipacao.Bloco;
import br.com.diagnosticolider.convite.RelatorioParticipacao.Grupo;
import br.com.diagnosticolider.convite.RelatorioParticipacao.Lider;
import br.com.diagnosticolider.convite.RelatorioParticipacao.Linha;
import br.com.diagnosticolider.convite.RelatorioParticipacao.Pagina;

class ParticipacaoTest {

	private static List<Linha> linhas(int n) {
		return IntStream.range(0, n).mapToObj(i -> new Linha("Líder " + i, null, "l" + i + "@a.com", null, null, null)).toList();
	}

	private static double altura(Pagina p) {
		return p.blocos().stream()
			.mapToDouble(b -> ParticipacaoService.ALTURA_GRUPO + b.lideres().size() * ParticipacaoService.ALTURA_LINHA)
			.sum();
	}

	@Test
	void grupoQueNaoCabeContinuaNaProximaPaginaSemPerderNinguem() {
		var a = new Grupo("comecou", "Começaram", "", 4);
		var b = new Grupo("nao-abriu", "Não abriram", "", 30);
		var c = new Grupo("respondeu", "Responderam", "", 12);
		List<Pagina> paginas = ParticipacaoService.paginar(List.of(a, b, c), List.of(linhas(4), linhas(30), linhas(12)));

		assertThat(paginas).hasSizeGreaterThan(1);
		assertThat(paginas.stream().flatMap(p -> p.blocos().stream()).mapToInt(x -> x.lideres().size()).sum()).isEqualTo(46);
		assertThat(altura(paginas.getFirst())).isLessThanOrEqualTo(ParticipacaoService.ALTURA_PRIMEIRA);
		paginas.stream().skip(1).forEach(p -> assertThat(altura(p)).isLessThanOrEqualTo(ParticipacaoService.ALTURA_PAGINA));
		// Nunca sobra um título de grupo sozinho no pé da página.
		paginas.forEach(p -> p.blocos().forEach(x -> assertThat(x.lideres()).isNotEmpty()));
		Bloco continuacao = paginas.get(1).blocos().getFirst();
		assertThat(continuacao.continuacao()).isTrue();
		assertThat(paginas).extracting(Pagina::numero).containsExactly(IntStream.rangeClosed(1, paginas.size()).boxed().toArray(Integer[]::new));
	}

	@Test
	void semLideresSaiSoAPrimeiraPagina() {
		assertThat(ParticipacaoService.paginar(List.of(), List.of())).singleElement()
			.satisfies(p -> assertThat(p.blocos()).isEmpty());
	}

	@Test
	void planilhaTrazResumoCalculadoELinkDeQuemFalta() throws Exception {
		Instant agora = Instant.parse("2026-11-23T15:00:00Z");
		var lideres = List.of(
				new Lider("Ana", "Gerente", "ana@a.com", "(11) 98765-4321", SituacaoParticipacao.COMECOU, null, 0, agora,
						agora, 2, null, "https://app/i/abc"),
				new Lider("Bruno", null, "bruno@a.com", null, SituacaoParticipacao.NAO_ABRIU, null, 0, null, null, 0, null,
						"https://app/i/def"),
				new Lider("Carla", "Diretor", "carla@a.com", null, SituacaoParticipacao.RESPONDEU, agora, 1, agora, agora, 3,
						agora, null));
		byte[] xlsx = new PlanilhaParticipacao().gerar("ACME", "Atualizado agora", null, lideres, 1);

		try (var pasta = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			var aba = pasta.getSheet("Participação");
			var resumo = aba.getRow(4);
			assertThat(resumo.getCell(0).getNumericCellValue()).isEqualTo(3);
			assertThat(resumo.getCell(1).getNumericCellValue()).isEqualTo(1);
			assertThat(resumo.getCell(2).getNumericCellValue()).isEqualTo(1);
			assertThat(resumo.getCell(3).getNumericCellValue()).isEqualTo(1);
			assertThat(resumo.getCell(4).getNumericCellValue()).isCloseTo(1 / 3.0, org.assertj.core.data.Offset.offset(0.001));
			assertThat(aba.getRow(7).getCell(0).getStringCellValue()).isEqualTo("Ana");
			assertThat(aba.getRow(7).getCell(4).getStringCellValue()).isEqualTo("Começou, não terminou");
			assertThat(aba.getRow(7).getCell(8).getHyperlink().getAddress()).isEqualTo("https://app/i/abc");
			assertThat(aba.getRow(9).getCell(8).getStringCellValue()).isEmpty();
			assertThat(aba.getRow(1).getCell(0).getStringCellValue()).contains("1 inativo fica fora da lista");
		}
	}

}
