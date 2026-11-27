package br.com.diagnosticolider.convite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.diagnosticolider.convite.ConviteDtos.ConviteItem;
import br.com.diagnosticolider.convite.ConviteService.Tentativa;

/** O cadastro único de líderes: quem vale quando a pessoa responde duas vezes, e o funil separado. */
class LideresTest {

	private static final Instant SEG = Instant.parse("2026-11-23T12:00:00Z");

	private static final Instant TER = SEG.plusSeconds(86_400);

	private static final Instant QUA = TER.plusSeconds(86_400);

	@Test
	void respostaConcluidaVenceAQueFicouPelaMetade() {
		var concluida = new Tentativa(SEG, SEG.plusSeconds(300));
		var pelaMetade = new Tentativa(TER, null);
		assertThat(ConviteService.vence(concluida, pelaMetade)).isTrue();
		assertThat(ConviteService.vence(pelaMetade, concluida)).isFalse();
	}

	@Test
	void entreDuasConcluidasValeAMaisRecente() {
		var primeira = new Tentativa(SEG, SEG.plusSeconds(300));
		var segunda = new Tentativa(QUA, QUA.plusSeconds(300));
		assertThat(ConviteService.vence(segunda, primeira)).isTrue();
		assertThat(ConviteService.vence(primeira, segunda)).isFalse();
	}

	@Test
	void entreDuasPelaMetadeValeAUltimaQueComecou() {
		assertThat(ConviteService.vence(new Tentativa(TER, null), new Tentativa(SEG, null))).isTrue();
	}

	@Test
	void funilContaSoOsConvidadosEOLinkFicaAParte() {
		var itens = List.of(
				item(StatusConvite.CONCLUIDO, OrigemLider.PLANILHA, TER, 1),
				item(StatusConvite.ENVIADO, OrigemLider.PLANILHA, TER, 0),
				item(StatusConvite.AGUARDANDO_ENVIO, OrigemLider.MANUAL, null, 0),
				item(StatusConvite.CONCLUIDO, OrigemLider.LINK, null, 0),
				item(StatusConvite.COMECOU, OrigemLider.LINK, null, 0),
				item(StatusConvite.INATIVO, OrigemLider.LINK, null, 0));

		ConviteDtos.Resumo r = ConviteService.resumo(itens);

		assertThat(r.total()).isEqualTo(5);
		assertThat(r.convidados()).isEqualTo(3);
		assertThat(r.peloLink()).isEqualTo(2);
		assertThat(r.peloLinkConcluiram()).isEqualTo(1);
		// O funil é só dos convidados: quem veio pelo link não "abriu um convite".
		assertThat(r.enviados()).isEqualTo(2);
		assertThat(r.abriram()).isEqualTo(1);
		assertThat(r.concluiram()).isEqualTo(1);
		assertThat(r.inativos()).isEqualTo(1);
	}

	private static ConviteItem item(StatusConvite status, OrigemLider origem, Instant enviadoEm, int acessos) {
		return new ConviteItem(1L, "Ana", "ana@x.com", null, null, false, null, status, SEG, enviadoEm,
				enviadoEm == null ? 0 : 1, null, acessos, null, "https://app/i/x", UUID.randomUUID(), null, null, null,
				origem, null);
	}

}
