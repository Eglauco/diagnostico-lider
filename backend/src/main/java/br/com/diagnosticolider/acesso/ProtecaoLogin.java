package br.com.diagnosticolider.acesso;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Freia tentativas de adivinhar senha: depois de 5 erros no mesmo e-mail (ou 20 vindos do
 * mesmo endereço IP) em 15 minutos, o login fica bloqueado até a janela passar.
 * Em memória: com uma instância só (Railway), é suficiente.
 */
@Component
class ProtecaoLogin {

	static final int LIMITE_POR_EMAIL = 5;

	static final int LIMITE_POR_IP = 20;

	static final Duration JANELA = Duration.ofMinutes(15);

	private static final int LIMPAR_ACIMA_DE = 10_000;

	private record Falhas(int quantidade, Instant inicio) {
	}

	private final Map<String, Falhas> falhas = new ConcurrentHashMap<>();

	private final Clock clock;

	ProtecaoLogin(Clock clock) {
		this.clock = clock;
	}

	/** Quanto falta para poder tentar de novo, se estiver bloqueado. */
	Optional<Duration> bloqueio(String email, String ip) {
		Instant agora = clock.instant();
		return restante("email:" + email, LIMITE_POR_EMAIL, agora)
			.or(() -> restante("ip:" + ip, LIMITE_POR_IP, agora));
	}

	void falhou(String email, String ip) {
		Instant agora = clock.instant();
		registrar("email:" + email, agora);
		registrar("ip:" + ip, agora);
		if (falhas.size() > LIMPAR_ACIMA_DE) {
			falhas.values().removeIf(f -> f.inicio().plus(JANELA).isBefore(agora));
		}
	}

	void entrou(String email) {
		falhas.remove("email:" + email);
	}

	private void registrar(String chave, Instant agora) {
		falhas.merge(chave, new Falhas(1, agora), (atual, nova) -> atual.inicio().plus(JANELA).isBefore(agora) ? nova
				: new Falhas(atual.quantidade() + 1, atual.inicio()));
	}

	private Optional<Duration> restante(String chave, int limite, Instant agora) {
		Falhas f = falhas.get(chave);
		if (f == null || f.quantidade() < limite) {
			return Optional.empty();
		}
		Instant libera = f.inicio().plus(JANELA);
		return agora.isBefore(libera) ? Optional.of(Duration.between(agora, libera)) : Optional.empty();
	}

}
