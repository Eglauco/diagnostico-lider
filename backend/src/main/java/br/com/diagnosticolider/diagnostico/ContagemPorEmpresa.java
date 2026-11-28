package br.com.diagnosticolider.diagnostico;

import java.time.Instant;

/**
 * Quantos diagnósticos cada empresa recebeu, quantos foram concluídos, quando chegou o último e o
 * índice médio dos concluídos (null: nenhum concluído).
 */
public record ContagemPorEmpresa(Long empresa, long total, long concluidos, Instant ultimo, Double media) {
}
