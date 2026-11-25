package br.com.diagnosticolider.convite;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lembrete aos líderes antes do prazo (3 dias e 1 dia antes). Vai só para quem já recebeu o
 * convite e ainda não concluiu; quem nunca recebeu o convite fica de fora.
 */
@Service
public class LembreteService {

	private final ConviteRepository convites;

	private final EnvioConvitesService envio;

	private final TransactionTemplate transacao;

	private final BloqueioDeEnvio bloqueios;

	LembreteService(ConviteRepository convites, EnvioConvitesService envio, TransactionTemplate transacao,
			BloqueioDeEnvio bloqueios) {
		this.bloqueios = bloqueios;
		this.convites = convites;
		this.envio = envio;
		this.transacao = transacao;
	}

	/** chamada: "Faltam 3 dias para responder", "Amanhã é o último dia para responder"… */
	public ResultadoLembrete lembrar(Long empresaId, String chamada) {
		List<Long> ids = transacao.execute(status -> {
			List<Convite> lembraveis = convites.daEmpresa(empresaId)
				.stream()
				.filter(c -> !c.inativo() && EnvioConvitesService.LEMBRAVEIS.contains(c.status()))
				.toList();
			// Quem voltou ou marcou spam no mesmo endereço não recebe lembrete (só pioraria a reputação).
			var bloqueados = bloqueios.de(lembraveis);
			return lembraveis.stream().map(Convite::getId).filter(id -> !bloqueados.containsKey(id)).toList();
		});
		return ids == null || ids.isEmpty() ? new ResultadoLembrete(0, 0, null) : envio.lembrar(ids, chamada);
	}

}
