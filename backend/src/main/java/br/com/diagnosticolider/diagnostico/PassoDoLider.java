package br.com.diagnosticolider.diagnostico;

import java.util.UUID;

/**
 * O que o líder (ou o painel, com o resultado individual) fez num diagnóstico, para a linha do
 * tempo do convite. Publicado depois do que aconteceu; quem ouve grava a auditoria.
 */
public sealed interface PassoDoLider {

	UUID diagnosticoId();

	/** Gravou uma resposta: quantas já respondeu de quantas. */
	record Respondeu(UUID diagnosticoId, int respondidas, int total) implements PassoDoLider {
	}

	/** Abriu a página do resultado (logo depois de concluir ou voltando pelo e-mail). */
	record ViuResultado(UUID diagnosticoId) implements PassoDoLider {
	}

	/** Usou o PDF do resultado: compartilhou (WhatsApp, e-mail…) ou baixou. */
	record UsouPdf(UUID diagnosticoId, boolean compartilhou) implements PassoDoLider {
	}

	/** Um administrador baixou o PDF individual no painel. */
	record PdfBaixadoNoPainel(UUID diagnosticoId, String autor) implements PassoDoLider {
	}

}
