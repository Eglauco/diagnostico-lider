import { HttpClient } from '@angular/common/http';
import { Component, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { firstValueFrom } from 'rxjs';

import { NovoConvite } from '../admin/convites/novo-convite';
import { RevisaoImportacao } from '../admin/convites/revisao-importacao';
import { Importacao } from '../admin/admin-modelos';
import { problemaDe } from '../api/diagnostico-api';
import { baixarArquivo } from '../compartilhado/download';
import { API_RH, GestaoRh } from './rh-api';

export interface AvisoRh {
  tipo: 'ok' | 'erro';
  texto: string;
  /** Um botão no aviso (ex.: "Desfazer"). */
  acao?: { rotulo: string; fazer: () => void };
}

/**
 * Os botões de gestão da página do RH: cadastrar um líder, importar a planilha (com a mesma
 * revisão linha a linha do painel) e enviar os convites de quem ainda não recebeu, com
 * confirmação e a prévia do e-mail. Quem respondeu não muda daqui (o servidor trava).
 */
@Component({
  selector: 'app-gestao-lideres-rh',
  imports: [NovoConvite, RevisaoImportacao],
  host: { class: 'block' },
  styleUrl: './acompanhar.css',
  styles: `
    .dialogo-rh {
      width: min(100% - 32px, 34rem);
      max-height: 92dvh;
      margin: auto;
      padding: 0;
      border: 0;
      border-radius: 24px;
      background: #fff;
      color: var(--color-abeto);
      box-shadow: 0 30px 80px -30px rgb(15 43 38 / 0.55);
    }
    .dialogo-rh[open] {
      animation: aparecer 240ms ease-out;
    }
    .dialogo-rh::backdrop {
      background: rgb(15 43 38 / 0.42);
      backdrop-filter: blur(2px);
    }
    .previa {
      width: 100%;
      height: 360px;
      border: 0;
      border-radius: 16px;
      background: var(--color-nevoa);
    }
  `,
  template: `
    <!-- Cadastrar e importar. -->
    <div class="mt-4 grid grid-cols-2 gap-2 sm:flex sm:flex-wrap sm:items-center">
      <button type="button" class="botao-secundario min-h-12 bg-white px-4 text-[15px]" (click)="adicionando.set(true)">
        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" aria-hidden="true"><path d="M12 5v14M5 12h14" stroke-linecap="round" /></svg>
        <span>Adicionar líder</span>
      </button>
      <button type="button" class="botao-secundario min-h-12 bg-white px-4 text-[15px]" aria-label="Importar planilha" [attr.aria-busy]="lendo()" [disabled]="lendo()" (click)="arquivo.click()">
        @if (lendo()) {
          <span class="size-4 animate-spin rounded-full border-2 border-linha border-t-abeto" aria-hidden="true"></span>
          <span>Lendo…</span>
        } @else {
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" aria-hidden="true"><path d="M12 15V4m0 0l-4.5 4.5M12 4l4.5 4.5M5 19.5h14" stroke-linecap="round" stroke-linejoin="round" /></svg>
          <!-- Em telas estreitas, "Importar" (o nome acessível segue completo). -->
          <span>Importar<span class="hidden min-[400px]:inline"> planilha</span></span>
        }
      </button>
      <button type="button" class="col-span-2 inline-flex min-h-11 items-center justify-center gap-1.5 px-2 text-[14px] font-semibold text-abeto-2 underline decoration-linha underline-offset-4 hover:text-abeto sm:col-span-1" (click)="baixarModelo()">
        Baixar a planilha modelo
      </button>
      <input #arquivo type="file" class="sr-only" accept=".xlsx,.csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/csv" tabindex="-1" aria-hidden="true" (change)="aoEscolher($event)" />
    </div>

    <!-- Quem ainda não recebeu o convite: um toque para enviar. -->
    @if (gestao(); as g) {
      @if (g.naoEnviados > 0 && !g.encerrada) {
        <div class="aparecer mt-4 flex flex-col gap-3 rounded-[20px] bg-white p-4 ring-1 ring-linha sm:flex-row sm:items-center">
          <div class="flex min-w-0 flex-1 items-start gap-3 sm:items-center">
            <span class="grid size-10 shrink-0 place-items-center rounded-2xl bg-acao text-sobre-acao" aria-hidden="true">
              <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="5" width="18" height="14" rx="2.5" /><path d="M4 7l8 6 8-6" stroke-linecap="round" stroke-linejoin="round" /></svg>
            </span>
            <p class="min-w-0 flex-1 text-[15px] leading-snug">
              <strong class="font-semibold">{{ g.naoEnviados === 1 ? '1 líder ainda não recebeu o convite.' : g.naoEnviados + ' líderes ainda não receberam o convite.' }}</strong>
              <span class="block text-[14px] text-abeto-2">
                {{ g.emailAtivo ? (g.naoEnviados === 1 ? 'Recebe' : 'Cada um recebe') + ' o link pessoal por e-mail, com o nome da ' + empresa() + '.' : 'O envio de e-mail está desligado: mande o link pelo WhatsApp.' }}
              </span>
            </p>
          </div>
          @if (g.emailAtivo) {
            <button type="button" class="botao-primario min-h-12 w-full shrink-0 px-5 text-[15px] sm:w-auto" (click)="confirmarEnvio()">
              Enviar {{ g.naoEnviados === 1 ? 'o convite' : g.naoEnviados + ' convites' }}
            </button>
          }
        </div>
      }
    }

    <!-- Confirmação do envio, com a prévia do e-mail. -->
    <dialog #confirmacao class="dialogo-rh" aria-labelledby="envio-titulo" (close)="previa.set(null)">
      <div class="p-6">
        <h2 id="envio-titulo" class="display text-[26px] leading-tight font-bold">
          Enviar {{ (gestao()?.naoEnviados ?? 0) === 1 ? 'o convite' : (gestao()?.naoEnviados ?? 0) + ' convites' }}?
        </h2>
        <p class="mt-2 text-[15px] leading-snug text-abeto-2">
          Cada líder recebe por e-mail o link pessoal, com os dados já preenchidos e o nome da {{ empresa() }}. Quem já recebeu não recebe de novo.
        </p>
        @if (previa(); as html) {
          <iframe class="previa mt-4" title="Prévia do e-mail de convite" [srcdoc]="html"></iframe>
        } @else {
          <button type="button" class="mt-3 inline-flex min-h-11 items-center gap-2 text-[15px] font-semibold text-abeto underline decoration-linha underline-offset-4" [attr.aria-busy]="carregandoPrevia()" (click)="verPrevia()">
            {{ carregandoPrevia() ? 'Abrindo o e-mail…' : 'Ver o e-mail que eles recebem' }}
          </button>
        }
        @if (erroEnvio(); as e) {
          <p class="aparecer mt-3 text-[15px] font-medium text-travada" role="alert">{{ e }}</p>
        }
        <div class="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button type="button" class="botao-secundario" (click)="confirmacao.close()">Cancelar</button>
          <button type="button" class="botao-primario" [attr.aria-busy]="enviando()" [disabled]="enviando()" (click)="enviar()">
            {{ enviando() ? 'Enviando…' : 'Enviar agora' }}
          </button>
        </div>
      </div>
    </dialog>

    @if (adicionando()) {
      <app-novo-convite
        [empresaId]="empresaId()"
        [cargos]="gestao()?.cargos ?? []"
        [destino]="api() + '/lideres'"
        (adicionado)="adicionado($event)"
        (fechou)="adicionando.set(false)"
      />
    }

    @if (importacao(); as imp) {
      <app-revisao-importacao [empresaId]="empresaId()" [importacao]="imp" [destino]="api() + '/lideres'" (criados)="criados($event)" (fechou)="importacao.set(null)" />
    }
  `,
})
export class GestaoLideresRh {
  private readonly http = inject(HttpClient);
  private readonly sanitizador = inject(DomSanitizer);

  readonly empresaId = input.required<number>();
  readonly empresa = input('');
  readonly gestao = input<GestaoRh | undefined>();

  /** Algo mudou (líder novo, convites enviados): a página recarrega a lista. */
  readonly mudou = output<void>();
  readonly aviso = output<AvisoRh>();

  private readonly confirmacao = viewChild.required<ElementRef<HTMLDialogElement>>('confirmacao');

  protected readonly adicionando = signal(false);
  protected readonly importacao = signal<Importacao | null>(null);
  protected readonly lendo = signal(false);
  protected readonly enviando = signal(false);
  protected readonly erroEnvio = signal<string | null>(null);
  protected readonly previa = signal<SafeHtml | null>(null);
  protected readonly carregandoPrevia = signal(false);

  protected api() {
    return `${API_RH}/empresas/${this.empresaId()}`;
  }

  protected adicionado(nome: string) {
    this.mudou.emit();
    this.aviso.emit({
      tipo: 'ok',
      texto: `${nome} entrou na lista. ${this.gestao()?.emailAtivo ? 'Envie o convite quando quiser.' : ''}`.trim(),
    });
  }

  protected criados(total: number) {
    this.importacao.set(null);
    this.mudou.emit();
    const envio = this.gestao()?.emailAtivo ? ` Envie ${total === 1 ? 'o convite' : 'os convites'} quando quiser.` : '';
    this.aviso.emit({ tipo: 'ok', texto: `${total} ${total === 1 ? 'líder entrou' : 'líderes entraram'} na lista.${envio}` });
  }

  protected aoEscolher(evento: Event) {
    const campo = evento.target as HTMLInputElement;
    const arquivo = campo.files?.[0];
    campo.value = '';
    if (arquivo) void this.ler(arquivo);
  }

  private async ler(arquivo: File) {
    if (!/\.(xlsx|csv)$/i.test(arquivo.name)) {
      this.aviso.emit({ tipo: 'erro', texto: 'Envie a planilha modelo preenchida (.xlsx) ou um arquivo .csv.' });
      return;
    }
    this.lendo.set(true);
    const corpo = new FormData();
    corpo.append('arquivo', arquivo, arquivo.name);
    try {
      this.importacao.set(await firstValueFrom(this.http.post<Importacao>(`${this.api()}/lideres/importar`, corpo)));
    } catch (e) {
      this.aviso.emit({ tipo: 'erro', texto: problemaDe(e).detail ?? 'Não foi possível ler a planilha.' });
    } finally {
      this.lendo.set(false);
    }
  }

  protected async baixarModelo() {
    try {
      await baixarArquivo(`${this.api()}/lideres/modelo.xlsx`, 'planilha-de-lideres.xlsx');
    } catch {
      this.aviso.emit({ tipo: 'erro', texto: 'Não foi possível baixar a planilha agora. Tente de novo.' });
    }
  }

  protected confirmarEnvio() {
    this.erroEnvio.set(null);
    this.confirmacao().nativeElement.showModal();
  }

  protected async verPrevia() {
    this.carregandoPrevia.set(true);
    try {
      const html = await firstValueFrom(this.http.get(`${this.api()}/lideres/email-previa`, { responseType: 'text' }));
      this.previa.set(this.sanitizador.bypassSecurityTrustHtml(html));
    } catch {
      this.erroEnvio.set('Não foi possível abrir a prévia agora.');
    } finally {
      this.carregandoPrevia.set(false);
    }
  }

  protected async enviar() {
    if (this.enviando()) return;
    this.enviando.set(true);
    this.erroEnvio.set(null);
    try {
      const r = await firstValueFrom(this.http.post<{ enviando: number }>(`${this.api()}/lideres/enviar`, null));
      this.confirmacao().nativeElement.close();
      this.aviso.emit({
        tipo: 'ok',
        texto: `${r.enviando === 1 ? 'O convite está saindo' : r.enviando + ' convites estão saindo'}. Em instantes aparecem como enviados.`,
      });
      // O envio é em segundo plano: a lista se atualiza logo depois.
      this.mudou.emit();
      setTimeout(() => this.mudou.emit(), 4000);
    } catch (e) {
      this.erroEnvio.set(problemaDe(e).detail ?? 'Não foi possível enviar agora. Tente de novo.');
    } finally {
      this.enviando.set(false);
    }
  }
}
