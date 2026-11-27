package br.com.diagnosticolider.rh;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Freia o uso abusivo da entrada por código. Pedir código manda e-mail (gasta a cota e a reputação
 * do domínio), então cada e-mail pede no máximo 3 a cada 15 minutos, e cada endereço IP, 10.
 * Errar o código: 5 tentativas por código (no banco) e 20 erros por IP na janela.
 * Em memória: com uma instância só (Railway), é suficiente.
 */
@Component
class ProtecaoRh {

	static final Duration JANELA = Duration.ofMinutes(15);

	static final int PEDIDOS_POR_EMAIL = 3;

	static final int PEDIDOS_POR_IP = 10;

	static final int ERROS_POR_IP = 20;

	private static final int LIMPAR_ACIMA_DE = 10_000;

	private record Contagem(int quantidade, Instant inicio) {
	}

	private final Map<String, Contagem> contagens = new ConcurrentHashMap<>();

	private final Clock clock;

	ProtecaoRh(Clock clock) {
		this.clock = clock;
	}

	/** Quanto falta para poder pedir de novo, se passou do limite. */
	Optional<Duration> bloqueioDePedido(String email, String ip) {
		Instant agora = clock.instant();
		return restante("pedido-email:" + email, PEDIDOS_POR_EMAIL, agora)
			.or(() -> restante("pedido-ip:" + ip, PEDIDOS_POR_IP, agora));
	}

	void pediu(String email, String ip) {
		Instant agora = clock.instant();
		contar("pedido-email:" + email, agora);
		contar("pedido-ip:" + ip, agora);
	}

	Optional<Duration> bloqueioDeTentativa(String ip) {
		return restante("erro-ip:" + ip, ERROS_POR_IP, clock.instant());
	}

	void errou(String ip) {
		contar("erro-ip:" + ip, clock.instant());
	}

	private void contar(String chave, Instant agora) {
		contagens.merge(chave, new Contagem(1, agora), (atual, nova) -> atual.inicio().plus(JANELA).isBefore(agora) ? nova
				: new Contagem(atual.quantidade() + 1, atual.inicio()));
		if (contagens.size() > LIMPAR_ACIMA_DE) {
			contagens.values().removeIf(c -> c.inicio().plus(JANELA).isBefore(agora));
		}
	}

	private Optional<Duration> restante(String chave, int limite, Instant agora) {
		Contagem c = contagens.get(chave);
		if (c == null || c.quantidade() < limite) {
			return Optional.empty();
		}
		Instant libera = c.inicio().plus(JANELA);
		return agora.isBefore(libera) ? Optional.of(Duration.between(agora, libera)) : Optional.empty();
	}

}
