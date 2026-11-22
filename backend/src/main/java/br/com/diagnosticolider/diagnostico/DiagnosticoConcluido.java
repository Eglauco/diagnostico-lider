package br.com.diagnosticolider.diagnostico;

import java.util.UUID;

/** Publicado quando o líder conclui; dispara a geração do PDF e o e-mail após o commit. */
public record DiagnosticoConcluido(UUID diagnosticoId) {
}
