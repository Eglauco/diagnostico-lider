package br.com.diagnosticolider.diagnostico;

import java.time.Instant;

/** Quantos diagnósticos cada empresa recebeu, quantos foram concluídos e quando chegou o último. */
public record ContagemPorEmpresa(Long empresa, long total, long concluidos, Instant ultimo) {
}
