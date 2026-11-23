import { afterRenderEffect, Component, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';

import { Resultado } from '../api/modelos';
import { DiagnosticoApi } from '../api/diagnostico-api';
import { Curva } from '../compartilhado/curva';
import { baixarArquivo } from '../compartilhado/download';
import { FAIXAS } from '../compartilhado/faixas';
import { CreditoDiagnostico, MarcaEmpresaCabecalho } from '../compartilhado/marca-empresa';
import { aplicarTema } from '../compartilhado/tema-marca';
import { semMovimento } from '../compartilhado/util';
import { NaoEncontrado } from '../nao-encontrado';

@Component({
  selector: 'app-resultado-pagina',
  imports: [CreditoDiagnostico, Curva, DatePipe, MarcaEmpresaCabecalho, NaoEncontrado],
  templateUrl: './resultado-pagina.html',
})
export class ResultadoPagina {
  private readonly api = inject(DiagnosticoApi);

  readonly id = input.required<string>();

  protected readonly resultado = httpResource<Resultado>(() => `/api/public/diagnosticos/${this.id()}/resultado`);
  protected readonly faixa = computed(() => {
    const r = this.resultado.value();
    return FAIXAS[r?.faixa.codigo ?? 'PLATO'] ?? FAIXAS['PLATO'];
  });

  /** O índice sobe de 0 até o valor final, acompanhando o ponto na curva. */
  protected readonly indiceExibido = signal(0);
  protected readonly barrasVisiveis = signal(false);
  protected readonly baixando = signal(false);
  protected readonly erroPdf = signal<string | null>(null);
  private consultasEmail = 0;

  /** Diagnóstico do link de uma empresa: as cores dela (a consulta repete enquanto o e-mail sai). */
  private readonly marca = computed(() => (this.resultado.hasValue() ? this.resultado.value().empresa : null), {
    equal: (a, b) => JSON.stringify(a) === JSON.stringify(b),
  });

  constructor() {
    effect((onCleanup) => {
      const marca = this.marca();
      if (marca?.personalizada) onCleanup(aplicarTema(marca.tema));
    });

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
      await baixarArquivo(this.api.urlPdf(r.id), 'diagnostico-carreira.pdf');
    } catch {
      this.erroPdf.set('Não conseguimos gerar o PDF agora. Tente de novo em instantes.');
    } finally {
      this.baixando.set(false);
    }
  }
}
