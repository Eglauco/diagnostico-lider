import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { DadosRespondente, Diagnostico, Problema, Resultado } from './modelos';

const API = '/api/public';

@Service()
export class DiagnosticoApi {
  private readonly http = inject(HttpClient);

  iniciar(campanha: string, dados: DadosRespondente): Promise<Diagnostico> {
    return firstValueFrom(this.http.post<Diagnostico>(`${API}/campanhas/${campanha}/diagnosticos`, paraEnvio(dados)));
  }

  atualizarRespondente(id: string, dados: DadosRespondente): Promise<Diagnostico> {
    return firstValueFrom(this.http.put<Diagnostico>(`${API}/diagnosticos/${id}/respondente`, paraEnvio(dados)));
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
}

function paraEnvio(dados: DadosRespondente): DadosRespondente {
  return {
    nome: dados.nome.trim(),
    email: dados.email.trim(),
    whatsapp: dados.whatsapp.replace(/\D/g, ''),
    empresa: dados.empresa.trim(),
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
