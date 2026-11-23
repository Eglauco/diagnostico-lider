/** Cores de cada faixa de resultado. "texto" tem contraste AA sobre "suave". */
export interface EstiloFaixa {
  rotulo: string;
  cor: string;
  texto: string;
  suave: string;
}

export const FAIXAS: Record<string, EstiloFaixa> = {
  TRAVADA: { rotulo: 'Travada', cor: '#C2364B', texto: '#A52B40', suave: '#F8E6E9' },
  PLATO: { rotulo: 'Platô', cor: '#A86A00', texto: '#855300', suave: '#FBF0DC' },
  ASCENSAO: { rotulo: 'Ascensão', cor: '#1F8A5B', texto: '#17714A', suave: '#E2F2EA' },
};

export const ORDEM_FAIXAS = ['TRAVADA', 'PLATO', 'ASCENSAO'] as const;

export function estiloFaixa(codigo: string | null | undefined): EstiloFaixa | null {
  return codigo ? (FAIXAS[codigo] ?? null) : null;
}
