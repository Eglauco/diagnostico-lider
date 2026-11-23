package br.com.diagnosticolider.acesso;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class ProtecaoLoginTest {

	/** Relógio que o teste adianta à mão. */
	static final class Relogio extends Clock {

		Instant agora = Instant.parse("2026-11-22T12:00:00Z");

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return agora;
		}

	}

	private final Relogio relogio = new Relogio();

	private final ProtecaoLogin protecao = new ProtecaoLogin(relogio);

	@Test
	void bloqueiaOEmailDepoisDeCincoErros() {
		for (int i = 0; i < ProtecaoLogin.LIMITE_POR_EMAIL - 1; i++) {
			protecao.falhou("ana@exemplo.com", "1.1.1.1");
		}
		assertThat(protecao.bloqueio("ana@exemplo.com", "1.1.1.1")).isEmpty();

		protecao.falhou("ana@exemplo.com", "1.1.1.1");
		assertThat(protecao.bloqueio("ana@exemplo.com", "2.2.2.2")).isPresent();
		assertThat(protecao.bloqueio("bia@exemplo.com", "2.2.2.2")).isEmpty();
	}

	@Test
	void liberaQuandoAJanelaPassa() {
		for (int i = 0; i < ProtecaoLogin.LIMITE_POR_EMAIL; i++) {
			protecao.falhou("ana@exemplo.com", "1.1.1.1");
		}
		relogio.agora = relogio.agora.plus(ProtecaoLogin.JANELA).plus(Duration.ofSeconds(1));
		assertThat(protecao.bloqueio("ana@exemplo.com", "1.1.1.1")).isEmpty();
	}

	@Test
	void bloqueiaOIpQueTentaMuitosEmails() {
		for (int i = 0; i < ProtecaoLogin.LIMITE_POR_IP; i++) {
			protecao.falhou("pessoa" + i + "@exemplo.com", "3.3.3.3");
		}
		assertThat(protecao.bloqueio("nova@exemplo.com", "3.3.3.3")).isPresent();
	}

	@Test
	void acertarASenhaZeraOsErrosDoEmail() {
		for (int i = 0; i < ProtecaoLogin.LIMITE_POR_EMAIL - 1; i++) {
			protecao.falhou("ana@exemplo.com", "1.1.1.1");
		}
		protecao.entrou("ana@exemplo.com");
		protecao.falhou("ana@exemplo.com", "1.1.1.1");
		assertThat(protecao.bloqueio("ana@exemplo.com", "4.4.4.4")).isEmpty();
	}

}
