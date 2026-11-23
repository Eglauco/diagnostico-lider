import { Component, computed, effect, ElementRef, inject, linkedSignal, signal, untracked, viewChild } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { FormField, form, maxLength, required, validate } from '@angular/forms/signals';
import { firstValueFrom } from 'rxjs';

import { problemaDe } from '../api/diagnostico-api';
import { vibrar } from '../compartilhado/util';
import { API_ADMIN, Disponibilidade, EmpresaDetalhe } from './admin-modelos';
import { CODIGO_MINIMO, codigoDigitando, normalizarCodigo } from './codigo-campanha';
import { CargoEditavel, cargosParaEnvio, cargosSugeridos } from './cargos/cargos';
import { SecaoCargos } from './cargos/secao-cargos';
import { normalizarHex, siglaDe, usarPreviaDeCores } from './cores/cores';
import { PreviaMarca } from './cores/previa-marca';
import { SecaoCores } from './cores/secao-cores';
import { CampoLogo, MudancaLogo } from './logo/campo-logo';
import { logoEmPrevia, salvarLogo } from './logo/salvar-logo';
import { QrSvg } from './qr/qr-svg';

type Campo = 'nome' | 'codigo' | 'corPrimaria' | 'corSecundaria' | 'cargos';

/**
 * Cadastro de empresa. O endereço do link sai do nome enquanto você digita (e pode ser
 * trocado); a prévia mostra o link e o QR Code antes de criar.
 */
@Component({
  selector: 'app-admin-nova-empresa',
  imports: [CampoLogo, FormField, PreviaMarca, QrSvg, RouterLink, SecaoCargos, SecaoCores],
  templateUrl: './admin-nova-empresa.html',
  styleUrl: './admin.css',
  host: { class: 'block' },
})
export class AdminNovaEmpresa {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly campoNome = viewChild<ElementRef<HTMLInputElement>>('campoNome');
  private readonly campoCodigo = viewChild<ElementRef<HTMLInputElement>>('campoCodigo');

  protected readonly modelo = signal({ nome: '', codigo: '' });
  protected readonly formulario = form(this.modelo, (c) => {
    required(c.nome, { message: 'Escreva o nome da empresa.' });
    maxLength(c.nome, 160, { message: 'Use até 160 caracteres.' });
    validate(c.codigo, ({ value }) =>
      normalizarCodigo(value()).length < CODIGO_MINIMO
        ? { kind: 'codigo-curto', message: 'Use pelo menos 3 letras ou números no endereço.' }
        : null,
    );
  });

  /** Até a pessoa mexer no endereço, ele acompanha o nome. */
  private readonly codigoEditado = signal(false);
  protected readonly codigo = computed(() => normalizarCodigo(this.modelo().codigo));

  private readonly codigoConferido = signal('');
  protected readonly disponibilidade = httpResource<Disponibilidade>(() => {
    const codigo = this.codigoConferido();
    return codigo.length >= CODIGO_MINIMO
      ? { url: `${API_ADMIN}/empresas/disponibilidade`, params: { codigo } }
      : undefined;
  });
  private readonly resposta = computed(() =>
    this.disponibilidade.hasValue() ? this.disponibilidade.value() : undefined,
  );

  /** Endereço público vem do servidor (APP_URL_PUBLICA); até lá, o desta página. */
  private readonly base = linkedSignal<Disponibilidade | undefined, string>({
    source: this.resposta,
    computation: (d, anterior) => (d ? d.url.slice(0, d.url.lastIndexOf('/c/')) : (anterior?.value ?? location.origin)),
  });
  protected readonly urlPrevia = computed(() => `${this.base()}/c/${this.codigo() || 'sua-empresa'}`);
  protected readonly enderecoCurto = computed(() => this.urlPrevia().replace(/^https?:\/\//, ''));

  protected readonly situacaoCodigo = computed<'curto' | 'conferindo' | 'livre' | 'ocupado' | null>(() => {
    const codigo = this.codigo();
    if (codigo.length < CODIGO_MINIMO) return codigo.length ? 'curto' : null;
    const d = this.resposta();
    if (this.codigoConferido() !== codigo || this.disponibilidade.isLoading()) return 'conferindo';
    if (!d || d.codigo !== codigo) return null;
    return d.disponivel ? 'livre' : 'ocupado';
  });
  protected readonly sugestao = computed(() => this.resposta()?.sugestao ?? null);

  // Cores da marca -----------------------------------------------------------
  protected readonly corPrimaria = signal('');
  protected readonly corSecundaria = signal('');
  protected readonly previa = usarPreviaDeCores(this.corPrimaria, this.corSecundaria);
  protected readonly sigla = computed(() => siglaDe(this.modelo().nome || 'Nome da empresa'));

  // Logomarca (enviada depois que a empresa é criada) -------------------------
  protected readonly logo = signal<MudancaLogo>(null);
  protected readonly logoPrevia = computed(() => logoEmPrevia(this.logo(), null));

  protected usarCoresDoLogo(cores: string[]) {
    this.corPrimaria.set(cores[0] ?? '');
    this.corSecundaria.set(cores[1] ?? '');
  }

  // Cargos: começa com a lista sugerida, para editar em vez de partir do zero ---
  protected readonly cargos = signal<CargoEditavel[]>(cargosSugeridos());

  // Envio --------------------------------------------------------------------
  protected readonly enviando = signal(false);
  protected readonly erro = signal<string | null>(null);
  private readonly tentou = signal(false);
  private readonly errosServidor = signal<Partial<Record<Campo, string>>>({});

  protected readonly erroNome = computed(
    () =>
      this.errosServidor().nome ?? (this.tentou() ? (this.formulario.nome().errors()[0]?.message ?? null) : null),
  );
  protected readonly erroCodigo = computed(() => {
    if (this.errosServidor().codigo) return this.errosServidor().codigo!;
    if (!this.tentou()) return null;
    if (this.situacaoCodigo() === 'ocupado') return 'Esse endereço já está em uso.';
    return this.formulario.codigo().errors()[0]?.message ?? null;
  });

  constructor() {
    effect(() => {
      const nome = this.modelo().nome;
      if (untracked(this.codigoEditado)) return;
      const codigo = normalizarCodigo(nome);
      if (untracked(this.modelo).codigo !== codigo) this.modelo.update((m) => ({ ...m, codigo }));
    });

    // Confere o endereço com uma pequena espera, para não consultar a cada tecla.
    effect((onCleanup) => {
      const codigo = this.codigo();
      const espera = setTimeout(() => this.codigoConferido.set(codigo), 300);
      onCleanup(() => clearTimeout(espera));
    });
  }

  protected editou(campo: Campo) {
    if (this.errosServidor()[campo]) this.errosServidor.update(({ [campo]: _, ...resto }) => resto);
    this.erro.set(null);
  }

  protected digitouCodigo(evento: Event) {
    this.codigoEditado.set(true);
    this.editou('codigo');
    const valor = (evento.target as HTMLInputElement).value;
    const ajustado = codigoDigitando(valor);
    if (ajustado !== valor) this.modelo.update((m) => ({ ...m, codigo: ajustado }));
  }

  protected ajustarCodigo() {
    const codigo = this.codigo();
    if (codigo !== this.modelo().codigo) this.modelo.update((m) => ({ ...m, codigo }));
  }

  protected usarSugestao() {
    const sugestao = this.sugestao();
    if (!sugestao) return;
    this.codigoEditado.set(true);
    this.editou('codigo');
    this.modelo.update((m) => ({ ...m, codigo: sugestao }));
  }

  protected async cadastrar(evento: Event) {
    evento.preventDefault();
    if (this.enviando()) return;
    this.tentou.set(true);
    this.ajustarCodigo();
    if (this.erroNome()) return this.recusar('nome');
    if (this.erroCodigo()) return this.recusar('codigo');
    if (this.corPrimaria() && !normalizarHex(this.corPrimaria())) return this.recusar('corPrimaria');
    if (this.corSecundaria() && !normalizarHex(this.corSecundaria())) return this.recusar('corSecundaria');

    this.enviando.set(true);
    this.erro.set(null);
    try {
      const empresa = await firstValueFrom(
        this.http.post<EmpresaDetalhe>(`${API_ADMIN}/empresas`, {
          nome: this.modelo().nome.trim(),
          codigo: this.codigo(),
          corPrimaria: normalizarHex(this.corPrimaria()),
          corSecundaria: normalizarHex(this.corSecundaria()),
          cargos: cargosParaEnvio(this.cargos()),
        }),
      );
      try {
        await salvarLogo(this.http, empresa.id, this.logo());
      } catch (e) {
        // A empresa já existe: segue para a edição dela, com o aviso, para tentar a logo de novo.
        await this.router.navigate(['/administrador/empresas', empresa.id, 'editar'], {
          replaceUrl: true,
          state: { erroLogo: problemaDe(e).detail ?? 'A empresa foi cadastrada, mas a logomarca não foi enviada. Tente de novo.' },
        });
        return;
      }
      // Substitui o formulário no histórico: "voltar" da página da empresa leva à lista.
      await this.router.navigate(['/administrador/empresas', empresa.id], { replaceUrl: true, state: { criada: true } });
    } catch (e) {
      const problema = problemaDe(e);
      const campos = (problema.campos ?? {}) as Partial<Record<Campo, string>>;
      const campo = (['nome', 'codigo', 'corPrimaria', 'corSecundaria', 'cargos'] as Campo[]).find((c) => campos[c]);
      if (campo) {
        this.errosServidor.set(campos);
        this.recusar(campo);
      } else {
        this.erro.set(problema.detail ?? 'Não foi possível cadastrar agora. Tente de novo.');
      }
    } finally {
      this.enviando.set(false);
    }
  }

  protected erroDoServidor(campo: Campo): string | null {
    return this.errosServidor()[campo] ?? null;
  }

  private recusar(campo: Campo) {
    vibrar(30);
    if (campo === 'nome') this.campoNome()?.nativeElement.focus();
    else if (campo === 'codigo') this.campoCodigo()?.nativeElement.focus();
    else if (campo === 'cargos') document.getElementById('cargos-secao')?.focus();
    else document.getElementById(campo === 'corPrimaria' ? 'cor-primaria' : 'cor-secundaria')?.focus();
  }
}
