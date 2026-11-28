import { HttpClient } from '@angular/common/http';
import { afterNextRender, Component, computed, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { firstValueFrom } from 'rxjs';

import { problemaDe } from '../api/diagnostico-api';
import { quando } from '../compartilhado/formatos';
import { telaDeComputador } from '../compartilhado/util';
import { assuntoDoLider, EmailPronto, enderecoDoEmail, mensagemDoLider, mensagemParaTodos, ProgramaEmail } from './mensagens-rh';
import { API_RH, GestaoRh, LiderRh, primeiroNome } from './rh-api';

/** Para quem é o e-mail: um líder (o botão de e-mail) ou todos que faltam ("Lembrar todos"). */
export type AlvoEmail = { tipo: 'um'; lider: LiderRh } | { tipo: 'todos'; lideres: LiderRh[] };

type Via = ProgramaEmail | 'COPIA';

const NOME_PROGRAMA: Record<Via, string> = {
  OUTLOOK: 'o Outlook',
  GMAIL: 'o Gmail',
  APP: 'o seu programa de e-mail',
  COPIA: '',
};

const minuscula = (texto: string) => texto.charAt(0).toLowerCase() + texto.slice(1);

/**
 * "Por e-mail": o RH escolhe, sempre, por onde a mensagem sai. Pela plataforma (com a marca da
 * empresa, a mensagem certa para a situação do líder e a entrega rastreada) ou pelo próprio e-mail
 * (Outlook, Gmail ou o programa do computador, com a mensagem já escrita: costuma ser mais aberto,
 * porque vem de alguém conhecido). Serve a um líder e ao "Lembrar todos que faltam".
 */
@Component({
  selector: 'app-email-rh',
  styleUrl: './acompanhar.css',
  styles: `
    .dialogo-email {
      width: min(100% - 32px, 46rem);
      max-height: min(92dvh, 860px);
      margin: auto;
      padding: 0;
      border: 0;
      border-radius: 26px;
      background: var(--color-nevoa);
      color: var(--color-abeto);
      box-shadow: 0 30px 80px -30px rgb(15 43 38 / 0.55);
      overscroll-behavior: contain;
    }
    .dialogo-email[open] {
      animation: aparecer 220ms ease-out;
    }
    .dialogo-email::backdrop {
      background: rgb(15 43 38 / 0.45);
      backdrop-filter: blur(2px);
    }
    /* No celular, sobe do rodapé (no alcance do polegar). */
    @media (width < 40rem) {
      .dialogo-email {
        width: 100%;
        max-width: 100%;
        max-height: 92dvh;
        margin: auto 0 0;
        border-radius: 26px 26px 0 0;
      }
      .dialogo-email[open] {
        animation: subir-email 320ms var(--ease-mola);
      }
    }
    @keyframes subir-email {
      from { transform: translate3d(0, 40px, 0); opacity: 0; }
    }

    .canal-envio {
      display: flex;
      flex-direction: column;
      align-items: stretch;
      border-radius: 22px;
      background: #fff;
      box-shadow: 0 0 0 1px color-mix(in srgb, var(--color-linha) 85%, transparent);
    }
    .selo {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      min-height: 24px;
      padding: 0 9px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 700;
    }
    .programa {
      display: flex;
      width: 100%;
      min-height: 50px;
      align-items: center;
      gap: 12px;
      padding: 0 16px;
      border-radius: 14px;
      box-shadow: inset 0 0 0 1.5px var(--color-linha);
      font-size: 15px;
      font-weight: 600;
      text-align: left;
      transition: box-shadow 160ms, background-color 160ms;
    }
    .programa:hover {
      background: var(--color-nevoa-2);
      box-shadow: inset 0 0 0 1.5px var(--color-abeto-3);
    }
    .programa:focus-visible {
      outline: 2px solid var(--color-acao);
      outline-offset: 2px;
    }
    .previa-email {
      width: 100%;
      height: 380px;
      border: 0;
      border-radius: 16px;
      background: #fff;
    }
    @media (prefers-reduced-motion: reduce) {
      .dialogo-email[open] { animation: none; }
    }
  `,
  template: `
    <dialog #dialogo class="dialogo-email" aria-labelledby="email-titulo" aria-describedby="email-sub" (close)="fechou.emit()" (click)="cliqueFora($event)">
      <div class="p-5 sm:p-7">
        <div class="flex items-start justify-between gap-4">
          <div class="min-w-0">
            <p class="rotulo text-brasa-escura">{{ alvo().tipo === 'um' ? 'Mandar e-mail' : 'Lembrar quem falta' }}</p>
            <h2 id="email-titulo" class="display mt-1 text-[26px] leading-tight font-bold sm:text-[30px]">{{ titulo() }}</h2>
            <p id="email-sub" class="mt-1 text-[14.5px] leading-snug break-words text-abeto-2">{{ subtitulo() }}</p>
          </div>
          <button type="button" class="-mt-1 -mr-2 grid size-12 shrink-0 place-items-center rounded-full text-abeto-2 hover:bg-white hover:text-abeto" aria-label="Fechar" (click)="fechar()">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" /></svg>
          </button>
        </div>

        @switch (estado()) {
          @case ('feito-plataforma') {
            <div class="aparecer mt-6 rounded-[22px] bg-white p-6 text-center ring-1 ring-linha/70" role="status">
              <span class="mx-auto grid size-14 place-items-center rounded-full bg-ascensao text-white" aria-hidden="true">
                <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.8"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
              </span>
              <p class="display mt-4 text-[24px] leading-tight font-bold">{{ feito()?.titulo }}</p>
              <p class="mx-auto mt-2 max-w-md text-[15px] leading-snug text-abeto-2">{{ feito()?.texto }}</p>
              <button type="button" class="botao-primario mt-6 min-h-12 px-8 text-[15px]" (click)="fechar()">Pronto</button>
            </div>
          }
          @case ('feito-proprio') {
            <div class="aparecer mt-6 rounded-[22px] bg-white p-6 text-center ring-1 ring-linha/70" role="status">
              <span class="mx-auto grid size-14 place-items-center rounded-full bg-acao text-sobre-acao" aria-hidden="true">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M4 12l16-8-6 16-2.5-6.5L4 12z" stroke-linejoin="round" /></svg>
              </span>
              <p class="display mt-4 text-[24px] leading-tight font-bold">{{ feito()?.titulo }}</p>
              <p class="mx-auto mt-2 max-w-md text-[15px] leading-snug text-abeto-2">{{ feito()?.texto }}</p>
              <div class="mt-6 flex flex-col-reverse justify-center gap-2 sm:flex-row">
                <button type="button" class="botao-secundario min-h-12 bg-white px-6 text-[15px]" (click)="estado.set('escolher')">Ver as opções de novo</button>
                <button type="button" class="botao-primario min-h-12 px-8 text-[15px]" (click)="fechar()">Pronto</button>
              </div>
            </div>
          }
          @default {
            <div class="mt-6 grid gap-3 sm:grid-cols-2">
              <!-- Pela plataforma: a mensagem certa para a situação, com a marca da empresa e a entrega rastreada. -->
              <section class="canal-envio p-5" aria-labelledby="opcao-plataforma">
                <div class="flex items-center gap-3">
                  <span class="grid size-11 shrink-0 place-items-center rounded-2xl bg-acao text-sobre-acao" aria-hidden="true">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="5" width="18" height="14" rx="2.5" /><path d="M4 7l8 6 8-6" stroke-linecap="round" stroke-linejoin="round" /></svg>
                  </span>
                  <div class="min-w-0">
                    <h3 id="opcao-plataforma" class="text-[17px] leading-tight font-semibold">Pela plataforma</h3>
                    <span class="selo mt-1 bg-ascensao-suave text-[#17714A]">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
                      Entrega rastreada
                    </span>
                  </div>
                </div>
                <p class="mt-3 text-[14.5px] leading-snug text-abeto-2">
                  Sai do e-mail do Diagnóstico do Líder, com a marca da {{ empresa() }} e o link pessoal. A entrega aparece na linha do tempo.
                </p>

                <ul class="mt-3 space-y-1.5 text-[14px] leading-snug" [class.hidden]="alvo().tipo === 'um' && !!bloqueio()">
                  @for (item of oQueVai(); track item.texto) {
                    <li class="flex items-start gap-2" [class.text-abeto-2]="item.fora">
                      <i class="mt-1.5 block size-2 shrink-0 rounded-full" [style.background]="item.cor" aria-hidden="true"></i>
                      <span>{{ item.texto }}</span>
                    </li>
                  }
                </ul>
                @if (ultimoEnvio(); as u) {
                  <p class="mt-2 text-[13px] text-abeto-2">{{ u }}</p>
                }

                <button type="button" class="mt-2 inline-flex min-h-11 items-center gap-1.5 self-start text-[14px] font-semibold text-abeto underline decoration-linha underline-offset-4 hover:decoration-abeto" [attr.aria-busy]="carregandoPrevia()" [attr.aria-expanded]="!!previa()" aria-controls="previa-do-email" (click)="alternarPrevia()">
                  {{ carregandoPrevia() ? 'Abrindo o e-mail…' : previa() ? 'Esconder o e-mail' : 'Ver o e-mail' }}
                </button>

                <div class="mt-auto pt-4">
                  @if (bloqueio(); as b) {
                    <p class="rounded-2xl bg-[#FDEDEF] px-4 py-3 text-[14px] leading-snug font-medium text-travada">{{ b }}</p>
                    @if (alvo().tipo === 'um' && liderUnico()?.alerta) {
                      <button type="button" class="botao-secundario mt-2 min-h-12 w-full bg-white text-[15px]" (click)="corrigir()">Corrigir o e-mail</button>
                    }
                  } @else {
                    <button type="button" class="botao-primario min-h-12 w-full text-[15px]" [attr.aria-busy]="enviando()" [disabled]="enviando()" (click)="enviarPelaPlataforma()">
                      @if (enviando()) {
                        <span class="size-4 animate-spin rounded-full border-2 border-white/30 border-t-white" aria-hidden="true"></span>
                        Enviando…
                      } @else {
                        {{ alvo().tipo === 'um' ? 'Enviar pela plataforma' : 'Enviar para ' + paraPlataforma().length }}
                      }
                    </button>
                  }
                  @if (erro(); as e) {
                    <p class="aparecer mt-2 text-[14px] leading-snug font-medium text-travada" role="alert">{{ e }}</p>
                  }
                </div>
              </section>

              <!-- Pelo e-mail do RH: Outlook, Gmail ou o programa do computador, com a mensagem pronta. -->
              <section class="canal-envio p-5" aria-labelledby="opcao-proprio">
                <div class="flex items-center gap-3">
                  <span class="grid size-11 shrink-0 place-items-center rounded-2xl bg-nevoa text-abeto ring-1 ring-linha" aria-hidden="true">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="3.6" /><path d="M5 20c.8-3.6 3.6-6 7-6s6.2 2.4 7 6" stroke-linecap="round" /></svg>
                  </span>
                  <div class="min-w-0">
                    <h3 id="opcao-proprio" class="text-[17px] leading-tight font-semibold">Pelo seu e-mail</h3>
                    <span class="selo mt-1 bg-nevoa text-abeto-2">Vem de você</span>
                  </div>
                </div>
                <p class="mt-3 text-[14.5px] leading-snug text-abeto-2">
                  @if (alvo().tipo === 'um') {
                    Sai do seu e-mail de trabalho, como uma mensagem sua, com o link pessoal de {{ primeiro() }}. Costuma ser mais aberto, porque vem de alguém conhecido.
                  } @else {
                    Um e-mail seu, com os {{ ocultos().length }} líderes em cópia oculta (ninguém vê o endereço dos outros). Cada um acha o link pessoal no e-mail do convite.
                  }
                </p>

                <div class="mt-4 grid gap-2">
                  @for (p of programas(); track p.via) {
                    <a class="programa" [href]="endereco(p.via)" [attr.target]="p.via === 'APP' ? null : '_blank'" rel="noopener" (click)="abriu(p.via)">
                      <span class="grid size-8 shrink-0 place-items-center rounded-lg text-[13px] font-bold" [style.background]="p.fundo" [style.color]="p.cor" aria-hidden="true">{{ p.sigla }}</span>
                      <span class="min-w-0 flex-1">
                        <span class="block leading-tight">{{ p.nome }}</span>
                        <span class="block text-[12.5px] font-normal text-abeto-2">{{ p.dica }}</span>
                      </span>
                      <svg class="shrink-0 text-abeto-3" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5" stroke-linecap="round" stroke-linejoin="round" /></svg>
                    </a>
                  }
                </div>
                <div class="mt-2 flex flex-wrap gap-x-4">
                  <button type="button" class="inline-flex min-h-11 items-center gap-1.5 text-[14px] font-semibold text-abeto underline decoration-linha underline-offset-4 hover:decoration-abeto" (click)="copiar()">
                    Copiar a mensagem
                  </button>
                  @if (alvo().tipo === 'todos') {
                    <button type="button" class="inline-flex min-h-11 items-center gap-1.5 text-[14px] font-semibold text-abeto underline decoration-linha underline-offset-4 hover:decoration-abeto" (click)="copiarEnderecos()">
                      {{ copiouEnderecos() ? 'E-mails copiados' : 'Copiar os e-mails' }}
                    </button>
                  }
                </div>
                <p class="mt-auto pt-3 text-[12.5px] leading-snug text-abeto-2">
                  Sem confirmação de entrega: a linha do tempo registra que você abriu a mensagem.
                </p>
              </section>
            </div>

            <!-- A prévia do e-mail da plataforma, na largura inteira (como o líder vê). -->
            @if (previa(); as html) {
              <div id="previa-do-email" class="aparecer mt-3">
                <p class="mb-2 text-[13px] font-semibold text-abeto-2">{{ alvo().tipo === 'um' ? 'O e-mail que ' + primeiro() + ' recebe pela plataforma' : 'O convite que vai pela plataforma (quem começou recebe o lembrete, no mesmo formato)' }}</p>
                <iframe class="previa-email ring-1 ring-linha/70" title="Prévia do e-mail que o líder recebe" [srcdoc]="html"></iframe>
              </div>
            }
          }
        }
      </div>
    </dialog>
  `,
})
export class EmailRh {
  private readonly http = inject(HttpClient);
  private readonly sanitizador = inject(DomSanitizer);
  private readonly dialogo = viewChild.required<ElementRef<HTMLDialogElement>>('dialogo');
  private readonly computador = telaDeComputador();

  readonly alvo = input.required<AlvoEmail>();
  readonly empresaId = input.required<number>();
  readonly empresa = input.required<string>();
  /** O e-mail de quem acompanha: no "Lembrar todos", vai como destinatário (os líderes em cópia oculta). */
  readonly remetente = input('');
  /** " O prazo vai até sexta, 28 de novembro, às 18h." (ou vazio). */
  readonly frasePrazo = input('');
  readonly gestao = input<GestaoRh | undefined>();

  readonly fechou = output<void>();
  /** Saiu algo pela plataforma ou ficou registrado: a lista se atualiza. */
  readonly mudou = output<void>();
  /** "Corrigir o e-mail": a página abre o cadastro do líder. */
  readonly corrigirEmail = output<LiderRh>();

  protected readonly estado = signal<'escolher' | 'feito-plataforma' | 'feito-proprio'>('escolher');
  protected readonly feito = signal<{ titulo: string; texto: string } | null>(null);
  protected readonly enviando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly previa = signal<SafeHtml | null>(null);
  protected readonly carregandoPrevia = signal(false);
  protected readonly copiouEnderecos = signal(false);

  protected readonly liderUnico = computed(() => {
    const a = this.alvo();
    return a.tipo === 'um' ? a.lider : null;
  });
  protected readonly primeiro = computed(() => primeiroNome(this.liderUnico()?.nome ?? ''));
  private readonly todos = computed(() => {
    const a = this.alvo();
    return a.tipo === 'um' ? [a.lider] : a.lideres;
  });
  /** Quem a plataforma alcança (e-mail que voltou ou foi para o spam fica de fora). */
  protected readonly paraPlataforma = computed(() => this.todos().filter((l) => !l.alerta));
  protected readonly ocultos = computed(() => this.todos().map((l) => l.email));

  protected readonly titulo = computed(() => {
    const a = this.alvo();
    return a.tipo === 'um' ? `Para ${a.lider.nome}` : `Lembrar ${a.lideres.length === 1 ? 'o líder que falta' : `os ${a.lideres.length} líderes que faltam`}`;
  });
  protected readonly subtitulo = computed(() => {
    const a = this.alvo();
    if (a.tipo === 'um') return a.lider.email;
    const comecaram = a.lideres.filter((l) => l.situacao === 'COMECOU').length;
    const outros = a.lideres.length - comecaram;
    const partes = [
      comecaram ? `${comecaram} ${comecaram === 1 ? 'começou e não terminou' : 'começaram e não terminaram'}` : '',
      outros ? `${outros} ainda não ${outros === 1 ? 'começou' : 'começaram'}` : '',
    ].filter(Boolean);
    return `${partes.join(' · ')}.`;
  });

  /** O que cada um recebe pela plataforma, numa lista curta. */
  protected readonly oQueVai = computed(() => {
    const a = this.alvo();
    const lembrete = 'var(--color-plato)';
    const convite = 'var(--color-acao)';
    if (a.tipo === 'um') {
      const l = a.lider;
      if (l.situacao === 'COMECOU') {
        return [{ texto: 'Vai um lembrete: “Falta pouco para terminar”, com o botão para continuar de onde parou.', cor: lembrete, fora: false }];
      }
      return [{ texto: l.enviadoEm ? 'Vai o convite de novo, com o link pessoal.' : 'Vai o convite, com o link pessoal.', cor: convite, fora: false }];
    }
    const livres = this.paraPlataforma();
    const lembretes = livres.filter((l) => l.situacao === 'COMECOU').length;
    const convites = livres.length - lembretes;
    const fora = a.lideres.length - livres.length;
    const itens: { texto: string; cor: string; fora: boolean }[] = [];
    if (lembretes) itens.push({ texto: `${lembretes} ${lembretes === 1 ? 'recebe' : 'recebem'} o lembrete para continuar de onde parou.`, cor: lembrete, fora: false });
    if (convites) itens.push({ texto: `${convites} ${convites === 1 ? 'recebe' : 'recebem'} o convite com o link pessoal.`, cor: convite, fora: false });
    if (fora) itens.push({ texto: `${fora} ${fora === 1 ? 'fica' : 'ficam'} de fora: o e-mail voltou ou foi para o spam.`, cor: 'var(--color-travada)', fora: true });
    return itens;
  });

  protected readonly ultimoEnvio = computed(() => {
    const l = this.liderUnico();
    if (!l?.enviadoEm) return null;
    return `Último convite pela plataforma: ${minuscula(quando(l.enviadoEm))}${l.envios > 1 ? ` · ${l.envios} envios` : ''}.`;
  });

  /** Por que a plataforma não manda agora (null: pode mandar). */
  protected readonly bloqueio = computed(() => {
    const g = this.gestao();
    if (g && !g.emailAtivo) return 'O envio de e-mail pela plataforma está desligado. Use o seu e-mail ou o WhatsApp.';
    if (g?.encerrada) return 'A avaliação já encerrou: a plataforma não manda mais e-mails.';
    const l = this.liderUnico();
    if (l?.alerta) return `${l.alerta}: a plataforma não manda de novo para este endereço. Corrija o e-mail ou use o WhatsApp.`;
    if (!l && this.paraPlataforma().length === 0) return 'Os e-mails de quem falta voltaram ou foram para o spam: use o WhatsApp.';
    return null;
  });

  /** O e-mail pronto para o Outlook, o Gmail ou o programa do computador. */
  private readonly pronto = computed<EmailPronto>(() => {
    const l = this.liderUnico();
    if (l) {
      return { para: [l.email], ocultos: [], assunto: assuntoDoLider(l), corpo: mensagemDoLider(l, this.empresa(), this.frasePrazo()) };
    }
    const m = mensagemParaTodos(this.empresa(), this.frasePrazo());
    return { para: this.remetente() ? [this.remetente()] : [], ocultos: this.ocultos(), assunto: m.assunto, corpo: m.corpo };
  });

  /** No computador, o Outlook e o Gmail na web primeiro; no celular, o app de e-mail. */
  protected readonly programas = computed(() => {
    const lista = [
      { via: 'OUTLOOK' as const, nome: 'Outlook', dica: 'Abre no navegador, já escrito', sigla: 'O', fundo: '#E6F0FB', cor: '#0F5DB8' },
      { via: 'GMAIL' as const, nome: 'Gmail', dica: 'Abre no navegador, já escrito', sigla: 'G', fundo: '#FDECEA', cor: '#C5221F' },
      {
        via: 'APP' as const,
        nome: this.computador() ? 'Programa de e-mail' : 'App de e-mail',
        dica: this.computador() ? 'Outlook instalado, Mail ou outro' : 'O app de e-mail do celular',
        sigla: '@',
        fundo: 'var(--color-nevoa)',
        cor: 'var(--color-abeto)',
      },
    ];
    return this.computador() ? lista : [lista[2], lista[1], lista[0]];
  });

  constructor() {
    afterNextRender(() => this.dialogo().nativeElement.showModal());
  }

  protected endereco(via: ProgramaEmail): string {
    return enderecoDoEmail(via, this.pronto());
  }

  protected fechar() {
    this.dialogo().nativeElement.close();
  }

  /** Clicar no fundo escurecido fecha. */
  protected cliqueFora(evento: MouseEvent) {
    if (evento.target === this.dialogo().nativeElement) this.fechar();
  }

  protected corrigir() {
    const l = this.liderUnico();
    if (l) this.corrigirEmail.emit(l);
  }

  protected async alternarPrevia() {
    if (this.previa()) {
      this.previa.set(null);
      return;
    }
    this.carregandoPrevia.set(true);
    try {
      const l = this.liderUnico();
      const url = l ? `${API_RH}/empresas/${this.empresaId()}/lideres/${l.id}/email-previa` : `${API_RH}/empresas/${this.empresaId()}/lideres/email-previa`;
      const html = await firstValueFrom(this.http.get(url, { responseType: 'text' }));
      this.previa.set(this.sanitizador.bypassSecurityTrustHtml(html));
      // Leva a prévia para a vista (ela abre embaixo dos dois cartões).
      setTimeout(() => document.getElementById('previa-do-email')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }), 60);
    } catch {
      this.erro.set('Não foi possível abrir a prévia agora.');
    } finally {
      this.carregandoPrevia.set(false);
    }
  }

  protected async enviarPelaPlataforma() {
    if (this.enviando()) return;
    this.enviando.set(true);
    this.erro.set(null);
    const base = `${API_RH}/empresas/${this.empresaId()}/lideres`;
    try {
      const l = this.liderUnico();
      if (l) {
        const r = await firstValueFrom(this.http.post<{ para: string; tipo: 'CONVITE' | 'LEMBRETE' }>(`${base}/${l.id}/enviar`, null));
        this.feito.set({
          titulo: r.tipo === 'LEMBRETE' ? 'Lembrete enviado' : 'Convite enviado',
          texto: `Saiu para ${r.para}${r.tipo === 'LEMBRETE' ? ', com o botão para continuar de onde parou' : ', com o link pessoal'}. A entrega aparece na linha do tempo em instantes.`,
        });
      } else {
        const r = await firstValueFrom(this.http.post<{ convites: number; lembretes: number; deFora: number }>(`${base}/lembrar`, null));
        const partes = [
          r.lembretes ? `${r.lembretes} ${r.lembretes === 1 ? 'lembrete' : 'lembretes'}` : '',
          r.convites ? `${r.convites} ${r.convites === 1 ? 'convite' : 'convites'}` : '',
        ].filter(Boolean);
        this.feito.set({
          titulo: `Saindo: ${partes.join(' e ')}`,
          texto: `Em alguns minutos a entrega aparece na linha do tempo de cada um.${r.deFora ? ` ${r.deFora} ${r.deFora === 1 ? 'ficou' : 'ficaram'} de fora porque o e-mail voltou ou foi para o spam: mande pelo WhatsApp.` : ''}`,
        });
        // O envio em lote é em segundo plano: a lista se atualiza de novo logo depois.
        setTimeout(() => this.mudou.emit(), 5000);
      }
      this.estado.set('feito-plataforma');
      this.mudou.emit();
    } catch (e) {
      this.erro.set(problemaDe(e).detail ?? 'O e-mail não saiu agora. Tente de novo ou use o seu e-mail.');
    } finally {
      this.enviando.set(false);
    }
  }

  /** Abriu no Outlook, no Gmail ou no programa: registra e mostra o que fazer por lá. */
  protected abriu(via: ProgramaEmail) {
    this.registrar(via);
    this.feito.set({
      titulo: `Abrimos ${NOME_PROGRAMA[via]}`,
      texto:
        via === 'APP'
          ? 'A mensagem já vai escrita: confira e envie de lá. Se nada abriu, este computador não tem um programa de e-mail configurado: use o Outlook, o Gmail ou copie a mensagem.'
          : `A mensagem já vai escrita, em outra aba: confira e clique em Enviar por lá.`,
    });
    this.estado.set('feito-proprio');
  }

  protected async copiar() {
    const p = this.pronto();
    try {
      await navigator.clipboard.writeText(`Assunto: ${p.assunto}\n\n${p.corpo}`);
      this.registrar('COPIA');
      this.feito.set({
        titulo: 'Mensagem copiada',
        texto:
          this.alvo().tipo === 'um'
            ? `Cole num e-mail para ${this.liderUnico()!.email}: o assunto está na primeira linha.`
            : 'Cole num e-mail com os líderes em cópia oculta (use “Copiar os e-mails”): o assunto está na primeira linha.',
      });
      this.estado.set('feito-proprio');
    } catch {
      this.erro.set('Não deu para copiar. Use o Outlook, o Gmail ou o programa de e-mail.');
    }
  }

  protected async copiarEnderecos() {
    try {
      await navigator.clipboard.writeText(this.ocultos().join(', '));
      this.copiouEnderecos.set(true);
      setTimeout(() => this.copiouEnderecos.set(false), 2200);
    } catch {
      this.erro.set('Não deu para copiar os e-mails agora.');
    }
  }

  /** Fica na linha do tempo de cada líder que o RH abriu a mensagem (sem confirmação de envio). */
  private registrar(via: Via) {
    const ids = this.todos().map((l) => l.id);
    this.http
      .post(`${API_RH}/empresas/${this.empresaId()}/lideres/contato`, { ids, canal: 'EMAIL', via })
      .subscribe({ next: () => this.mudou.emit(), error: () => undefined });
  }
}
