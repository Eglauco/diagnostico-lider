/** Contratos da API do painel (espelham AdminDtos no backend). */

import { LogoEmpresa, TemaMarca } from '../compartilhado/tema-marca';

export type StatusDiagnostico = 'EM_ANDAMENTO' | 'CONCLUIDO';
export type StatusEmail = 'NAO_ENVIADO' | 'PENDENTE' | 'ENVIADO' | 'FALHOU';

export interface Painel {
  total: number;
  concluidos: number;
  emAndamento: number;
  indiceMedio: number | null;
  porFaixa: Record<string, number>;
  pontos: { indice: number; faixa: string }[];
  campanhas: { codigo: string; nome: string; empresa: string | null; empresaId: number | null; total: number }[];
  /** Cargos do link escolhido, para filtrar (valor: id do cargo ou "outro"). Vazio sem cargos. */
  cargos: { valor: string; nome: string; total: number }[];
}

/** Empresas (espelham EmpresaDtos no backend). */
export interface LinkEmpresa {
  codigo: string;
  url: string;
  ativo: boolean;
}

export interface EmpresaResumo {
  id: number;
  nome: string;
  criadaEm: string;
  link: LinkEmpresa | null;
  diagnosticos: number;
  concluidos: number;
  ultimoDiagnostico: string | null;
  cores: CoresEmpresa;
  logo: LogoEmpresa | null;
}

/** Cores da marca como cadastradas (null: identidade do Diagnóstico do Líder). */
export interface CoresEmpresa {
  primaria: string | null;
  secundaria: string | null;
}

/** Prévia da paleta enquanto o administrador escolhe as cores. */
export interface PreviaTema {
  tema: TemaMarca;
  ajustes: string[];
}

export interface EmpresaDetalhe {
  id: number;
  nome: string;
  criadaEm: string;
  link: LinkEmpresa | null;
  indicadores: {
    total: number;
    concluidos: number;
    emAndamento: number;
    indiceMedio: number | null;
    porFaixa: Record<string, number>;
  };
  /** Diagnósticos concluídos necessários para o relatório consolidado (anonimato). */
  minimoRelatorio: number;
  cores: CoresEmpresa;
  /** Paleta pronta (cartão do QR Code e prévia). */
  tema: TemaMarca;
  logo: LogoEmpresa | null;
  /** Na ordem da hierarquia, com quantos diagnósticos cada um já tem. */
  cargos: { id: number; nome: string; diagnosticos: number }[];
}

export interface Disponibilidade {
  codigo: string;
  url: string;
  valido: boolean;
  disponivel: boolean;
  sugestao: string | null;
}

export interface Pagina<T> {
  itens: T[];
  total: number;
  pagina: number;
  tamanho: number;
  totalPaginas: number;
}

export interface ItemLista {
  id: string;
  nome: string;
  email: string;
  empresa: string | null;
  campanha: string;
  campanhaNome: string;
  campanhaDeEmpresa: boolean;
  status: StatusDiagnostico;
  pontuacao: number | null;
  faixa: string | null;
  faixaTitulo: string | null;
  statusEmail: StatusEmail;
  iniciadoEm: string;
  concluidoEm: string | null;
  respondidas: number;
  totalPerguntas: number;
  /** "Gerente", "Outro: Head de vendas", "Outro cargo" ou null. */
  cargo: string | null;
}

export interface Detalhe {
  id: string;
  status: StatusDiagnostico;
  statusEmail: StatusEmail;
  iniciadoEm: string;
  concluidoEm: string | null;
  consentimentoEm: string;
  emailEnviadoEm: string | null;
  lider: {
    nome: string;
    email: string;
    whatsapp: string;
    empresa: string | null;
    cargo: string | null;
    tempoCargo: string | null;
  };
  origem: { campanha: string; campanhaNome: string; empresaVinculada: string | null; questionario: string; versao: number };
  resultado: {
    pontuacao: number;
    faixa: { codigo: string; titulo: string; resumo: string };
    dimensoes: { codigo: string; nome: string; pontuacao: number }[];
  } | null;
  respostas: {
    perguntaId: number;
    numero: number;
    dimensao: string | null;
    enunciado: string;
    respondida: boolean;
    opcoes: { id: number; texto: string; pontos: number; escolhida: boolean }[];
  }[];
}

export const API_ADMIN = '/api/admin';
