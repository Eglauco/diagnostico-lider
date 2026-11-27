/** Contratos da API pública (espelham os records do backend). */

import { MarcaEmpresa } from '../compartilhado/tema-marca';

export interface CampanhaPublica {
  codigo: string;
  nome: string;
  /** Link de empresa: nome, monograma e paleta. Link geral: null. */
  empresa: MarcaEmpresa | null;
  /** Empresa com cargos cadastrados: o líder informa cargo e tempo no cargo. Senão, null. */
  perfil: PerfilCampanha | null;
  questionario: Questionario;
  /** Até quando a avaliação da empresa vai (ISO; null sem data). */
  encerraEm: string | null;
  /** Já passou da data: ninguém começa nem responde mais. */
  encerrada: boolean;
}

/**
 * Link pessoal (/i/:token): os dados que vieram da planilha do RH para o líder confirmar, o link
 * da empresa e o diagnóstico, se ele já começou. cargo: id, CARGO_OUTRO ou null.
 */
export interface ConvitePublico {
  nome: string;
  email: string;
  whatsapp: string | null;
  cargo: string | null;
  campanha: CampanhaPublica;
  diagnosticoId: string | null;
  /** PLANILHA, MANUAL ou LINK (começou pelo link aberto da empresa). */
  origem: 'PLANILHA' | 'MANUAL' | 'LINK';
}

export interface PerfilCampanha {
  /** valor = id do cargo, na ordem da hierarquia. */
  cargos: OpcaoPerfil[];
  /** valor = código da faixa de tempo (ex.: DE_1_A_2). */
  tempos: OpcaoPerfil[];
}

export interface OpcaoPerfil {
  valor: string;
  texto: string;
}

/** "Meu cargo não está na lista". */
export const CARGO_OUTRO = 'outro';

/** Cargo (id ou CARGO_OUTRO), nome escrito em "Outro" (opcional) e tempo no cargo. */
export interface PerfilLider {
  cargo: string | null;
  outroNome: string;
  tempo: string | null;
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

/** A empresa não é pedida: no link de empresa ela vem do próprio link; no geral, não há. */
export interface DadosRespondente {
  nome: string;
  email: string;
  whatsapp: string;
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
  /** Quando o diagnóstico veio do link de uma empresa. */
  empresa: MarcaEmpresa | null;
}

/** Erro no formato application/problem+json (RFC 9457). */
export interface Problema {
  status: number;
  detail?: string;
  /** Campo → mensagem. Além dos dados do líder: cargo, cargoOutroNome e tempoCargo. */
  campos?: Record<string, string>;
  pendentes?: number[];
}
