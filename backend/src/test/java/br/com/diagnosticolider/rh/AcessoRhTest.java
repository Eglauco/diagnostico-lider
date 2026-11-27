package br.com.diagnosticolider.rh;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** Regras da entrada do RH que não dependem do banco. */
class AcessoRhTest {

	private static final Instant AGORA = Instant.parse("2026-11-27T12:00:00Z");

	@Test
	void codigoServeUmaVezDentroDoPrazoEAteCincoErros() {
		var c = new CodigoRh("ana@x.com", CodigoRh.Tipo.CODIGO, "h", "l", AGORA, AGORA.plus(Duration.ofMinutes(10)));
		assertThat(c.valido(AGORA.plusSeconds(60), 5)).isTrue();
		assertThat(c.valido(AGORA.plus(Duration.ofMinutes(10)), 5)).isFalse();
		for (int i = 0; i < 5; i++) {
			c.errou();
		}
		assertThat(c.valido(AGORA.plusSeconds(60), 5)).isFalse();

		var outro = new CodigoRh("ana@x.com", CodigoRh.Tipo.CODIGO, "h", "l2", AGORA, AGORA.plus(Duration.ofMinutes(10)));
		outro.usar(AGORA.plusSeconds(5));
		assertThat(outro.valido(AGORA.plusSeconds(10), 5)).isFalse();
	}

	@Test
	void aparelhoLembradoRenovaNoMaximoUmaVezPorHora() {
		var a = new AcessoRh("ana@x.com", "t", AGORA, Duration.ofDays(30), "iPhone");
		assertThat(a.usar(AGORA.plus(Duration.ofMinutes(20)), Duration.ofDays(30), Duration.ofHours(1))).isFalse();
		assertThat(a.usar(AGORA.plus(Duration.ofHours(2)), Duration.ofDays(30), Duration.ofHours(1))).isTrue();
		// Renovou às 2h: vale até 30 dias e 2 horas depois do primeiro uso.
		assertThat(a.valido(AGORA.plus(Duration.ofDays(30)).plus(Duration.ofHours(1)))).isTrue();
		assertThat(a.valido(AGORA.plus(Duration.ofDays(30)).plus(Duration.ofHours(3)))).isFalse();
		a.encerrar(AGORA.plus(Duration.ofDays(1)));
		assertThat(a.valido(AGORA.plus(Duration.ofDays(1)))).isFalse();
	}

	@Test
	void pedirCodigoTemLimitePorEmailEPorIp() {
		var protecao = new ProtecaoRh(Clock.fixed(AGORA, ZoneOffset.UTC));
		for (int i = 0; i < ProtecaoRh.PEDIDOS_POR_EMAIL; i++) {
			assertThat(protecao.bloqueioDePedido("ana@x.com", "1.1.1.1")).isEmpty();
			protecao.pediu("ana@x.com", "1.1.1.1");
		}
		assertThat(protecao.bloqueioDePedido("ana@x.com", "2.2.2.2")).isPresent();
		assertThat(protecao.bloqueioDePedido("bruno@x.com", "2.2.2.2")).isEmpty();
	}

	@Test
	void hashNaoGuardaOCodigo() {
		assertThat(AcessoRhService.hash("123456")).hasSize(64).doesNotContain("123456");
		assertThat(AcessoRhService.normalizar("  Ana@Empresa.COM ")).isEqualTo("ana@empresa.com");
	}

}
