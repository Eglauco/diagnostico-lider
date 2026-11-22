/** Contratos da API pública (espelham os records do backend). */

export interface CampanhaPublica {
  codigo: string;
  nome: string;
  empresa: { nome: string } | null;
  questionario: Questionario;
}

export interface Questionario {
  versaoId: number;
  titulo: string;
  dimensoes: { codigo: string; nome: string }[];
  perguntas: Pergunta[];
}

export interface Pergunta {
  id: number;
  codigo: string;
  dimensao: string | null;
  enunciado: string;
  ajuda: string | null;
  tipo: 'ESCOLHA_UNICA' | 'ESCOLHA_MULTIPLA';
  obrigatoria: boolean;
  opcoes: Opcao[];
}

export interface Opcao {
  id: number;
  texto: string;
}

export interface DadosRespondente {
  nome: string;
  email: string;
  whatsapp: string;
  empresa: string;
}

export interface Diagnostico {
  id: string;
  campanha: string;
  status: 'EM_ANDAMENTO' | 'CONCLUIDO';
  primeiroNome: string;
  /** perguntaId → opções escolhidas */
  respostas: Record<string, number[]>;
}

export type CodigoFaixa = 'TRAVADA' | 'PLATO' | 'ASCENSAO';

export interface Resultado {
  id: string;
  nome: string;
  primeiroNome: string;
  concluidoEm: string;
  pontuacao: number;
  faixa: {
    codigo: CodigoFaixa;
    titulo: string;
    resumo: string;
    descricao: string;
    recomendacoes: string[];
  };
  dimensoes: { codigo: string; nome: string; descricao: string; pontuacao: number }[];
  emailMascarado: string;
  statusEmail: 'NAO_ENVIADO' | 'PENDENTE' | 'ENVIADO' | 'FALHOU';
}

/** Erro no formato application/problem+json (RFC 9457). */
export interface Problema {
  status: number;
  detail?: string;
  campos?: Partial<Record<keyof DadosRespondente, string>>;
  pendentes?: number[];
}
