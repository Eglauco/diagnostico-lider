package br.com.diagnosticolider.convite;

import java.time.Instant;
import java.util.List;

/**
 * A participação para a tela do RH (/acompanhar): os mesmos grupos do relatório em PDF, com as
 * datas de verdade (a tela escreve "há 5 min"). Só situação e contato: nenhuma resposta nem
 * resultado individual.
 *
 * @param peloLink       quantos entraram pelo link aberto da empresa (sem convite)
 * @param hoje           quantos responderam hoje (horário de Brasília)
 * @param ultimaResposta quando chegou a última resposta (null: ninguém respondeu)
 */
public record ParticipacaoAoVivo(int total, int responderam, int comecaram, int abriram, int naoAbriram, int peloLink,
		int hoje, Instant ultimaResposta, int inativos, List<Grupo> grupos) {

	/** codigo: COMECOU, ABRIU, NAO_ABRIU ou RESPONDEU, na ordem de quem está mais perto de terminar. */
	public record Grupo(String codigo, String titulo, String dica, List<Lider> lideres) {
	}

	/**
	 * respondidas e perguntas: só de quem começou e não terminou. link: o link pessoal, só de quem
	 * falta. alerta: o e-mail voltou ou foi para o spam (o RH manda o link por outro canal).
	 * origem: PLANILHA, MANUAL ou LINK (começou sozinho pelo link aberto da empresa).
	 * respondendoDeNovo: já respondeu e começou de novo (respondidas e perguntas são da tentativa nova;
	 * a resposta anterior vale até esta terminar).
	 */
	public record Lider(Long id, String nome, String cargo, String email, String whatsapp, String situacao,
			Instant enviadoEm, int envios, Instant primeiroAcessoEm, Instant ultimoAcessoEm, int acessos,
			Instant respondeuEm, Integer respondidas, Integer perguntas, String link, String alerta, String origem,
			boolean respondendoDeNovo) {
	}

}
