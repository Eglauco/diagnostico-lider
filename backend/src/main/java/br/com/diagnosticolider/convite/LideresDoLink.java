package br.com.diagnosticolider.convite;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.diagnosticolider.diagnostico.Diagnostico;

/**
 * Na subida: quem começou pelo link aberto de uma empresa e ainda não está no cadastro de líderes
 * (de antes de o cadastro existir) entra agora, com a mesma regra de quem chega pelo link hoje
 * ({@link ConviteService#entrarNoCadastro}). Sem nada pendente, não faz nada.
 */
@Component
class LideresDoLink implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(LideresDoLink.class);

	private final ConviteRepository convites;

	private final ConviteService servico;

	private final TransactionTemplate transacao;

	private final Clock clock;

	LideresDoLink(ConviteRepository convites, ConviteService servico, TransactionTemplate transacao, Clock clock) {
		this.convites = convites;
		this.servico = servico;
		this.transacao = transacao;
		this.clock = clock;
	}

	@Override
	public void run(ApplicationArguments args) {
		Integer antes = transacao.execute(status -> {
			List<Diagnostico> pendentes = convites.semCadastro();
			Instant agora = clock.instant();
			pendentes.forEach(d -> servico.entrarNoCadastro(d, agora, true));
			return pendentes.size();
		});
		Integer depois = transacao.execute(status -> convites.semCadastro().size());
		if (antes != null && antes > 0) {
			log.info("Cadastro de líderes: {} diagnóstico(s) do link aberto conferido(s); {} seguem sem cadastro "
					+ "(respondendo de novo, entram ao terminar).", antes, depois);
		}
	}

}
