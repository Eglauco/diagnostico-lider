import { afterNextRender, Component, computed, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { problemaDe } from '../../api/diagnostico-api';
import { API_ADMIN, CargoOpcao, Importacao } from '../admin-modelos';
import {
  CampoRevisao,
  emailsRepetidos,
  foraDaLista,
  LinhaRevisao,
  linhaDeRevisao,
  OUTRO_CARGO,
  paraEnvio,
  problemas,
  Problema,
  mascarar,
} from './convites';

type Filtro = 'todas' | 'ajustar' | 'fora';

interface Avaliada {
  linha: LinhaRevisao;
  fora: string | null;
  problemas: Problema[];
}

/**
 * "Revise os líderes": tudo o que veio da planilha antes de virar convite. As linhas aparecem
 * como texto (leve até com milhares); a que tem problema mostra o que falta e, no cargo, a
 * sugestão de um toque. Qualquer linha pode ser editada ou removida ali mesmo.
 */
@Component({
  selector: 'app-revisao-importacao',
  styleUrl: '../admin.css',
  template: `
    <dialog #dialogo class="dialogo dialogo-largo" aria-labelledby="revisao-titulo" (close)="fechou.emit()">
      <div class="flex max-h-[inherit] flex-col">
        <header class="flex items-start justify-between gap-4 border-b border-linha px-5 pt-5 pb-4 sm:px-7 sm:pt-6">
          <div class="min-w-0">
            <h2 id="revisao-titulo" class="display text-[26px] leading-tight font-bold sm:text-[30px]">Revise os líderes</h2>
            <p class="mt-1 truncate text-[14px] text-abeto-2">
              {{ importacao().arquivo }} · {{ linhas().length }} {{ linhas().length === 1 ? 'linha' : 'linhas' }}
            </p>
          </div>
          <button type="button" class="-mt-1 -mr-2 grid size-11 shrink-0 place-items-center rounded-full text-abeto-2 hover:bg-nevoa hover:text-abeto" aria-label="Fechar sem convidar" (click)="fechar()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" /></svg>
          </button>
        </header>

        <div class="flex gap-2 overflow-x-auto px-5 py-3 [scrollbar-width:none] sm:px-7" role="group" aria-label="Mostrar">
          <button type="button" class="chip" [attr.aria-pressed]="filtro() === 'todas'" (click)="filtro.set('todas')">
            Todas <span class="contagem">{{ linhas().length }}</span>
          </button>
          <button type="button" class="chip" [attr.aria-pressed]="filtro() === 'ajustar'" (click)="filtro.set('ajustar')">
            Para ajustar <span class="contagem">{{ comErro().length }}</span>
          </button>
          @if (fora().length) {
            <button type="button" class="chip" [attr.aria-pressed]="filtro() === 'fora'" (click)="filtro.set('fora')">
              Ficam de fora <span class="contagem">{{ fora().length }}</span>
            </button>
          }
        </div>

        <ol class="min-h-0 flex-1 space-y-2 overflow-y-auto overscroll-contain px-5 pb-4 sm:px-7">
          @for (a of visiveis(); track a.linha.chave) {
            @let l = a.linha;
            <li class="rounded-2xl px-4 py-3 ring-1" [class]="a.fora ? 'bg-nevoa-2 ring-linha/70' : a.problemas.length ? 'bg-[#FFF6F7] ring-[#F0C7CE]' : 'bg-white ring-linha'">
              <div class="flex items-start gap-3">
                <span class="mt-0.5 grid size-6 shrink-0 place-items-center rounded-full text-white" [class]="a.fora ? 'bg-abeto-3' : a.problemas.length ? 'bg-travada' : 'bg-ascensao'" aria-hidden="true">
                  @if (a.fora) {
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.4"><path d="M6 12h12" stroke-linecap="round" /></svg>
                  } @else if (a.problemas.length) {
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.4"><path d="M12 6v8M12 18v.5" stroke-linecap="round" /></svg>
                  } @else {
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.4"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
                  }
                </span>
                <span class="sr-only">{{ a.fora ? 'Fica de fora.' : a.problemas.length ? 'Precisa de ajuste.' : 'Pronta.' }}</span>

                <div class="min-w-0 flex-1">
                  @if (editando() === l.chave) {
                    <div class="grid gap-3 sm:grid-cols-2">
                      <div>
                        <label class="campo-rotulo" [for]="l.chave + '-nome'">Nome completo</label>
                        <input class="campo" [id]="l.chave + '-nome'" [value]="l.nome" autocomplete="off" [attr.aria-invalid]="temProblema(a, 'nome')" (input)="mudar(l.chave, 'nome', $any($event.target).value)" />
                      </div>
                      <div>
                        <label class="campo-rotulo" [for]="l.chave + '-email'">E-mail</label>
                        <input class="campo" type="email" inputmode="email" autocapitalize="none" spellcheck="false" [id]="l.chave + '-email'" [value]="l.email" [attr.aria-invalid]="temProblema(a, 'email')" (input)="mudar(l.chave, 'email', $any($event.target).value)" />
                      </div>
                      <div>
                        <label class="campo-rotulo" [for]="l.chave + '-whatsapp'">WhatsApp (opcional)</label>
                        <input class="campo" type="tel" inputmode="tel" [id]="l.chave + '-whatsapp'" [value]="l.whatsapp" placeholder="(11) 98765-4321" [attr.aria-invalid]="temProblema(a, 'whatsapp')" (input)="mudar(l.chave, 'whatsapp', mascarar($event))" />
                      </div>
                      @if (cargos().length) {
                        <div>
                          <label class="campo-rotulo" [for]="l.chave + '-cargo'">Cargo</label>
                          <select class="campo" [id]="l.chave + '-cargo'" [value]="l.cargo" [attr.aria-invalid]="temProblema(a, 'cargo')" (change)="mudar(l.chave, 'cargo', $any($event.target).value)">
                            <option value="">Escolha o cargo</option>
                            @for (c of cargos(); track c.id) {
                              <option [value]="'' + c.id" [selected]="l.cargo === '' + c.id">{{ c.nome }}</option>
                            }
                            <option [value]="OUTRO" [selected]="l.cargo === OUTRO">Outro cargo</option>
                          </select>
                        </div>
                      }
                    </div>
                  } @else {
                    <p class="text-[15px] leading-snug font-semibold break-words">{{ l.nome || 'Sem nome' }}</p>
                    <p class="mt-0.5 text-[14px] break-all text-abeto-2">{{ l.email || 'Sem e-mail' }}{{ l.whatsapp ? ' · ' + l.whatsapp : '' }}</p>
                    <p class="mt-0.5 text-[14px] text-abeto-2">
                      {{ nomeCargo(l) }}
                      <span class="text-abeto-3"> · linha {{ l.linha }}</span>
                    </p>
                  }

                  @if (a.fora) {
                    <p class="mt-2 text-[14px] text-abeto-2">{{ a.fora }}</p>
                  }
                  @for (p of a.problemas; track $index) {
                    <div class="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-[14px]">
                      <p class="font-medium text-travada">{{ p.mensagem }}</p>
                      @if (p.campo === 'cargo' && l.sugestaoCargoId; as sugestao) {
                        <button type="button" class="botao-sugestao" (click)="mudar(l.chave, 'cargo', '' + sugestao)">
                          Usar “{{ nomeDoCargo(sugestao) }}”
                        </button>
                      }
                    </div>
                  }
                </div>

                <div class="-my-1 -mr-2 flex shrink-0">
                  @if (!a.fora) {
                    <button type="button" class="botao-icone-linha" [attr.aria-label]="(editando() === l.chave ? 'Concluir edição de ' : 'Editar ') + (l.nome || 'linha ' + l.linha)" (click)="alternarEdicao(l.chave)">
                      @if (editando() === l.chave) {
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
                      } @else {
                        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" aria-hidden="true"><path d="M4 20h4L19 9l-4-4L4 16v4zM13.5 6.5l4 4" stroke-linecap="round" stroke-linejoin="round" /></svg>
                      }
                    </button>
                  }
                  <button type="button" class="botao-icone-linha hover:text-travada" [attr.aria-label]="'Remover ' + (l.nome || 'linha ' + l.linha) + ' da lista'" (click)="remover(l.chave)">
                    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" /></svg>
                  </button>
                </div>
              </div>
            </li>
          } @empty {
            <li class="rounded-2xl bg-nevoa-2 px-4 py-6 text-center text-[15px] text-abeto-2">
              {{ filtro() === 'ajustar' ? 'Nenhuma linha precisa de ajuste.' : 'Nada para mostrar aqui.' }}
            </li>
          }
        </ol>

        <footer class="border-t border-linha px-5 pt-4 pb-[max(16px,env(safe-area-inset-bottom))] sm:px-7 sm:pb-6">
          <p class="text-[14px] text-abeto-2" aria-live="polite">
            @if (comErro().length) {
              <span class="font-medium text-travada">{{ comErro().length === 1 ? '1 linha com problema fica' : comErro().length + ' linhas com problema ficam' }} de fora até ser{{ comErro().length === 1 ? '' : 'em' }} corrigida{{ comErro().length === 1 ? '' : 's' }}.</span>
            } @else if (prontas().length) {
              Tudo certo. Os convites ficam prontos para envio por e-mail.
            }
          </p>
          <p class="min-h-5 text-[14px] font-medium text-travada" aria-live="assertive">{{ erro() }}</p>
          <div class="mt-2 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <button type="button" class="botao-secundario" (click)="fechar()">Cancelar</button>
            <button type="button" class="botao-primario" [attr.aria-busy]="enviando()" [disabled]="enviando() || !prontas().length" (click)="convidar()">
              @if (enviando()) {
                <span class="size-5 animate-spin rounded-full border-2 border-white/30 border-t-white" aria-hidden="true"></span>
                Criando os convites…
              } @else {
                Convidar {{ prontas().length }} {{ prontas().length === 1 ? 'líder' : 'líderes' }}
              }
            </button>
          </div>
        </footer>
      </div>
    </dialog>
  `,
  styles: `
    .botao-icone-linha {
      display: grid;
      place-items: center;
      width: 44px;
      height: 44px;
      border-radius: 14px;
      color: var(--color-abeto-2);
    }
    .botao-icone-linha:hover {
      background: var(--color-nevoa-2);
      color: var(--color-abeto);
    }
    .botao-sugestao {
      min-height: 36px;
      padding: 0 12px;
      border-radius: 999px;
      background: var(--color-abeto);
      color: #fff;
      font-size: 14px;
      font-weight: 600;
    }
  `,
})
export class RevisaoImportacao {
  private readonly http = inject(HttpClient);
  private readonly dialogo = viewChild.required<ElementRef<HTMLDialogElement>>('dialogo');

  readonly empresaId = input.required<number>();
  readonly importacao = input.required<Importacao>();
  readonly criados = output<number>();
  readonly fechou = output();

  protected readonly OUTRO = OUTRO_CARGO;
  protected readonly mascarar = mascarar;
  protected readonly cargos = computed<CargoOpcao[]>(() => this.importacao().cargos);
  protected readonly linhas = signal<LinhaRevisao[]>([]);
  protected readonly filtro = signal<Filtro>('todas');
  protected readonly editando = signal<string | null>(null);
  protected readonly enviando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly errosServidor = signal<Record<string, string>>({});

  private readonly avaliadas = computed<Avaliada[]>(() => {
    const linhas = this.linhas();
    const ativas = linhas.filter((l) => !foraDaLista(l));
    const repetidos = emailsRepetidos(ativas);
    const temCargos = this.cargos().length > 0;
    const doServidor = this.errosServidor();
    return linhas.map((linha) => {
      const fora = foraDaLista(linha);
      if (fora) return { linha, fora, problemas: [] };
      // O que o servidor recusou (ex.: e-mail que acabou de ganhar convite) também é ajuste.
      const servidor = doServidor[linha.chave];
      const lista = problemas(linha, repetidos, temCargos);
      return { linha, fora, problemas: servidor ? [...lista, { campo: 'email' as const, mensagem: servidor }] : lista };
    });
  });
  protected readonly prontas = computed(() => this.avaliadas().filter((a) => !a.fora && !a.problemas.length));
  protected readonly comErro = computed(() => this.avaliadas().filter((a) => !a.fora && a.problemas.length));
  protected readonly fora = computed(() => this.avaliadas().filter((a) => a.fora));
  protected readonly visiveis = computed(() => {
    switch (this.filtro()) {
      case 'ajustar':
        return this.comErro();
      case 'fora':
        return this.fora();
      default:
        return this.avaliadas();
    }
  });

  constructor() {
    afterNextRender(() => {
      this.linhas.set(this.importacao().linhas.map(linhaDeRevisao));
      // Com algo para ajustar, abre direto nelas: é por onde a pessoa vai começar.
      if (this.comErro().length) this.filtro.set('ajustar');
      this.dialogo().nativeElement.showModal();
    });
  }

  protected nomeDoCargo(id: number): string {
    return this.cargos().find((c) => c.id === id)?.nome ?? '';
  }

  protected nomeCargo(l: LinhaRevisao): string {
    if (l.cargo === OUTRO_CARGO) return 'Outro cargo';
    if (l.cargo) return this.nomeDoCargo(Number(l.cargo));
    return this.cargos().length ? (l.cargoTexto ? `Cargo: ${l.cargoTexto}` : 'Sem cargo') : 'Sem cargo';
  }

  protected temProblema(a: Avaliada, campo: CampoRevisao): boolean {
    return a.problemas.some((p) => p.campo === campo);
  }

  protected mudar(chave: string, campo: CampoRevisao, valor: string) {
    this.linhas.update((lista) => lista.map((l) => (l.chave === chave ? { ...l, [campo]: valor } : l)));
    this.errosServidor.update(({ [chave]: _, ...resto }) => resto);
    this.erro.set(null);
  }

  protected alternarEdicao(chave: string) {
    this.editando.update((atual) => (atual === chave ? null : chave));
    if (this.editando()) setTimeout(() => document.getElementById(`${chave}-nome`)?.focus(), 0);
  }

  protected remover(chave: string) {
    this.linhas.update((lista) => lista.filter((l) => l.chave !== chave));
    if (this.editando() === chave) this.editando.set(null);
  }

  protected async convidar() {
    const prontas = this.prontas().map((a) => a.linha);
    if (!prontas.length || this.enviando()) return;
    this.enviando.set(true);
    this.erro.set(null);
    try {
      const r = await firstValueFrom(
        this.http.post<{ criados: number }>(`${API_ADMIN}/empresas/${this.empresaId()}/convites`, {
          convites: prontas.map(paraEnvio),
          origem: 'planilha',
        }),
      );
      this.criados.emit(r.criados);
      this.dialogo().nativeElement.close();
    } catch (e) {
      const problema = problemaDe(e) as { detail?: string; linhas?: Record<string, string> };
      if (problema.linhas) {
        // O servidor responde pela posição na lista enviada: volta para a linha certa.
        const erros: Record<string, string> = {};
        Object.entries(problema.linhas).forEach(([i, mensagem]) => {
          const linha = prontas[Number(i)];
          if (linha) erros[linha.chave] = mensagem;
        });
        this.errosServidor.set(erros);
      }
      this.erro.set(problema.detail ?? 'Não foi possível criar os convites agora. Tente de novo.');
    } finally {
      this.enviando.set(false);
    }
  }

  protected fechar() {
    this.dialogo().nativeElement.close();
  }
}
