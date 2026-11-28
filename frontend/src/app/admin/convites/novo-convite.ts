import { afterNextRender, Component, computed, ElementRef, inject, input, linkedSignal, output, signal, viewChild } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { problemaDe } from '../../api/diagnostico-api';
import { API_ADMIN, CargoOpcao } from '../admin-modelos';
import { problemaTelefone } from '../../compartilhado/entrada-telefone';
import { CampoRevisao, LinhaRevisao, mascarar, OUTRO_CARGO, paraEnvio, problemas } from './convites';

/** Os dados de um líder para editar (página do RH): o formulário abre preenchido e salva com PUT. */
export interface EdicaoLider {
  url: string;
  nome: string;
  email: string;
  whatsapp: string | null;
  cargoId: number | null;
  cargoOutro: boolean;
}

/**
 * "Adicionar líder": um convite feito no painel, para quem ficou de fora da planilha. Mesmas
 * regras da importação (e-mail válido e sem repetir, cargo da lista); recebe o e-mail de convite
 * quando o envio estiver liberado, como os outros. Também serve à página do RH: para cadastrar
 * (destino: a API do RH) e para editar um líder (edicao).
 */
@Component({
  selector: 'app-novo-convite',
  styleUrl: '../admin.css',
  template: `
    <dialog #dialogo class="dialogo" aria-labelledby="novo-convite-titulo" (close)="fechou.emit()">
      <form class="dialogo-corpo" novalidate (submit)="adicionar($event)">
        <div class="flex items-start justify-between gap-4">
          <div>
            <h2 id="novo-convite-titulo" class="display text-[28px] leading-tight font-bold">{{ edicao() ? 'Editar dados' : 'Adicionar líder' }}</h2>
            <p class="mt-1 text-[14px] text-abeto-2">
              {{ edicao() ? 'Os dados novos valem para o link pessoal e para os próximos e-mails.' : 'Para quem ficou de fora da planilha: recebe um link pessoal, como os outros.' }}
            </p>
          </div>
          <button type="button" class="-mt-1 -mr-2 grid size-11 shrink-0 place-items-center rounded-full text-abeto-2 hover:bg-nevoa hover:text-abeto" aria-label="Fechar" (click)="fechar()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" /></svg>
          </button>
        </div>

        <div class="mt-6 space-y-4">
          <div>
            <label class="campo-rotulo" for="novo-nome">Nome completo</label>
            <input id="novo-nome" class="campo" autocomplete="off" autocapitalize="words" [value]="dados().nome" [attr.aria-invalid]="!!erro('nome')" aria-describedby="novo-nome-erro" (input)="mudar('nome', $any($event.target).value)" (blur)="tocar('nome')" />
            <p id="novo-nome-erro" class="mt-1 min-h-5 text-[14px] font-medium text-travada">{{ erro('nome') }}</p>
          </div>
          <div>
            <label class="campo-rotulo" for="novo-email">E-mail</label>
            <input id="novo-email" class="campo" type="email" inputmode="email" autocomplete="off" autocapitalize="none" spellcheck="false" [value]="dados().email" [attr.aria-invalid]="!!erro('email')" aria-describedby="novo-email-erro" (input)="mudar('email', $any($event.target).value)" (blur)="tocar('email')" />
            <p id="novo-email-erro" class="mt-1 min-h-5 text-[14px] font-medium text-travada">{{ erro('email') }}</p>
          </div>
          <div class="grid gap-4" [class.sm:grid-cols-2]="cargos().length">
            <div>
              <label class="campo-rotulo" for="novo-whatsapp">WhatsApp (opcional)</label>
              <input id="novo-whatsapp" class="campo" type="tel" inputmode="tel" autocomplete="off" placeholder="(11) 98765-4321" [value]="dados().whatsapp" [attr.aria-invalid]="!!erro('whatsapp')" aria-describedby="novo-whatsapp-erro" (input)="mudar('whatsapp', mascarar($event))" (blur)="tocar('whatsapp')" />
              <p id="novo-whatsapp-erro" class="mt-1 min-h-5 text-[14px] font-medium text-travada" aria-live="polite">{{ erro('whatsapp') }}</p>
            </div>
            @if (cargos().length) {
              <div>
                <label class="campo-rotulo" for="novo-cargo">Cargo</label>
                <select id="novo-cargo" class="campo" [attr.aria-invalid]="!!erro('cargo')" aria-describedby="novo-cargo-erro" (change)="mudar('cargo', $any($event.target).value)">
                  <option value="" [selected]="!dados().cargo">Escolha o cargo</option>
                  @for (c of cargos(); track c.id) {
                    <option [value]="'' + c.id" [selected]="dados().cargo === '' + c.id">{{ c.nome }}</option>
                  }
                  <option [value]="OUTRO" [selected]="dados().cargo === OUTRO">Outro cargo</option>
                </select>
                <p id="novo-cargo-erro" class="mt-1 min-h-5 text-[14px] font-medium text-travada">{{ erro('cargo') }}</p>
              </div>
            }
          </div>
        </div>

        <p class="min-h-5 text-[14px] font-medium text-travada" aria-live="assertive">{{ erroGeral() }}</p>
        <div class="mt-3 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button type="button" class="botao-secundario" (click)="fechar()">Cancelar</button>
          <button type="submit" class="botao-primario" [attr.aria-busy]="enviando()" [disabled]="enviando()">
            {{ edicao() ? (enviando() ? 'Salvando…' : 'Salvar') : enviando() ? 'Adicionando…' : 'Adicionar líder' }}
          </button>
        </div>
      </form>
    </dialog>
  `,
})
export class NovoConvite {
  private readonly http = inject(HttpClient);
  private readonly dialogo = viewChild.required<ElementRef<HTMLDialogElement>>('dialogo');

  readonly empresaId = input.required<number>();
  readonly cargos = input<CargoOpcao[]>([]);
  /** Para onde vai o cadastro (padrão: o painel do administrador). */
  readonly destino = input<string | null>(null);
  /** Editar um líder em vez de cadastrar: o formulário abre com os dados dele. */
  readonly edicao = input<EdicaoLider | null>(null);
  readonly adicionado = output<string>();
  readonly fechou = output();

  protected readonly OUTRO = OUTRO_CARGO;
  protected readonly mascarar = mascarar;
  protected readonly dados = linkedSignal<LinhaRevisao>(() => {
    const e = this.edicao();
    return {
      chave: 'novo',
      linha: 0,
      nome: e?.nome ?? '',
      email: e?.email ?? '',
      whatsapp: e?.whatsapp ?? '',
      cargo: e ? (e.cargoOutro ? OUTRO_CARGO : e.cargoId != null ? String(e.cargoId) : '') : '',
      cargoTexto: '',
      sugestaoCargoId: null,
      jaConvidado: false,
      exemplo: false,
    };
  });
  private readonly tentou = signal(false);
  /** Campos que a pessoa já deixou: o erro deles aparece sem esperar o "Adicionar". */
  private readonly tocados = signal<ReadonlySet<CampoRevisao>>(new Set());
  protected readonly enviando = signal(false);
  protected readonly erroGeral = signal<string | null>(null);
  private readonly erroServidor = signal<string | null>(null);
  private readonly lista = computed(() => problemas(this.dados(), new Set(), this.cargos().length > 0));

  constructor() {
    afterNextRender(() => {
      this.dialogo().nativeElement.showModal();
      setTimeout(() => document.getElementById('novo-nome')?.focus(), 0);
    });
  }

  protected erro(campo: CampoRevisao): string | null {
    if (campo === 'email' && this.erroServidor()) return this.erroServidor();
    if (this.tentou() || this.tocados().has(campo)) return this.lista().find((p) => p.campo === campo)?.mensagem ?? null;
    // Ainda digitando o WhatsApp: só o que já é certeza (DDD que não existe, celular sem o 9).
    return campo === 'whatsapp' ? problemaTelefone(this.dados().whatsapp, true) : null;
  }

  protected tocar(campo: CampoRevisao) {
    // Campo vazio ao sair não é erro ainda: o "Adicionar" cobra.
    const valor = this.dados()[campo];
    if (!valor.trim() || this.tocados().has(campo)) return;
    this.tocados.update((s) => new Set(s).add(campo));
  }

  protected mudar(campo: CampoRevisao, valor: string) {
    this.dados.update((d) => ({ ...d, [campo]: valor }));
    if (campo === 'email') this.erroServidor.set(null);
    this.erroGeral.set(null);
  }

  protected async adicionar(evento: Event) {
    evento.preventDefault();
    if (this.enviando()) return;
    this.tentou.set(true);
    const primeiro = this.lista()[0];
    if (primeiro) {
      document.getElementById(`novo-${primeiro.campo}`)?.focus();
      return;
    }
    this.enviando.set(true);
    try {
      const edicao = this.edicao();
      await firstValueFrom(
        edicao
          ? this.http.put(edicao.url, paraEnvio(this.dados()))
          : this.http.post(this.destino() ?? `${API_ADMIN}/empresas/${this.empresaId()}/convites`, {
              convites: [paraEnvio(this.dados())],
              origem: 'painel',
            }),
      );
      this.adicionado.emit(this.dados().nome.trim());
      this.dialogo().nativeElement.close();
    } catch (e) {
      const problema = problemaDe(e) as { detail?: string; linhas?: Record<string, string>; campos?: Record<string, string> };
      const daLinha = problema.linhas?.['0'] ?? problema.campos?.['email'];
      if (daLinha) {
        this.erroServidor.set(daLinha);
        document.getElementById('novo-email')?.focus();
      } else {
        this.erroGeral.set(problema.detail ?? 'Não foi possível adicionar agora. Tente de novo.');
      }
    } finally {
      this.enviando.set(false);
    }
  }

  protected fechar() {
    this.dialogo().nativeElement.close();
  }
}
