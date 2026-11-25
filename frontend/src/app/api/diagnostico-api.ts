import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { CARGO_OUTRO, DadosRespondente, Diagnostico, PerfilLider, Problema, Resultado } from './modelos';

const API = '/api/public';

@Service()
export class DiagnosticoApi {
  private readonly http = inject(HttpClient);

  /** perfil: só no link de empresa com cargos (null nos demais). */
  iniciar(campanha: string, dados: DadosRespondente, perfil: PerfilLider | null): Promise<Diagnostico> {
    return firstValueFrom(
      this.http.post<Diagnostico>(`${API}/campanhas/${campanha}/diagnosticos`, paraEnvio(dados, perfil)),
    );
  }

  /** Link pessoal: confirma (ou corrige) os dados e começa o diagnóstico do convite. */
  iniciarConvite(token: string, dados: DadosRespondente, perfil: PerfilLider | null): Promise<Diagnostico> {
    return firstValueFrom(
      this.http.post<Diagnostico>(`${API}/convites/${token}/diagnosticos`, paraEnvio(dados, perfil)),
    );
  }

  /** O link pessoal abriu neste aparelho (vai para a auditoria do convite). */
  registrarAcesso(token: string, dispositivo: 'celular' | 'computador'): Promise<unknown> {
    return firstValueFrom(this.http.post(`${API}/convites/${token}/acesso`, { dispositivo }));
  }

  atualizarRespondente(id: string, dados: DadosRespondente, perfil: PerfilLider | null): Promise<Diagnostico> {
    return firstValueFrom(this.http.put<Diagnostico>(`${API}/diagnosticos/${id}/respondente`, paraEnvio(dados, perfil)));
  }

  buscar(id: string): Promise<Diagnostico> {
    return firstValueFrom(this.http.get<Diagnostico>(`${API}/diagnosticos/${id}`));
  }

  responder(id: string, perguntaId: number, opcoes: number[]): Promise<unknown> {
    return firstValueFrom(this.http.put(`${API}/diagnosticos/${id}/respostas/${perguntaId}`, { opcoes }));
  }

  finalizar(id: string): Promise<Resultado> {
    return firstValueFrom(this.http.post<Resultado>(`${API}/diagnosticos/${id}/finalizar`, null));
  }

  urlPdf(id: string): string {
    return `${API}/diagnosticos/${id}/relatorio.pdf`;
  }

  /** Auditoria: o líder compartilhou ou baixou o PDF. Sem esperar e sem atrapalhar se falhar. */
  registrarUsoDoPdf(id: string, acao: 'compartilhou' | 'baixou'): void {
    this.http.post(`${API}/diagnosticos/${id}/pdf-usado`, { acao }).subscribe({ error: () => undefined });
  }
}

function paraEnvio(dados: DadosRespondente, perfil: PerfilLider | null) {
  const outro = perfil?.cargo === CARGO_OUTRO;
  return {
    nome: dados.nome.trim(),
    email: dados.email.trim(),
    whatsapp: dados.whatsapp.replace(/\D/g, ''),
    cargoId: perfil?.cargo && !outro ? Number(perfil.cargo) : null,
    cargoOutro: outro,
    cargoOutroNome: outro ? perfil.outroNome.trim() || null : null,
    tempoCargo: perfil?.tempo ?? null,
  };
}

/** Converte qualquer falha HTTP num Problema com mensagem que o líder entende. */
export function problemaDe(erro: unknown): Problema {
  if (erro instanceof HttpErrorResponse) {
    if (erro.status === 0) {
      return { status: 0, detail: 'Sem conexão com a internet. Confira o sinal e tente de novo.' };
    }
    const corpo = erro.error as Partial<Problema> | null;
    if (corpo && typeof corpo === 'object' && (corpo.detail || corpo.campos)) {
      return { ...corpo, status: erro.status } as Problema;
    }
    return { status: erro.status, detail: 'Algo deu errado do nosso lado. Tente de novo em instantes.' };
  }
  return { status: -1, detail: 'Algo deu errado. Tente de novo.' };
}
