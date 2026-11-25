import { afterNextRender, Component, computed, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { problemaDe } from '../../api/diagnostico-api';
import { problemaTelefone } from '../../compartilhado/entrada-telefone';
import { quando } from '../../compartilhado/formatos';
import { API_ADMIN, CargoOpcao, ConviteItem, EventoConvite, SituacaoEntrega, TipoEventoConvite } from '../admin-modelos';
import { CLASSE_STATUS, LinhaRevisao, mascarar, OUTRO_CARGO, paraEnvio, problemas, ROTULO_EVENTO, ROTULO_STATUS } from './convites';

/** Eventos que pedem atenção (vermelho) e os que marcam progresso do líder (verde). */
const ALERTA: TipoEventoConvite[] = ['FALHA_ENVIO', 'EMAIL_DEVOLVIDO', 'EMAIL_SPAM', 'INATIVADO'];
const PROGRESSO: TipoEventoConvite[] = [
  'ABERTO',
  'DADOS_CONFIRMADOS',
  'DADOS_CORRIGIDOS',
  'COMECOU_RESPONDER',
  'CONCLUIDO',
  'VIU_RESULTADO',
  'BAIXOU_PDF',
  'COMPARTILHOU_PDF',
  'REATIVADO',
];

/** Como a entrega do e-mail aparece embaixo do evento (o Resend avisa; sem pixel de abertura). */
const ENTREGA: Record<SituacaoEntrega, { texto: string; classe: string }> = {
  ENVIADO: { texto: 'Enviado', classe: 'bg-nevoa-2 text-abeto-2 ring-1 ring-linha' },
  ENTREGUE: { texto: 'Entregue', classe: 'bg-ascensao-suave text-[#17714A]' },
  ATRASADO: { texto: 'Entrega atrasada', classe: 'bg-[#FFF3DA] text-[#7A4D00]' },
  DEVOLVIDO: { texto: 'Voltou', classe: 'bg-[#FDEDEF] text-travada' },
  SPAM: { texto: 'Marcado como spam', classe: 'bg-[#FDEDEF] text-travada' },
  BLOQUEADO: { texto: 'Bloqueado', classe: 'bg-[#FDEDEF] text-travada' },
  FALHOU: { texto: 'Não saiu', classe: 'bg-[#FDEDEF] text-travada' },
};

/** A cor da linha ao lado de "Enviar convite por e-mail". */
const TOM_ENVIO = {
  neutro: 'text-abeto-2',
  ok: 'font-medium text-[#17714A]',
  erro: 'font-medium text-travada',
} as const;

/**
 * Um convite por dentro: o link pessoal (para suporte), os dados (editáveis), a linha do tempo
 * da auditoria (criado, enviado, abriu, confirmou ou corrigiu, concluiu) e inativar/reativar.
 */
@Component({
  selector: 'app-detalhe-convite',
  imports: [RouterLink],
  styleUrl: '../admin.css',
  template: `
    <dialog #dialogo class="dialogo dialogo-largo dialogo-medio" aria-labelledby="convite-titulo" (close)="fechou.emit()">
      <div class="flex flex-col">
        <header class="flex items-start justify-between gap-4 border-b border-linha px-5 pt-5 pb-4 sm:px-7 sm:pt-6">
          <div class="min-w-0">
            <span class="inline-flex min-h-7 items-center rounded-full px-2.5 text-[13px] font-semibold" [class]="classe()">{{ rotulo() }}</span>
            <h2 id="convite-titulo" class="display mt-2 text-[26px] leading-tight font-bold break-words">{{ convite().nome }}</h2>
            <p class="mt-0.5 text-[14px] break-all text-abeto-2">{{ convite().email }}</p>
          </div>
          <button type="button" class="-mt-1 -mr-2 grid size-11 shrink-0 place-items-center rounded-full text-abeto-2 hover:bg-nevoa hover:text-abeto" aria-label="Fechar" (click)="fechar()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" /></svg>
          </button>
        </header>

        <div class="min-h-0 flex-1 space-y-6 overflow-y-auto overscroll-contain px-5 py-5 sm:px-7">
          @if (inativo()) {
            <div class="flex items-start gap-3 rounded-2xl bg-nevoa-2 px-4 py-3.5 text-[15px] ring-1 ring-linha" role="status">
              <svg class="mt-0.5 shrink-0 text-abeto-2" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M8 12h8" stroke-linecap="round" /></svg>
              <p class="text-abeto-2"><strong class="font-semibold text-abeto">Inativo:</strong> o link está fechado e o líder não entra em nenhum cálculo (painel, funil e relatório da empresa).</p>
            </div>
          }
          @if (!inativo()) {
            <section aria-labelledby="convite-link">
              <h3 id="convite-link" class="text-[15px] font-semibold">Link pessoal</h3>
              <p class="mt-1 text-[13px] text-abeto-2">O líder recebe este link por e-mail. Copie só para ajudar quem não achou o convite.</p>
              <div class="mt-2 flex items-center gap-2 rounded-2xl bg-nevoa-2 py-1.5 pr-1.5 pl-4 ring-1 ring-linha/70">
                <p class="min-w-0 flex-1 truncate text-[15px] font-semibold select-all">{{ convite().link }}</p>
                <button type="button" class="botao-secundario min-h-11 shrink-0 px-4 text-[14px]" (click)="copiar()">{{ copiado() ? 'Copiado' : 'Copiar' }}</button>
              </div>
              @if (podeEnviar()) {
                @let situacao = situacaoEnvio();
                <div class="mt-3 flex flex-col gap-2.5 sm:flex-row sm:items-center sm:gap-4">
                  <button
                    type="button"
                    class="botao-primario min-h-12 w-full shrink-0 px-6 text-[16px] disabled:cursor-not-allowed disabled:opacity-40 sm:w-auto"
                    aria-describedby="convite-envio-situacao"
                    [attr.aria-busy]="enviando()"
                    [disabled]="envioBloqueado()"
                    (click)="enviarConvite()"
                  >
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 6.5h16v11H4z" stroke-linejoin="round" /><path d="M4.5 7l7.5 6 7.5-6" stroke-linejoin="round" /></svg>
                    {{ enviando() ? 'Enviando…' : 'Enviar convite por e-mail' }}
                  </button>
                  <p id="convite-envio-situacao" class="text-[13px] sm:text-[14px]" [class]="TOM_ENVIO[situacao.tom]" aria-live="polite">{{ situacao.texto }}</p>
                </div>
              }
            </section>
          }

          <section aria-labelledby="convite-dados">
            <div class="flex items-center justify-between gap-3">
              <h3 id="convite-dados" class="text-[15px] font-semibold">Dados</h3>
              @if (!editando() && !inativo()) {
                <button type="button" class="inline-flex min-h-11 items-center text-[15px] font-semibold text-abeto hover:underline" (click)="editar()">Editar</button>
              }
            </div>
            @if (editando(); as e) {
              <div class="mt-2 grid gap-3 sm:grid-cols-2">
                <div>
                  <label class="campo-rotulo" for="conv-nome">Nome completo</label>
                  <input id="conv-nome" class="campo" [value]="e.nome" [attr.aria-invalid]="erroDe('nome') !== null" (input)="mudar('nome', $any($event.target).value)" />
                </div>
                <div>
                  <label class="campo-rotulo" for="conv-email">E-mail</label>
                  <input id="conv-email" class="campo" type="email" inputmode="email" autocapitalize="none" spellcheck="false" [value]="e.email" [attr.aria-invalid]="erroDe('email') !== null" (input)="mudar('email', $any($event.target).value)" />
                </div>
                <div>
                  <label class="campo-rotulo" for="conv-whatsapp">WhatsApp (opcional)</label>
                  <input id="conv-whatsapp" class="campo" type="tel" inputmode="tel" placeholder="(11) 98765-4321" [value]="e.whatsapp" [attr.aria-invalid]="erroDe('whatsapp') !== null || !!erroWhatsapp()" aria-describedby="conv-whatsapp-erro" (input)="mudar('whatsapp', mascarar($event))" (blur)="whatsappTocado.set(true)" />
                  <p id="conv-whatsapp-erro" class="mt-1 text-[14px] font-medium text-travada empty:hidden" aria-live="polite">{{ erroWhatsapp() }}</p>
                </div>
                @if (cargos().length) {
                  <div>
                    <label class="campo-rotulo" for="conv-cargo">Cargo</label>
                    <select id="conv-cargo" class="campo" [attr.aria-invalid]="erroDe('cargo') !== null" (change)="mudar('cargo', $any($event.target).value)">
                      <option value="" [selected]="!e.cargo">Escolha o cargo</option>
                      @for (c of cargos(); track c.id) {
                        <option [value]="'' + c.id" [selected]="e.cargo === '' + c.id">{{ c.nome }}</option>
                      }
                      <option [value]="OUTRO" [selected]="e.cargo === OUTRO">Outro cargo</option>
                    </select>
                  </div>
                }
              </div>
              <p class="mt-2 min-h-5 text-[14px] font-medium text-travada" aria-live="polite">{{ erroEdicao() }}</p>
              <div class="mt-1 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                <button type="button" class="botao-secundario" (click)="editando.set(null)">Cancelar</button>
                <button type="button" class="botao-primario" [attr.aria-busy]="salvando()" [disabled]="salvando()" (click)="salvar()">
                  {{ salvando() ? 'Salvando…' : 'Salvar dados' }}
                </button>
              </div>
              @if (convite().diagnosticoId) {
                <p class="mt-2 text-[13px] text-abeto-2">Ele já respondeu: a correção vale também no diagnóstico (o cargo certo entra nos relatórios).</p>
              }
            } @else {
              <dl class="mt-2 divide-y divide-linha rounded-2xl ring-1 ring-linha">
                <div class="flex justify-between gap-4 px-4 py-3 text-[15px]"><dt class="text-abeto-2">WhatsApp</dt><dd class="text-right font-medium">{{ convite().whatsapp || '—' }}</dd></div>
                <div class="flex justify-between gap-4 px-4 py-3 text-[15px]"><dt class="text-abeto-2">Cargo</dt><dd class="text-right font-medium">{{ convite().cargo || '—' }}</dd></div>
              </dl>
            }
          </section>

          <section aria-labelledby="convite-historico">
            <h3 id="convite-historico" class="text-[15px] font-semibold">Linha do tempo</h3>
            @if (eventos.hasValue()) {
              <ol class="mt-3">
                @for (ev of eventos.value(); track $index; let ultimo = $last) {
                  <li class="relative flex gap-3 pb-4">
                    @if (!ultimo) {
                      <span class="absolute top-4 bottom-0 left-[7px] w-px bg-linha" aria-hidden="true"></span>
                    }
                    <span class="relative mt-1 size-[15px] shrink-0 rounded-full ring-4 ring-white" [class]="corEvento(ev)" aria-hidden="true"></span>
                    <div class="min-w-0">
                      <p class="text-[15px] font-semibold">{{ ROTULO_EVENTO[ev.tipo] }}</p>
                      @if (ev.detalhe) {
                        <p class="mt-0.5 text-[14px] break-words text-abeto-2">{{ ev.detalhe }}</p>
                      }
                      @if (ev.email && ev.tipo !== 'EMAIL_DEVOLVIDO' && ev.tipo !== 'EMAIL_SPAM') {
                        @let email = ev.email;
                        <p class="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-[13px]">
                          <span class="inline-flex min-h-6 items-center rounded-full px-2 font-semibold" [class]="ENTREGA[email.situacao].classe">{{ ENTREGA[email.situacao].texto }}</span>
                          @if (email.situacao === 'ENTREGUE' && email.entregueEm) {
                            <span class="text-abeto-3">{{ quando(email.entregueEm) }}</span>
                          } @else if (email.situacao === 'ENVIADO') {
                            <span class="text-abeto-3">aguardando o aviso de entrega</span>
                          }
                        </p>
                      }
                      <p class="mt-0.5 text-[13px] text-abeto-3">{{ quando(ev.quando) }}@if (ev.autor) { · por {{ ev.autor }}}</p>
                    </div>
                  </li>
                }
              </ol>
            } @else if (eventos.error()) {
              <p class="mt-2 text-[14px] text-travada">Não foi possível carregar o histórico.</p>
            } @else {
              <div class="mt-3 h-24 animate-pulse rounded-2xl bg-nevoa-2"></div>
            }
          </section>
        </div>

        <footer class="border-t border-linha px-5 pt-3 pb-[max(14px,env(safe-area-inset-bottom))] sm:px-7">
          @if (inativo()) {
            <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <p class="text-[14px] text-abeto-2">Reativar abre o link de novo e devolve o líder aos cálculos.</p>
              <button type="button" class="botao-primario shrink-0" [attr.aria-busy]="salvando()" [disabled]="salvando()" (click)="reativar()">Reativar líder</button>
            </div>
          } @else if (confirmandoInativacao()) {
            <p class="text-[14px] text-abeto">
              O link fecha e {{ convite().diagnosticoId ? 'o diagnóstico dele sai' : 'ele sai' }} de todos os cálculos: painel, funil e relatório da empresa. Nada é apagado, e dá para reativar depois.
            </p>
            <div class="mt-3 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <button type="button" class="botao-secundario" (click)="confirmandoInativacao.set(false)">Voltar</button>
              <button type="button" class="botao-primario bg-travada hover:bg-[#9F2A3D]" [attr.aria-busy]="salvando()" [disabled]="salvando()" (click)="inativar()">Inativar líder</button>
            </div>
          } @else {
            <div class="flex flex-wrap items-center justify-between gap-2">
              <button type="button" class="inline-flex min-h-11 items-center text-[15px] font-semibold text-travada hover:underline" (click)="confirmandoInativacao.set(true)">
                Inativar líder
              </button>
              @if (convite().diagnosticoId; as diagnostico) {
                <a [routerLink]="['/administrador/diagnosticos', diagnostico]" class="inline-flex min-h-11 items-center gap-1 text-[15px] font-semibold text-abeto hover:underline">
                  Ver o diagnóstico <span aria-hidden="true">→</span>
                </a>
              }
            </div>
          }
        </footer>
      </div>
    </dialog>
  `,
})
export class DetalheConvite {
  private readonly http = inject(HttpClient);
  private readonly dialogo = viewChild.required<ElementRef<HTMLDialogElement>>('dialogo');

  readonly convite = input.required<ConviteItem>();
  readonly cargos = input<CargoOpcao[]>([]);
  /** O servidor envia e-mail de verdade (fora do modo log). */
  readonly emailAtivo = input(true);
  /** A avaliação da empresa já encerrou: o convite não sai mais. */
  readonly encerrada = input(false);
  readonly mudou = output();
  readonly fechou = output();

  protected readonly OUTRO = OUTRO_CARGO;
  protected readonly ROTULO_EVENTO = ROTULO_EVENTO;
  protected readonly quando = quando;
  protected readonly mascarar = mascarar;
  protected readonly ENTREGA = ENTREGA;
  protected readonly eventos = httpResource<EventoConvite[]>(() => `${API_ADMIN}/convites/${this.convite().id}/eventos`);
  protected readonly rotulo = computed(() => ROTULO_STATUS[this.convite().status]);
  protected readonly classe = computed(() => CLASSE_STATUS[this.convite().status]);

  protected readonly editando = signal<LinhaRevisao | null>(null);
  protected readonly salvando = signal(false);
  protected readonly erroEdicao = signal<string | null>(null);
  protected readonly whatsappTocado = signal(false);
  /** Erro do WhatsApp embaixo do campo: ao sair dele, ou já no meio quando é certeza (DDD, 9). */
  protected readonly erroWhatsapp = computed(() => {
    const e = this.editando();
    if (!e || this.erroEdicao()) return null;
    return problemaTelefone(e.whatsapp, !this.whatsappTocado());
  });
  protected readonly copiado = signal(false);
  protected readonly confirmandoInativacao = signal(false);
  protected readonly inativo = computed(() => this.convite().status === 'INATIVO');

  protected readonly TOM_ENVIO = TOM_ENVIO;
  protected readonly enviando = signal(false);
  /** Para quem o convite acabou de sair (o endereço que o servidor confirmou). */
  protected readonly enviadoPara = signal<string | null>(null);
  protected readonly erroEnvio = signal<string | null>(null);
  /** Quem ainda pode receber: ativo e sem diagnóstico concluído. */
  protected readonly podeEnviar = computed(() => !this.inativo() && this.convite().status !== 'CONCLUIDO');
  protected readonly envioBloqueado = computed(() => !this.emailAtivo() || this.encerrada());
  /** A linha ao lado do botão: o resultado do clique ou como está o envio deste líder. */
  protected readonly situacaoEnvio = computed<{ texto: string; tom: keyof typeof TOM_ENVIO }>(() => {
    const erro = this.erroEnvio();
    if (erro) return { texto: erro, tom: 'erro' };
    const para = this.enviadoPara();
    if (para) return { texto: `Enviado agora para ${para}. A entrega aparece na linha do tempo.`, tom: 'ok' };
    if (!this.emailAtivo()) return { texto: 'O envio por e-mail está desligado neste servidor.', tom: 'neutro' };
    if (this.encerrada()) return { texto: 'A avaliação já foi encerrada: o convite não sai mais.', tom: 'neutro' };
    const c = this.convite();
    if (!c.envios) return { texto: 'O convite ainda não foi enviado.', tom: 'neutro' };
    const vezes = c.envios === 1 ? '1 vez' : `${c.envios} vezes`;
    return { texto: `Enviado ${vezes}. Último envio: ${quando(c.enviadoEm)}.`, tom: 'neutro' };
  });

  constructor() {
    afterNextRender(() => this.dialogo().nativeElement.showModal());
  }

  protected corEvento(ev: EventoConvite): string {
    if (ALERTA.includes(ev.tipo)) return 'bg-travada';
    if (PROGRESSO.includes(ev.tipo)) return 'bg-ascensao';
    return 'bg-abeto-3';
  }

  protected editar() {
    const c = this.convite();
    this.erroEdicao.set(null);
    this.whatsappTocado.set(false);
    this.editando.set({
      chave: `c${c.id}`,
      linha: 0,
      nome: c.nome,
      email: c.email,
      whatsapp: c.whatsapp ?? '',
      cargo: c.cargoOutro ? OUTRO_CARGO : c.cargoId !== null ? String(c.cargoId) : '',
      cargoTexto: '',
      sugestaoCargoId: null,
      jaConvidado: false,
      exemplo: false,
    });
    setTimeout(() => document.getElementById('conv-nome')?.focus(), 0);
  }

  protected mudar(campo: 'nome' | 'email' | 'whatsapp' | 'cargo', valor: string) {
    this.editando.update((e) => (e ? { ...e, [campo]: valor } : e));
    this.erroEdicao.set(null);
  }

  protected erroDe(campo: string): string | null {
    const e = this.editando();
    if (!e || !this.erroEdicao()) return null;
    return problemas(e, new Set(), this.cargos().length > 0).find((p) => p.campo === campo)?.mensagem ?? null;
  }

  protected async salvar() {
    const e = this.editando();
    if (!e || this.salvando()) return;
    const lista = problemas(e, new Set(), this.cargos().length > 0);
    if (lista.length) {
      this.erroEdicao.set(lista[0].mensagem);
      return;
    }
    this.salvando.set(true);
    try {
      await firstValueFrom(this.http.put(`${API_ADMIN}/convites/${this.convite().id}`, paraEnvio(e)));
      this.editando.set(null);
      this.enviadoPara.set(null);
      this.erroEnvio.set(null);
      this.eventos.reload();
      this.mudou.emit();
    } catch (erro) {
      this.erroEdicao.set(problemaDe(erro).detail ?? 'Não foi possível salvar agora.');
    } finally {
      this.salvando.set(false);
    }
  }

  protected inativar() {
    return this.acao('inativar');
  }

  protected reativar() {
    return this.acao('reativar');
  }

  /** Inativa ou reativa; a lista recarrega e o histórico ganha o evento. */
  private async acao(qual: 'inativar' | 'reativar') {
    this.salvando.set(true);
    try {
      await firstValueFrom(this.http.post(`${API_ADMIN}/convites/${this.convite().id}/${qual}`, null));
      this.confirmandoInativacao.set(false);
      this.eventos.reload();
      this.mudou.emit();
    } finally {
      this.salvando.set(false);
    }
  }

  /** Envia o convite só deste líder, na hora; a linha do tempo e a lista recarregam. */
  protected async enviarConvite() {
    if (this.enviando() || this.envioBloqueado()) return;
    this.enviando.set(true);
    this.erroEnvio.set(null);
    this.enviadoPara.set(null);
    try {
      const feito = await firstValueFrom(this.http.post<{ para: string }>(`${API_ADMIN}/convites/${this.convite().id}/enviar`, null));
      this.enviadoPara.set(feito.para);
      this.mudou.emit();
    } catch (erro) {
      this.erroEnvio.set(problemaDe(erro).detail ?? 'O convite não saiu. Tente de novo em instantes.');
    } finally {
      this.enviando.set(false);
      this.eventos.reload();
    }
  }

  protected async copiar() {
    try {
      await navigator.clipboard.writeText(this.convite().link);
      this.copiado.set(true);
      setTimeout(() => this.copiado.set(false), 2000);
    } catch {
      /* sem permissão: o texto continua selecionável */
    }
  }

  protected fechar() {
    this.dialogo().nativeElement.close();
  }
}
