import { Component, computed, ElementRef, inject, input, signal, viewChild } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { problemaDe } from '../../api/diagnostico-api';
import { quando } from '../../compartilhado/formatos';
import { AcessosRh, API_ADMIN, EnvioAcompanhamento, ItemAcompanhamento, PainelAcompanhamento } from '../admin-modelos';

const SELO: Record<PainelAcompanhamento['situacao'], { texto: string; classe: string }> = {
  ATIVO: { texto: 'Ligado', classe: 'bg-ascensao-suave text-[#17714A]' },
  PAUSADO: { texto: 'Pausado', classe: 'bg-[#FFF3DA] text-[#7A4D00]' },
  AGUARDANDO: { texto: 'Aguardando convites', classe: 'bg-nevoa-2 text-abeto-2 ring-1 ring-linha' },
  SEM_GESTOR: { texto: 'Sem gestor', classe: 'bg-nevoa-2 text-abeto-2 ring-1 ring-linha' },
  DESLIGADO: { texto: 'Desligado', classe: 'bg-nevoa-2 text-abeto-2 ring-1 ring-linha' },
  FINAL_PENDENTE: { texto: 'Enviando o resultado', classe: 'bg-[#E8EEF9] text-[#1F4A9A]' },
  FINALIZADO: { texto: 'Resultado enviado', classe: 'bg-ascensao-suave text-[#17714A]' },
};

const TITULO_ITEM: Record<ItemAcompanhamento['tipo'], string> = {
  DIARIO: 'Relatório diário',
  FINAL: 'Resultado final',
  LEMBRETE: 'Lembrete aos líderes',
};

/**
 * "Relatórios para o gestor": o relatório de participação que sai todo dia útil às 8h, o
 * resultado consolidado no fim e os lembretes aos líderes antes do prazo. Prévia, envio na hora,
 * pausa e o histórico do que saiu.
 */
@Component({
  selector: 'app-cartao-acompanhamento',
  imports: [RouterLink],
  host: { class: 'block' },
  styleUrl: '../admin.css',
  template: `
    <section class="cartao p-5 sm:p-6" aria-labelledby="titulo-acompanhamento">
      @if (painel.hasValue()) {
        @let p = painel.value();
        <div class="flex items-start gap-3.5">
          <span class="grid size-11 shrink-0 place-items-center rounded-2xl bg-nevoa text-abeto" aria-hidden="true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9"><rect x="3.5" y="5" width="17" height="15" rx="2.5" /><path d="M3.5 9.5h17M8 3v4M16 3v4" stroke-linecap="round" /><path d="M8.5 14.5l2.2 2 4.8-4.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
          </span>
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1.5">
              <h2 id="titulo-acompanhamento" class="text-[17px] leading-snug font-semibold">Relatórios para o gestor</h2>
              <span class="inline-flex min-h-7 items-center rounded-full px-2.5 text-[13px] font-semibold" [class]="selo().classe">{{ selo().texto }}</span>
            </div>
            <p class="mt-1 text-[14px] text-abeto-2">
              <strong class="font-semibold text-abeto">{{ p.titulo }}.</strong> {{ p.explicacao }}
            </p>
            @if (p.gestores.length) {
              <p class="mt-2 flex flex-wrap items-center gap-x-2 text-[14px] text-abeto-2">
                <span class="break-all">Para {{ p.gestores.join(', ') }}</span>
                <a [routerLink]="['/administrador/empresas', empresaId(), 'editar']" fragment="gestores-secao" class="inline-flex min-h-12 items-center font-semibold text-abeto underline decoration-linha underline-offset-4 hover:decoration-abeto">Mudar</a>
              </p>
            }
          </div>
        </div>

        @if (aviso(); as a) {
          <p class="aparecer mt-4 rounded-2xl px-4 py-3 text-[15px]" [class]="a.ok ? 'bg-ascensao-suave text-[#17714A]' : 'bg-[#FDEDEF] text-travada'" [attr.role]="a.ok ? 'status' : 'alert'">{{ a.texto }}</p>
        }

        <div class="mt-4 flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          @if (p.situacao === 'SEM_GESTOR') {
            <a [routerLink]="['/administrador/empresas', empresaId(), 'editar']" fragment="gestores-secao" class="botao-primario">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M12 5v14M5 12h14" stroke-linecap="round" /></svg>
              Cadastrar gestor
            </a>
          } @else if (p.situacao !== 'DESLIGADO') {
            <button type="button" class="botao-primario" [attr.aria-busy]="enviando()" [disabled]="enviando()" (click)="enviarAgora()">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" aria-hidden="true"><path d="M4 12l16-8-6 16-2.5-6.5L4 12z" stroke-linejoin="round" /></svg>
              {{ enviando() ? 'Enviando…' : textoEnviar() }}
            </button>
          }
          <button type="button" class="botao-secundario" [attr.aria-busy]="abrindo()" (click)="verEmail('gestor')">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M2.5 12s3.5-6.5 9.5-6.5S21.5 12 21.5 12s-3.5 6.5-9.5 6.5S2.5 12 2.5 12z" /><circle cx="12" cy="12" r="2.8" /></svg>
            Ver o e-mail
          </button>
          @if (p.situacao === 'ATIVO' || p.situacao === 'PAUSADO' || p.situacao === 'AGUARDANDO') {
            <button type="button" class="botao-secundario" [attr.aria-busy]="pausando()" [disabled]="pausando()" (click)="pausar(!p.pausado)">
              @if (p.pausado) {
                <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5.5v13l11-6.5-11-6.5z" /></svg>
                Retomar o diário
              } @else {
                <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><rect x="6.5" y="5.5" width="4" height="13" rx="1" /><rect x="13.5" y="5.5" width="4" height="13" rx="1" /></svg>
                Pausar o diário
              }
            </button>
          }
        </div>

        @if (p.gestores.length && acessos.hasValue()) {
          @let a = acessos.value();
          <div class="mt-5 border-t border-linha pt-4">
            <h3 class="text-[15px] font-semibold">Acompanhamento ao vivo</h3>
            <p class="mt-1 text-[14px] text-abeto-2">
              Os gestores também veem a participação e o resultado do grupo, sem nenhuma resposta individual, na página desta empresa:
              <span class="font-semibold break-all text-abeto">{{ semProtocolo(a.pagina) }}</span>. O endereço abre direto nela, mesmo para quem
              acompanha mais de uma. Entram com o próprio e-mail: o código chega na hora, sem senha.
            </p>
            <ul class="mt-3 space-y-2">
              @for (g of a.gestores; track g.email) {
                <li class="flex flex-col gap-2 rounded-2xl bg-nevoa-2 px-4 py-3 sm:flex-row sm:items-center">
                  <div class="min-w-0 flex-1">
                    <p class="text-[14.5px] font-semibold break-all">{{ g.email }}</p>
                    <p class="text-[13.5px] text-abeto-2">
                      @if (g.ultimoAcesso) {
                        Último acesso {{ minuscula(quando(g.ultimoAcesso)) }}{{ aparelho(g.aparelho) ? ' · ' + aparelho(g.aparelho) : '' }}
                      } @else {
                        Ainda não entrou
                      }
                      @if (g.conviteEnviadoEm) {
                        · acesso mandado {{ minuscula(quando(g.conviteEnviadoEm)) }}
                      }
                    </p>
                  </div>
                  <button type="button" class="botao-secundario min-h-11 shrink-0 px-4 text-[14px]" [attr.aria-busy]="mandando() === g.email" [disabled]="!!mandando()" (click)="mandarAcesso(g.email)">
                    @if (mandando() === g.email) {
                      <span class="size-4 animate-spin rounded-full border-2 border-linha border-t-abeto" aria-hidden="true"></span>
                      Mandando…
                    } @else {
                      {{ g.conviteEnviadoEm ? 'Mandar de novo' : 'Mandar acesso' }}
                    }
                  </button>
                </li>
              }
            </ul>
            <div class="mt-2 flex flex-wrap gap-x-4">
              <button type="button" class="inline-flex min-h-11 items-center gap-2 text-[15px] font-semibold text-abeto hover:underline" (click)="verEmail('acesso')">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M2.5 12s3.5-6.5 9.5-6.5S21.5 12 21.5 12s-3.5 6.5-9.5 6.5S2.5 12 2.5 12z" /><circle cx="12" cy="12" r="2.8" /></svg>
                Ver o e-mail de acesso
              </button>
              <button type="button" class="inline-flex min-h-11 items-center gap-2 text-[15px] font-semibold text-abeto hover:underline" (click)="copiarPagina(a.pagina)">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="8" y="8" width="12" height="12" rx="2.5" /><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2" /></svg>
                {{ paginaCopiada() ? 'Endereço copiado' : 'Copiar o endereço' }}
              </button>
              <a [href]="a.pagina" target="_blank" rel="noopener" class="inline-flex min-h-11 items-center gap-1.5 text-[15px] font-semibold text-abeto hover:underline">
                Abrir a página <span aria-hidden="true">↗</span><span class="sr-only">(abre em nova aba)</span>
              </a>
            </div>
          </div>
        }

        @if (p.lembretes; as l) {
          <div class="mt-5 border-t border-linha pt-4">
            <h3 class="text-[15px] font-semibold">Lembretes aos líderes que faltam</h3>
            <p class="mt-1 text-[14px] text-abeto-2">Saem sozinhos, com o link pessoal de cada um, para quem recebeu o convite e ainda não respondeu.</p>
            <dl class="mt-3 grid gap-2 text-[14px] sm:grid-cols-2">
              <div class="rounded-2xl bg-nevoa-2 px-4 py-3">
                <dt class="font-semibold">3 dias antes do prazo</dt>
                <dd class="mt-0.5 text-abeto-2">{{ l.tresDias }}</dd>
              </div>
              <div class="rounded-2xl bg-nevoa-2 px-4 py-3">
                <dt class="font-semibold">1 dia antes do prazo</dt>
                <dd class="mt-0.5 text-abeto-2">{{ l.umDia }}</dd>
              </div>
            </dl>
            <button type="button" class="mt-2 inline-flex min-h-11 items-center gap-2 text-[15px] font-semibold text-abeto hover:underline" (click)="verEmail('lembrete')">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M2.5 12s3.5-6.5 9.5-6.5S21.5 12 21.5 12s-3.5 6.5-9.5 6.5S2.5 12 2.5 12z" /><circle cx="12" cy="12" r="2.8" /></svg>
              Ver o lembrete
            </button>
          </div>
        }

        @if (p.historico.length) {
          <details class="historico mt-5 border-t border-linha pt-2">
            <summary class="flex min-h-12 cursor-pointer list-none items-center justify-between gap-3 text-[15px] font-semibold">
              Histórico de envios <span class="text-[14px] font-normal text-abeto-2">{{ p.historico.length }} {{ p.historico.length === 1 ? 'envio' : 'envios' }}</span>
              <svg class="seta ml-auto shrink-0 text-abeto-3" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round" /></svg>
            </summary>
            <ol class="mt-1 space-y-2">
              @for (item of p.historico; track item.id) {
                <li class="flex items-start gap-3 rounded-2xl px-1 py-2">
                  <span class="mt-1 grid size-6 shrink-0 place-items-center rounded-full" [class]="item.enviado ? 'bg-ascensao-suave text-[#17714A]' : 'bg-[#FDEDEF] text-travada'" aria-hidden="true">
                    @if (item.enviado) {
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
                    } @else {
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><path d="M7 7l10 10M17 7L7 17" stroke-linecap="round" /></svg>
                    }
                  </span>
                  <div class="min-w-0 flex-1 text-[14px]">
                    <p class="font-semibold text-abeto">
                      {{ tituloItem(item) }}@if (item.manual) {<span class="font-normal text-abeto-2"> · {{ item.autor ? 'enviado por ' + item.autor : 'enviado no painel' }}</span>}
                    </p>
                    <p class="break-words text-abeto-2">{{ quando(item.quando) }} · {{ item.para }}</p>
                    @if (item.total !== null && item.responderam !== null) {
                      <p class="text-abeto-2">{{ item.responderam }} de {{ item.total }} já tinham respondido</p>
                    }
                    @if (!item.enviado) {
                      <p class="font-medium text-travada">Não saiu: {{ item.detalhe }}</p>
                    } @else if (item.entrega === 'ENTREGUE') {
                      <p class="text-[#17714A]">Entregue</p>
                    } @else if (item.entrega && item.entrega !== 'ENVIADO') {
                      <p class="font-medium" [class]="item.entrega === 'ATRASADO' ? 'text-[#7A4D00]' : 'text-travada'">{{ item.entregaDetalhe }}</p>
                    } @else if (item.tipo === 'LEMBRETE' && item.detalhe) {
                      <p class="text-abeto-2">{{ item.detalhe }}</p>
                    }
                  </div>
                </li>
              }
            </ol>
          </details>
        }
      } @else if (painel.error()) {
        <p class="text-[15px] text-abeto-2" role="alert">Não foi possível carregar os relatórios para o gestor.</p>
      } @else {
        <div class="h-28 animate-pulse rounded-2xl bg-nevoa-2" aria-hidden="true"></div>
      }
    </section>

    <dialog #dialogo class="dialogo dialogo-largo dialogo-medio" aria-labelledby="gestor-email-titulo" (close)="html.set(null)">
      <div class="flex flex-col">
        <header class="flex items-start justify-between gap-4 border-b border-linha px-5 pt-5 pb-4 sm:px-7">
          <div>
            <h2 id="gestor-email-titulo" class="display text-[24px] leading-tight font-bold">{{ previa() === 'lembrete' ? 'Lembrete ao líder' : previa() === 'acesso' ? 'Acesso ao acompanhamento' : 'E-mail para o gestor' }}</h2>
            <p class="mt-1 text-[14px] text-abeto-2">
              {{ previa() === 'lembrete' ? 'Como chega para quem ainda não respondeu, com o link pessoal dele.' : previa() === 'acesso' ? 'O botão entra direto na página da empresa (vale 7 dias, uma vez).' : 'Como sairia agora. Os anexos (PDF e Excel) vão junto.' }}
            </p>
          </div>
          <button type="button" class="-mt-1 -mr-2 grid size-11 shrink-0 place-items-center rounded-full text-abeto-2 hover:bg-nevoa hover:text-abeto" aria-label="Fechar" (click)="dialogo.close()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" /></svg>
          </button>
        </header>
        @if (html(); as conteudo) {
          <iframe class="min-h-0 w-full flex-1 bg-nevoa" [title]="previa() === 'lembrete' ? 'Prévia do lembrete ao líder' : previa() === 'acesso' ? 'Prévia do e-mail de acesso' : 'Prévia do e-mail para o gestor'" sandbox="" [srcdoc]="conteudo"></iframe>
        }
      </div>
    </dialog>
  `,
  styles: `
    .historico summary::-webkit-details-marker {
      display: none;
    }
    .historico .seta {
      transition: transform 160ms;
    }
    .historico[open] .seta {
      transform: rotate(180deg);
    }
    @media (prefers-reduced-motion: reduce) {
      .historico .seta {
        transition: none;
      }
    }
  `,
})
export class CartaoAcompanhamento {
  private readonly http = inject(HttpClient);
  private readonly sanitizador = inject(DomSanitizer);
  private readonly caixaEmail = viewChild<ElementRef<HTMLDialogElement>>('dialogo');

  readonly empresaId = input.required<number>();
  /** Muda quando a lista de convites muda (enviou, importou…): o cartão busca a situação de novo. */
  readonly atualizacao = input<unknown>(null);

  protected readonly painel = httpResource<PainelAcompanhamento>(() => {
    this.atualizacao();
    return `${API_ADMIN}/empresas/${this.empresaId()}/acompanhamento`;
  });

  /** Quem da empresa acompanha pela página do RH e quando entrou. */
  protected readonly acessos = httpResource<AcessosRh>(() => {
    this.atualizacao();
    return `${API_ADMIN}/empresas/${this.empresaId()}/acesso-rh`;
  });

  protected readonly quando = quando;
  protected readonly mandando = signal<string | null>(null);
  protected readonly paginaCopiada = signal(false);
  protected readonly enviando = signal(false);
  protected readonly pausando = signal(false);
  protected readonly abrindo = signal(false);
  protected readonly aviso = signal<{ ok: boolean; texto: string } | null>(null);
  /** HTML do nosso próprio template, num iframe sem scripts (sandbox). */
  protected readonly html = signal<SafeHtml | null>(null);
  /** Qual e-mail está na prévia. */
  protected readonly previa = signal<'gestor' | 'lembrete' | 'acesso'>('gestor');

  protected readonly selo = computed(() => SELO[this.painel.hasValue() ? this.painel.value().situacao : 'DESLIGADO']);
  protected readonly textoEnviar = computed(() => {
    const s = this.painel.hasValue() ? this.painel.value().situacao : null;
    return s === 'FINAL_PENDENTE' || s === 'FINALIZADO' ? 'Enviar o resultado agora' : 'Enviar o relatório agora';
  });

  protected tituloItem(item: ItemAcompanhamento): string {
    return TITULO_ITEM[item.tipo];
  }

  protected async enviarAgora() {
    if (this.enviando()) return;
    this.enviando.set(true);
    this.aviso.set(null);
    try {
      const r = await firstValueFrom(
        this.http.post<EnvioAcompanhamento>(`${API_ADMIN}/empresas/${this.empresaId()}/acompanhamento/enviar`, {}),
      );
      this.aviso.set({ ok: r.enviado, texto: r.enviado ? r.mensagem : `O e-mail não saiu: ${r.mensagem}` });
      this.painel.reload();
    } catch (e) {
      this.aviso.set({ ok: false, texto: problemaDe(e).detail ?? 'Não foi possível enviar agora. Tente de novo.' });
    } finally {
      this.enviando.set(false);
    }
  }

  protected async pausar(pausado: boolean) {
    if (this.pausando()) return;
    this.pausando.set(true);
    this.aviso.set(null);
    try {
      const painel = await firstValueFrom(
        this.http.put<PainelAcompanhamento>(`${API_ADMIN}/empresas/${this.empresaId()}/acompanhamento/pausa`, { pausado }),
      );
      this.painel.set(painel);
      this.aviso.set({ ok: true, texto: pausado ? 'Relatório diário pausado.' : 'Relatório diário retomado.' });
    } catch (e) {
      this.aviso.set({ ok: false, texto: problemaDe(e).detail ?? 'Não foi possível mudar agora. Tente de novo.' });
    } finally {
      this.pausando.set(false);
    }
  }

  protected minuscula(texto: string) {
    return texto.charAt(0).toLowerCase() + texto.slice(1);
  }

  protected semProtocolo(url: string) {
    return url.replace(/^https?:\/\//, '');
  }

  /** "Mozilla/5.0 (iPhone; …)" → "iPhone". */
  protected aparelho(ua: string | null): string | null {
    if (!ua) return null;
    if (/iPhone/i.test(ua)) return 'iPhone';
    if (/iPad/i.test(ua)) return 'iPad';
    if (/Android/i.test(ua)) return 'Android';
    if (/Windows/i.test(ua)) return 'Windows';
    if (/Macintosh|Mac OS/i.test(ua)) return 'Mac';
    return null;
  }

  protected async mandarAcesso(email: string) {
    if (this.mandando()) return;
    this.mandando.set(email);
    this.aviso.set(null);
    try {
      await firstValueFrom(this.http.post(`${API_ADMIN}/empresas/${this.empresaId()}/acesso-rh/convite`, { email }));
      this.aviso.set({ ok: true, texto: `Acesso mandado para ${email}. O botão do e-mail vale por 7 dias.` });
      this.acessos.reload();
    } catch (e) {
      this.aviso.set({ ok: false, texto: problemaDe(e).detail ?? 'Não foi possível mandar agora. Tente de novo.' });
    } finally {
      this.mandando.set(null);
    }
  }

  protected async copiarPagina(pagina: string) {
    try {
      await navigator.clipboard.writeText(pagina);
      this.paginaCopiada.set(true);
      setTimeout(() => this.paginaCopiada.set(false), 2000);
    } catch {
      this.aviso.set({ ok: false, texto: 'Não deu para copiar. O endereço é ' + pagina });
    }
  }

  protected async verEmail(qual: 'gestor' | 'lembrete' | 'acesso') {
    if (this.abrindo()) return;
    this.abrindo.set(true);
    this.previa.set(qual);
    const caminho = qual === 'lembrete' ? 'convites/lembrete-previa' : qual === 'acesso' ? 'acesso-rh/convite-previa' : 'acompanhamento/email-previa';
    try {
      const html = await firstValueFrom(
        this.http.get(`${API_ADMIN}/empresas/${this.empresaId()}/${caminho}`, { responseType: 'text' }),
      );
      this.html.set(this.sanitizador.bypassSecurityTrustHtml(html));
      setTimeout(() => this.caixaEmail()?.nativeElement.showModal(), 0);
    } catch {
      this.aviso.set({ ok: false, texto: 'Não foi possível montar o e-mail agora.' });
    } finally {
      this.abrindo.set(false);
    }
  }
}
