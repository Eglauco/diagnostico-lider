package br.com.diagnosticolider.convite;

import java.time.Instant;
import java.util.List;

import br.com.diagnosticolider.empresa.PaletaMarca;

/**
 * Relatório de participação dos convites, para a empresa cobrar quem falta responder. Só
 * situação e contato: nenhuma resposta nem resultado individual.
 *
 * @param paginas o PDF já paginado (cada linha tem altura fixa; ver {@link ParticipacaoService})
 */
public record RelatorioParticipacao(String empresa, String sigla, String logo, boolean logoPedeNome,
		PaletaMarca.Tema tema, String rotulo, String titulo, String subtitulo, String atualizadoEm, String prazo,
		boolean encerrada, Placar placar, List<Pagina> paginas, int inativos, String nomeArquivo) {

	/** Barra do topo: quantos responderam, estão no meio ou nem abriram. */
	public record Placar(int total, List<Parte> partes) {
	}

	public record Parte(String codigo, String rotulo, int quantidade, int percentual) {
	}

	public record Pagina(int numero, List<Bloco> blocos) {
	}

	/** Um grupo (ou a continuação dele) dentro de uma página. */
	public record Bloco(Grupo grupo, boolean continuacao, List<Linha> lideres) {
	}

	public record Grupo(String codigo, String titulo, String dica, int quantidade) {
	}

	public record Linha(String nome, String cargo, String contato, String detalhe, String link, String linkVisivel) {
	}

	/** Um líder com tudo que a planilha precisa (datas de verdade, para filtrar e ordenar). */
	record Lider(String nome, String cargo, String email, String whatsapp, SituacaoParticipacao situacao,
			Instant enviadoEm, int envios, Instant primeiroAcessoEm, Instant ultimoAcessoEm, int acessos,
			Instant respondeuEm, String link) {
	}

}
