import { afterRenderEffect, Component, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';

import { CodigoFaixa, Resultado } from '../api/modelos';
import { DiagnosticoApi } from '../api/diagnostico-api';
import { Curva } from '../compartilhado/curva';
import { Marca } from '../compartilhado/marca';
import { semMovimento } from '../compartilhado/util';
import { NaoEncontrado } from '../nao-encontrado';

const FAIXAS: Record<CodigoFaixa, { cor: string; suave: string }> = {
  TRAVADA: { cor: '#C2364B', suave: '#F8E6E9' },
  PLATO: { cor: '#A86A00', suave: '#FBF0DC' },
  ASCENSAO: { cor: '#1F8A5B', suave: '#E2F2EA' },
};

@Component({
  selector: 'app-resultado-pagina',
  imports: [Curva, DatePipe, Marca, NaoEncontrado],
  templateUrl: './resultado-pagina.html',
})
export class ResultadoPagina {
  private readonly api = inject(DiagnosticoApi);

  readonly id = input.required<string>();

  protected readonly resultado = httpResource<Resultado>(() => `/api/public/diagnosticos/${this.id()}/resultado`);
  protected readonly faixa = computed(() => {
    const r = this.resultado.value();
    return r ? FAIXAS[r.faixa.codigo] : FAIXAS.PLATO;
  });

  /** O índice sobe de 0 até o valor final, acompanhando o ponto na curva. */
  protected readonly indiceExibido = signal(0);
  protected readonly barrasVisiveis = signal(false);
  protected readonly baixando = signal(false);
  protected readonly erroPdf = signal<string | null>(null);
  private consultasEmail = 0;

  constructor() {
    afterRenderEffect(() => {
      const r = this.resultado.value();
      if (!r || this.barrasVisiveis()) return;
      this.animarIndice(r.pontuacao);
      setTimeout(() => this.barrasVisiveis.set(true), 300);
    });

    // O e-mail sai em segundo plano: acompanha até ele ser enviado.
    effect((onCleanup) => {
      const r = this.resultado.value();
      if (r?.statusEmail !== 'PENDENTE' || this.consultasEmail >= 12) return;
      const timer = setTimeout(() => {
        this.consultasEmail++;
        this.resultado.reload();
      }, 2500);
      onCleanup(() => clearTimeout(timer));
    });
  }

  private animarIndice(final: number) {
    if (semMovimento()) {
      this.indiceExibido.set(final);
      return;
    }
    const inicio = performance.now() + 200;
    const duracao = 1500;
    const passo = (agora: number) => {
      const p = Math.max(0, Math.min(1, (agora - inicio) / duracao));
      const suave = p < 0.5 ? 4 * p ** 3 : 1 - (-2 * p + 2) ** 3 / 2;
      this.indiceExibido.set(Math.round(final * suave));
      if (p < 1) requestAnimationFrame(passo);
    };
    requestAnimationFrame(passo);
  }

  protected async baixarPdf() {
    const r = this.resultado.value();
    if (!r || this.baixando()) return;
    this.baixando.set(true);
    this.erroPdf.set(null);
    try {
      const resposta = await fetch(this.api.urlPdf(r.id));
      if (!resposta.ok) throw new Error(String(resposta.status));
      const arquivo = URL.createObjectURL(await resposta.blob());
      const link = document.createElement('a');
      link.href = arquivo;
      link.download = nomeArquivo(resposta.headers.get('Content-Disposition'), r.primeiroNome);
      document.body.append(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(arquivo), 30_000);
    } catch {
      this.erroPdf.set('Não conseguimos gerar o PDF agora. Tente de novo em instantes.');
    } finally {
      this.baixando.set(false);
    }
  }
}

function nomeArquivo(disposicao: string | null, primeiroNome: string): string {
  const doServidor = disposicao?.match(/filename="([^"]+)"/)?.[1];
  if (doServidor) return doServidor;
  const base = primeiroNome
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .replace(/[^A-Za-z0-9]+/g, '-')
    .toLowerCase();
  return `diagnostico-carreira-${base || 'lider'}.pdf`;
}
