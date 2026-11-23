import { afterRenderEffect, Component, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';

import { Resultado } from '../api/modelos';
import { DiagnosticoApi } from '../api/diagnostico-api';
import { Curva } from '../compartilhado/curva';
import { buscarArquivo, compartilharArquivo, podeCompartilharArquivos, salvarBlob } from '../compartilhado/download';
import { FAIXAS } from '../compartilhado/faixas';
import { CreditoDiagnostico, MarcaEmpresaCabecalho } from '../compartilhado/marca-empresa';
import { aplicarTema } from '../compartilhado/tema-marca';
import { semMovimento, vibrar } from '../compartilhado/util';
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

  /** No celular, o botão principal manda o PDF pela folha de compartilhar do sistema (WhatsApp, e-mail, Arquivos). */
  protected readonly podeCompartilhar = podeCompartilharArquivos();
  protected readonly preparando = signal(false);
  /** O PDF ficou pronto depois que o toque "expirou": um segundo toque abre a folha na hora. */
  protected readonly tocarDeNovo = signal(false);
  /** Confirmação breve depois que a folha de compartilhar fecha com o envio feito. */
  protected readonly compartilhado = signal(false);
  protected readonly android = /android/i.test(navigator.userAgent);
  private pdf: Promise<File> | null = null;
  private timerCompartilhado = 0;
  private pdfAdiantado = false;

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

    // No celular, o PDF já vem sendo preparado enquanto a pessoa lê: assim o "Compartilhar" abre
    // a folha do sistema no mesmo toque (o navegador só permite isso logo depois do toque).
    effect((onCleanup) => {
      if (!this.podeCompartilhar || this.pdfAdiantado || !this.resultado.hasValue()) return;
      const timer = setTimeout(() => {
        this.pdfAdiantado = true;
        this.prepararPdf().catch(() => undefined);
      }, 1500);
      onCleanup(() => clearTimeout(timer));
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

  /** Gera (uma vez) o PDF do diagnóstico; serve tanto para compartilhar quanto para baixar. */
  private prepararPdf(): Promise<File> {
    this.pdf ??= buscarArquivo(this.api.urlPdf(this.id()), 'diagnostico-carreira.pdf').catch((erro: unknown) => {
      this.pdf = null;
      throw erro;
    });
    return this.pdf;
  }

  protected async compartilharPdf() {
    const r = this.resultado.value();
    if (!r || this.preparando()) return;
    this.erroPdf.set(null);
    this.compartilhado.set(false);
    this.preparando.set(true);
    let arquivo: File;
    try {
      arquivo = await this.prepararPdf();
    } catch {
      this.erroPdf.set('Não conseguimos gerar o PDF agora. Tente de novo em instantes.');
      return;
    } finally {
      this.preparando.set(false);
    }
    const segundoToque = this.tocarDeNovo();
    try {
      const resultado = await compartilharArquivo(arquivo, `Diagnóstico de carreira de ${r.nome}`);
      if (resultado === 'compartilhado') this.confirmarCompartilhado();
      if (resultado !== 'precisa-de-toque') this.tocarDeNovo.set(false);
      // Recusou mesmo com o arquivo pronto: o navegador não deixa compartilhar daqui, então baixa.
      else if (segundoToque) this.salvar(arquivo);
      else this.tocarDeNovo.set(true);
    } catch {
      this.salvar(arquivo);
    }
  }

  protected async baixarPdf() {
    if (!this.resultado.hasValue() || this.baixando()) return;
    this.baixando.set(true);
    this.erroPdf.set(null);
    try {
      this.salvar(await this.prepararPdf());
    } catch {
      this.erroPdf.set('Não conseguimos gerar o PDF agora. Tente de novo em instantes.');
    } finally {
      this.baixando.set(false);
    }
  }

  private confirmarCompartilhado() {
    vibrar(12);
    this.compartilhado.set(true);
    clearTimeout(this.timerCompartilhado);
    this.timerCompartilhado = window.setTimeout(() => this.compartilhado.set(false), 4000);
  }

  private salvar(arquivo: File) {
    this.tocarDeNovo.set(false);
    salvarBlob(arquivo, arquivo.name);
  }
}
