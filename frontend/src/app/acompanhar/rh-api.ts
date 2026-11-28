import { HttpClient } from '@angular/common/http';
import { inject, Service, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { MarcaEmpresa } from '../compartilhado/tema-marca';

export const API_RH = '/api/acompanhar';

export interface EmpresaDoGestor {
  id: number;
  nome: string;
  /** O código do link da empresa: a página dela é /acompanhar/{codigo} (null: empresa sem link). */
  codigo: string | null;
}

/** O endereço da página de uma empresa (pelo código; sem código, pelo número). */
export function rotaDaEmpresa(e: EmpresaDoGestor): (string | number)[] {
  return ['/acompanhar', e.codigo ?? e.id];
}

export interface GestorRh {
  email: string;
  empresas: EmpresaDoGestor[];
}

export type SituacaoLider = 'COMECOU' | 'ABRIU' | 'NAO_ABRIU' | 'RESPONDEU';

export interface LiderRh {
  id: number;
  nome: string;
  cargo: string | null;
  email: string;
  whatsapp: string | null;
  situacao: SituacaoLider;
  enviadoEm: string | null;
  envios: number;
  primeiroAcessoEm: string | null;
  ultimoAcessoEm: string | null;
  acessos: number;
  respondeuEm: string | null;
  respondidas: number | null;
  perguntas: number | null;
  /** O link pessoal (só de quem falta). */
  link: string | null;
  /** O e-mail voltou ou foi para o spam. */
  alerta: string | null;
  /** PLANILHA, MANUAL ou LINK (começou sozinho pelo link aberto da empresa). */
  origem: 'PLANILHA' | 'MANUAL' | 'LINK';
  /** Já respondeu e começou de novo (respondidas e perguntas são da tentativa nova). */
  respondendoDeNovo: boolean;
}

export interface GrupoRh {
  codigo: SituacaoLider;
  titulo: string;
  dica: string;
  lideres: LiderRh[];
}

export interface ParticipacaoRh {
  total: number;
  responderam: number;
  comecaram: number;
  abriram: number;
  naoAbriram: number;
  /** Quantos entraram pelo link aberto da empresa, sem convite. */
  peloLink: number;
  hoje: number;
  ultimaResposta: string | null;
  inativos: number;
  grupos: GrupoRh[];
}

export interface PainelRh {
  empresa: MarcaEmpresa;
  encerraEm: string | null;
  encerrada: boolean;
  participacao: ParticipacaoRh;
  /** Concluídos, inclusive quem respondeu pelo link aberto (sem convite). */
  concluidos: number;
  agora: string;
}

export interface FaixaRh {
  codigo: string;
  titulo: string;
  leitura: string | null;
  minimo: number;
  maximo: number;
  quantidade: number;
  percentual: number;
  acoes: string[];
}

export interface DimensaoRh {
  codigo: string;
  nome: string;
  media: number;
  menor: number;
  maior: number;
  emAlerta: number;
  fortes: number;
  referencia: number | null;
  acao: string | null;
  destaque: 'forte' | 'fragil' | null;
}

export interface ParteFaixa {
  codigo: string;
  rotulo: string;
  quantidade: number;
  percentual: number;
}

export interface GrupoCargoRh {
  nome: string;
  outros: boolean;
  lideres: number;
  media: number;
  faixaDaMedia: string | null;
  faixas: ParteFaixa[];
  travados: number;
  dimensoes: { media: number | null; faixa: string | null }[];
}

export interface TempoRh {
  rotulo: string;
  lideres: number;
  oculto: boolean;
  media: number | null;
  faixaDaMedia: string | null;
  faixas: ParteFaixa[];
}

export interface PorCargoRh {
  grupos: GrupoCargoRh[];
  maisTravado: GrupoCargoRh | null;
  dimensoes: string[];
  tempos: TempoRh[];
  notaAnonimato: string | null;
  notaSemCargo: string | null;
}

export interface OpcaoRh {
  letra: string;
  texto: string;
  pontos: number;
  quantidade: number;
  percentual: number;
  maisEscolhida: boolean;
}

export interface PerguntaRh {
  numero: number;
  dimensao: string | null;
  enunciado: string;
  respondentes: number;
  indice: number;
  alerta: number;
  opcoes: OpcaoRh[];
}

export interface DadosRh {
  situacao: string | null;
  explicacao: string | null;
  periodo: string;
  questionario: string;
  nota: string | null;
  participacao: { iniciaram: number; concluiram: number; emAndamento: number; taxaConclusao: number; tempoMediano: string };
  indice: { media: number; mediana: number; menor: number; maior: number };
  referencia: { media: number; lideres: number; comparacao: string } | null;
  faixaDaMedia: FaixaRh;
  faixas: FaixaRh[];
  destaques: { codigo: string; titulo: string; texto: string }[];
  /** Um por líder, sem identificação, em ordem de índice. */
  pontos: { indice: number; faixa: string }[];
  limites: { alerta: number; forte: number };
  dimensoes: DimensaoRh[];
  porCargo: PorCargoRh | null;
  perguntas: PerguntaRh[];
  notaVersoes: string | null;
}

/** Um passo da linha do tempo de um líder (só a participação). */
export interface PassoRh {
  tipo: string;
  titulo: string;
  detalhe: string | null;
  quando: string;
  /** A cor do ponto: neutro, envio, alerta ou concluido. */
  tom: 'neutro' | 'envio' | 'alerta' | 'concluido';
  /** A entrega do e-mail desse passo (ENTREGUE, DEVOLVIDO...). */
  entrega: { situacao: string; entregueEm: string | null; atualizadoEm: string | null } | null;
  /** O e-mail de quem do RH fez a ação (cadastro, edição, envio pela página do RH). */
  por: string | null;
}

/** O que a página do RH precisa para gerenciar os líderes da empresa. */
export interface GestaoRh {
  cargos: { id: number; nome: string }[];
  /** O envio de e-mails está ligado (senão, o convite vai pelo WhatsApp). */
  emailAtivo: boolean;
  encerrada: boolean;
  /** Quantos ainda não receberam o convite. */
  naoEnviados: number;
  retirados: RetiradoRh[];
}

export interface RetiradoRh {
  id: number;
  nome: string;
  cargo: string | null;
  /** Não respondeu: o RH pode devolver à lista. */
  podeVoltar: boolean;
}

/** Os dados de um líder para o formulário de edição. */
export interface LiderEditavel {
  id: number;
  nome: string;
  email: string;
  whatsapp: string | null;
  cargoId: number | null;
  cargoOutro: boolean;
  respondeu: boolean;
}

export interface ResultadoRh {
  estado: 'AGUARDANDO' | 'PARCIAL' | 'FINAL';
  concluidos: number;
  minimo: number;
  respostas: number;
  atualizadoEm: string | null;
  novas: number;
  faltamParaAtualizar: number;
  faltamParaAbrir: number;
  dados: DadosRh | null;
}

/**
 * Quem acompanha neste aparelho. O acesso vive num cookie HttpOnly (o JavaScript não lê), lembrado
 * por 30 dias; aqui fica só o e-mail e as empresas. O Angular envia o token CSRF sozinho.
 */
@Service()
export class SessaoRh {
  private readonly http = inject(HttpClient);
  private conferida = false;

  /** undefined: ainda não conferiu; null: ninguém neste aparelho. */
  readonly gestor = signal<GestorRh | null | undefined>(undefined);

  async conferir(forcar = false): Promise<GestorRh | null> {
    if (this.conferida && !forcar) return this.gestor() ?? null;
    const sessao = await firstValueFrom(this.http.get<{ gestor: GestorRh | null }>(`${API_RH}/sessao`));
    this.gestor.set(sessao.gestor);
    this.conferida = true;
    return sessao.gestor;
  }

  /** empresa: o código da página de onde pediu, para o botão do e-mail abrir nela. */
  pedirCodigo(email: string, empresa?: string | null): Promise<unknown> {
    return firstValueFrom(this.http.post(`${API_RH}/codigo`, { email, empresa: empresa ?? null }));
  }

  async entrar(pedido: { email?: string; codigo?: string; link?: string }): Promise<GestorRh> {
    const sessao = await firstValueFrom(this.http.post<{ gestor: GestorRh }>(`${API_RH}/entrar`, pedido));
    this.gestor.set(sessao.gestor);
    this.conferida = true;
    return sessao.gestor;
  }

  async sair(): Promise<void> {
    try {
      await firstValueFrom(this.http.post(`${API_RH}/sair`, null));
    } finally {
      this.encerrada();
    }
  }

  /** O servidor respondeu 401: o acesso venceu ou foi retirado. */
  encerrada() {
    this.gestor.set(null);
    this.conferida = true;
  }
}

/** "(11) 98765-4321" ou "11987654321" → "5511987654321" (o wa.me pede o país). */
export function numeroWhatsapp(bruto: string | null | undefined): string | null {
  if (!bruto) return null;
  if (bruto.trim().startsWith('+')) {
    const internacional = bruto.replace(/\D/g, '');
    return internacional.length >= 8 ? internacional : null;
  }
  const d = bruto.replace(/\D/g, '');
  if ((d.length === 12 || d.length === 13) && d.startsWith('55')) return d;
  if (d.length === 10 || d.length === 11) return `55${d}`;
  return null;
}

export function primeiroNome(nome: string): string {
  return nome.trim().split(/\s+/)[0] ?? nome;
}
