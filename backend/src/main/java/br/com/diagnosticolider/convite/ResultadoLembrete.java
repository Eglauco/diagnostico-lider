package br.com.diagnosticolider.convite;

/** Como foi um lote de lembretes: quantos saíram, quantos falharam e o motivo da última falha. */
public record ResultadoLembrete(int enviados, int falhas, String motivo) {
}
