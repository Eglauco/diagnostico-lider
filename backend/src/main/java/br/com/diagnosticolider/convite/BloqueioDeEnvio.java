package br.com.diagnosticolider.convite;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import br.com.diagnosticolider.notificacao.EmailEnviado;
import br.com.diagnosticolider.notificacao.EmailEnviadoRepository;

/**
 * Quem não deve receber outro e-mail no mesmo endereço: o último convite ou lembrete voltou, foi
 * marcado como spam ou o Resend bloqueou. Mandar de novo só gera outra devolução ou outra queixa de
 * spam, e é isso que derruba a reputação do domínio (e a entrega nos e-mails corporativos).
 * Corrigir o e-mail do líder no painel libera o envio para o endereço novo.
 */
@Component
class BloqueioDeEnvio {

	enum Motivo {
		DEVOLVIDO, SPAM, BLOQUEADO
	}

	/** texto: o que o painel mostra ao lado do botão, já com o que fazer. */
	record Bloqueio(Motivo motivo, String texto) {
	}

	private static final Set<EmailEnviado.Tipo> CONVITE_OU_LEMBRETE = EnumSet.of(EmailEnviado.Tipo.CONVITE,
			EmailEnviado.Tipo.LEMBRETE);

	private final EmailEnviadoRepository emails;

	BloqueioDeEnvio(EmailEnviadoRepository emails) {
		this.emails = emails;
	}

	Map<Long, Bloqueio> de(Collection<Convite> lista) {
		if (lista.isEmpty()) {
			return Map.of();
		}
		Map<Long, EmailEnviado> ultimos = new HashMap<>();
		emails
			.findByConviteIdInAndTipoInOrderByEnviadoEmAscIdAsc(lista.stream().map(Convite::getId).toList(),
					CONVITE_OU_LEMBRETE)
			.forEach(e -> ultimos.put(e.getConviteId(), e));
		Map<Long, Bloqueio> bloqueios = new HashMap<>();
		for (Convite c : lista) {
			EmailEnviado ultimo = ultimos.get(c.getId());
			if (ultimo != null) {
				bloqueio(c.getEmail(), ultimo.getPara(), ultimo.getSituacao()).ifPresent(b -> bloqueios.put(c.getId(), b));
			}
		}
		return bloqueios;
	}

	Optional<Bloqueio> de(Convite convite) {
		return Optional.ofNullable(de(List.of(convite)).get(convite.getId()));
	}

	/** emailAtual: o do líder hoje. para: para onde foi o último e-mail. Endereço novo libera. */
	static Optional<Bloqueio> bloqueio(String emailAtual, String para, EmailEnviado.Situacao situacao) {
		if (emailAtual == null || para == null || !para.strip().equalsIgnoreCase(emailAtual.strip())) {
			return Optional.empty();
		}
		return switch (situacao) {
			case SPAM -> Optional.of(new Bloqueio(Motivo.SPAM,
					"O líder marcou o convite como spam. Novos e-mails para este endereço não saem: "
							+ "mande o link pessoal por WhatsApp."));
			case BLOQUEADO -> Optional.of(new Bloqueio(Motivo.BLOQUEADO,
					"O Resend bloqueou este endereço porque ele já devolveu e-mail ou marcou spam. "
							+ "Corrija o e-mail ou mande o link pessoal por WhatsApp."));
			case DEVOLVIDO -> Optional.of(new Bloqueio(Motivo.DEVOLVIDO,
					"O último e-mail voltou. Corrija o endereço em Editar antes de enviar de novo."));
			default -> Optional.empty();
		};
	}

}
