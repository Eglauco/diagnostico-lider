package br.com.diagnosticolider.acompanhamento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.diagnosticolider.empresa.EmpresaRepository;
import br.com.diagnosticolider.notificacao.EmailSender;

/**
 * A cada 5 minutos, passa por todas as empresas e faz o que for a hora de fazer (a regra está na
 * {@link Agenda}). Rodar sempre, em vez de só às 8h, faz o envio sair mesmo se o servidor estava
 * fora do ar na hora certa, e o consolidado sair logo depois que o último líder responde. O
 * agendador do Spring tem uma linha só: uma rodada nunca atropela a outra.
 */
@Component
class AgendadorAcompanhamento {

	private static final Logger log = LoggerFactory.getLogger(AgendadorAcompanhamento.class);

	private final EmpresaRepository empresas;

	private final AcompanhamentoService acompanhamento;

	private final EmailSender email;

	AgendadorAcompanhamento(EmpresaRepository empresas, AcompanhamentoService acompanhamento, EmailSender email) {
		this.empresas = empresas;
		this.acompanhamento = acompanhamento;
		this.email = email;
	}

	@Scheduled(cron = "0 */5 * * * *", zone = "America/Sao_Paulo")
	void rodar() {
		if (!email.ativo()) {
			return;
		}
		for (Long id : empresas.ids()) {
			try {
				acompanhamento.processar(id);
			}
			catch (RuntimeException e) {
				// Uma empresa com problema não pode travar as outras.
				log.error("Acompanhamento da empresa {} falhou nesta rodada", id, e);
			}
		}
	}

}
