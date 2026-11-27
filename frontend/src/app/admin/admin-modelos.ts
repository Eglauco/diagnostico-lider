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
  encerramento: Encerramento;
}

/** Até quando a avaliação vai (em: ISO, null sem data) e se já encerrou. */
export interface Encerramento {
  em: string | null;
  encerrada: boolean;
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
    /** O cadastro de líderes: convidados (planilha e manual) e quem começou pelo link aberto. */
    lideres: number;
    convidados: number;
    peloLink: number;
  };
  /** Diagnósticos concluídos necessários para o relatório consolidado (anonimato). */
  minimoRelatorio: number;
  cores: CoresEmpresa;
  /** Paleta pronta (cartão do QR Code e prévia). */
  tema: TemaMarca;
  logo: LogoEmpresa | null;
  /** Na ordem da hierarquia, com quantos diagnósticos cada um já tem. */
  cargos: { id: number; nome: string; diagnosticos: number }[];
  encerramento: Encerramento;
  /** E-mails dos gestores que recebem os relatórios automáticos (pode ser vazio). */
  gestores: string[];
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
  /** Inativado: fica guardado, mas fora de todos os cálculos. */
  inativadoEm: string | null;
  /** O líder respondeu de novo: vale esta outra resposta (esta saiu dos cálculos). */
  substituidoPor: string | null;
  respostas: {
    perguntaId: number;
    numero: number;
    dimensao: string | null;
    enunciado: string;
    respondida: boolean;
    opcoes: { id: number; texto: string; pontos: number; escolhida: boolean }[];
  }[];
}

// Convites nominais -----------------------------------------------------------

export type StatusConvite = 'AGUARDANDO_ENVIO' | 'ENVIADO' | 'ABRIU' | 'COMECOU' | 'CONCLUIDO' | 'INATIVO';

/** Como o líder entrou na avaliação: planilha do RH, cadastro manual ou o link aberto da empresa. */
export type OrigemLider = 'PLANILHA' | 'MANUAL' | 'LINK';

export interface CargoOpcao {
  id: number;
  nome: string;
}

export interface ConviteItem {
  id: number;
  nome: string;
  email: string;
  whatsapp: string | null;
  cargoId: number | null;
  cargoOutro: boolean;
  cargo: string | null;
  status: StatusConvite;
  criadoEm: string;
  enviadoEm: string | null;
  envios: number;
  ultimoAcessoEm: string | null;
  acessos: number;
  concluidoEm: string | null;
  link: string;
  /** O diagnóstico do convite, quando o líder já começou. */
  diagnosticoId: string | null;
  /** Por que o último e-mail não saiu. Some quando um envio der certo ou o líder abrir o link. */
  falhaEnvio: string | null;
  /**
   * O último e-mail voltou, foi marcado como spam ou o Resend bloqueou o endereço: outro envio
   * para o mesmo e-mail não sai (só pioraria a reputação). Corrigir o e-mail libera.
   */
  bloqueio: 'DEVOLVIDO' | 'SPAM' | 'BLOQUEADO' | null;
  /** O que fazer, para mostrar ao lado do botão de envio. */
  bloqueioMotivo: string | null;
  origem: OrigemLider;
  /** Já concluiu e está respondendo de novo pelo link aberto (a resposta concluída vale até esta terminar). */
  novaTentativa: { nome: string; iniciadoEm: string; respondidas: number; perguntas: number } | null;
}

/**
 * O funil dos convidados: cada número conta quem chegou pelo menos até ali. Quem começou pelo link
 * aberto fica à parte (peloLink). total: todos os líderes ativos. Inativos fora dos cálculos.
 */
export interface ResumoConvites {
  total: number;
  aguardandoEnvio: number;
  enviados: number;
  abriram: number;
  comecaram: number;
  concluiram: number;
  inativos: number;
  convidados: number;
  peloLink: number;
  peloLinkConcluiram: number;
}

export interface Convites {
  /** Há provedor de e-mail configurado (sem ele, o disparo fica desligado). */
  emailAtivo: boolean;
  encerraEm: string | null;
  encerrada: boolean;
  resumo: ResumoConvites;
  convites: ConviteItem[];
  cargos: CargoOpcao[];
}

/** Linha lida da planilha, com o que o servidor já sabe (cargo que bateu, sugestão, já convidado). */
export interface LinhaImportada {
  linha: number;
  nome: string;
  email: string;
  whatsapp: string;
  cargoTexto: string;
  cargoId: number | null;
  cargoOutro: boolean;
  sugestaoCargoId: number | null;
  jaConvidado: boolean;
  exemplo: boolean;
}

export interface Importacao {
  arquivo: string;
  linhas: LinhaImportada[];
  cargos: CargoOpcao[];
}

export interface NovoConvite {
  nome: string;
  email: string;
  whatsapp: string;
  cargoId: number | null;
  cargoOutro: boolean;
}

export type TipoEventoConvite =
  | 'CRIADO'
  | 'EDITADO'
  | 'ENVIADO'
  | 'FALHA_ENVIO'
  | 'RESULTADO_ENVIADO'
  | 'EMAIL_DEVOLVIDO'
  | 'EMAIL_SPAM'
  | 'COMECOU_RESPONDER'
  | 'VIU_RESULTADO'
  | 'BAIXOU_PDF'
  | 'COMPARTILHOU_PDF'
  | 'PDF_BAIXADO_PAINEL'
  | 'ABERTO'
  | 'DADOS_CONFIRMADOS'
  | 'DADOS_CORRIGIDOS'
  | 'CONCLUIDO'
  | 'LEMBRETE'
  | 'INATIVADO'
  | 'REATIVADO'
  | 'COMECOU_PELO_LINK'
  | 'RESPONDEU_DE_NOVO';

export interface EventoConvite {
  tipo: TipoEventoConvite;
  quando: string;
  detalhe: string | null;
  /** Quem fez no painel (null: o líder, o sistema ou o envio automático). */
  autor: string | null;
  /** A entrega do e-mail deste evento (convite, lembrete, resultado), pelos avisos do Resend. */
  email: EntregaEmail | null;
}

export type SituacaoEntrega = 'ENVIADO' | 'ENTREGUE' | 'ATRASADO' | 'DEVOLVIDO' | 'SPAM' | 'BLOQUEADO' | 'FALHOU';

export interface EntregaEmail {
  situacao: SituacaoEntrega;
  para: string;
  entregueEm: string | null;
  atualizadoEm: string | null;
  detalhe: string | null;
}

export const API_ADMIN = '/api/admin';

/** O cartão "Relatórios para o gestor": situação, próximo envio, lembretes aos líderes e histórico. */
export interface PainelAcompanhamento {
  emailAtivo: boolean;
  gestores: string[];
  pausado: boolean;
  situacao: 'DESLIGADO' | 'SEM_GESTOR' | 'AGUARDANDO' | 'ATIVO' | 'PAUSADO' | 'FINAL_PENDENTE' | 'FINALIZADO';
  titulo: string;
  explicacao: string;
  proximoEnvio: string | null;
  /** Uma frase para cada lembrete aos líderes; null sem prazo. */
  lembretes: { tresDias: string; umDia: string } | null;
  historico: ItemAcompanhamento[];
}

/** A página do RH (/acompanhar): cada gestor, quando entrou e quando o acesso foi mandado. */
export interface AcessosRh {
  pagina: string;
  gestores: AcessoDoGestor[];
}

export interface AcessoDoGestor {
  email: string;
  ultimoAcesso: string | null;
  aparelho: string | null;
  acessos: number;
  conviteEnviadoEm: string | null;
}

export interface ItemAcompanhamento {
  id: number;
  tipo: 'DIARIO' | 'FINAL' | 'LEMBRETE';
  manual: boolean;
  quando: string;
  para: string;
  responderam: number | null;
  total: number | null;
  enviado: boolean;
  detalhe: string | null;
  /** Quem clicou em "Enviar agora" (null: automático). */
  autor: string | null;
  /** O que o Resend avisou sobre a entrega (null: sem aviso ainda). */
  entrega: SituacaoEntrega | null;
  entregaDetalhe: string | null;
}

export interface EnvioAcompanhamento {
  enviado: boolean;
  tipo: 'DIARIO' | 'FINAL';
  mensagem: string;
}
