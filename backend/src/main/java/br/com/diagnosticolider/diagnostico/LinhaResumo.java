package br.com.diagnosticolider.diagnostico;

/** Projeção enxuta para os indicadores do painel: uma linha por diagnóstico. */
public record LinhaResumo(StatusDiagnostico status, String faixa, Integer pontuacao) {
}
