package br.com.diagnosticolider.acompanhamento;

import java.util.List;

import br.com.diagnosticolider.convite.ConviteEmail;
import br.com.diagnosticolider.convite.ParticipacaoService.Devolvido;
import br.com.diagnosticolider.convite.ParticipacaoService.Pendente;
import br.com.diagnosticolider.empresa.PaletaMarca;

/**
 * O que o template email/gestor.html usa, no relatório diário e no final. Público porque o
 * Thymeleaf lê os campos por reflexão.
 *
 * @param rotulo     linha pequena acima do título ("Acompanhamento da participação · 25 de novembro")
 * @param novidade   o que mudou desde o último relatório ("+3 desde o último relatório")
 * @param placar     as partes da barra (só as que têm alguém)
 * @param pendentes  os primeiros de quem falta; outrosPendentes: quantos ficaram só no PDF
 * @param consolidado números do grupo no final (null no diário ou sem o mínimo de líderes)
 * @param aviso      no final sem o mínimo: por que o consolidado não foi
 * @param anexos     nomes legíveis do que vai anexado
 * @param urlAoVivo  a página do RH (/acompanhar), para ver a participação e o resultado a qualquer hora
 */
public record GestorEmail(String empresa, String sigla, ConviteEmail.Logo logo, PaletaMarca.Tema tema, boolean fim,
		String rotulo, String titulo, String subtitulo, String novidade, String prazo, List<Parte> placar,
		List<Pendente> pendentes, int outrosPendentes, List<Devolvido> devolvidos, Consolidado consolidado, String aviso,
		List<String> anexos, String rodape, String urlAoVivo) {

	public record Parte(String rotulo, int quantidade, int percentual, String cor) {
	}

	public record Consolidado(int indiceMedio, String faixa, String corFaixa, int lideres) {
	}

}
