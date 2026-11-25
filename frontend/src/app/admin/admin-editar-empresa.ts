import { Component, computed, effect, ElementRef, inject, input, signal, untracked, viewChild } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { Location } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { form, FormField, maxLength, required } from '@angular/forms/signals';
import { firstValueFrom } from 'rxjs';

import { problemaDe } from '../api/diagnostico-api';
import { vibrar } from '../compartilhado/util';
import { API_ADMIN, EmpresaDetalhe } from './admin-modelos';
import { CargoEditavel, cargosDaEmpresa, cargosParaEnvio } from './cargos/cargos';
import { SecaoCargos } from './cargos/secao-cargos';
import { SecaoEncerramento } from './encerramento/secao-encerramento';
import { errosDeGestores, gestoresParaEnvio, problemaGestores, SecaoGestores } from './gestores/secao-gestores';
import { normalizarHex, siglaDe, usarPreviaDeCores } from './cores/cores';
import { PreviaMarca } from './cores/previa-marca';
import { SecaoCores } from './cores/secao-cores';
import { CampoLogo, MudancaLogo } from './logo/campo-logo';
import { logoEmPrevia, salvarLogo } from './logo/salvar-logo';

type Campo = 'nome' | 'corPrimaria' | 'corSecundaria' | 'cargos';

/** Editar nome, logo, cores, cargos e o encerramento da avaliação. O link fica como está: pode já estar impresso nos materiais. */
@Component({
  selector: 'app-admin-editar-empresa',
  imports: [CampoLogo, FormField, PreviaMarca, RouterLink, SecaoCargos, SecaoCores, SecaoEncerramento, SecaoGestores],
  templateUrl: './admin-editar-empresa.html',
  styleUrl: './admin.css',
  host: { class: 'block' },
})
export class AdminEditarEmpresa {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly rota = inject(ActivatedRoute);
  private readonly campoNome = viewChild<ElementRef<HTMLInputElement>>('campoNome');

  readonly id = input.required<string>();
  protected readonly empresa = httpResource<EmpresaDetalhe>(() => `${API_ADMIN}/empresas/${this.id()}`);

  protected readonly modelo = signal({ nome: '' });
  protected readonly formulario = form(this.modelo, (c) => {
    required(c.nome, { message: 'Escreva o nome da empresa.' });
    maxLength(c.nome, 160, { message: 'Use até 160 caracteres.' });
  });
  protected readonly corPrimaria = signal('');
  protected readonly corSecundaria = signal('');
  protected readonly previa = usarPreviaDeCores(this.corPrimaria, this.corSecundaria);
  protected readonly sigla = computed(() => siglaDe(this.modelo().nome || 'Empresa'));

  protected readonly cargos = signal<CargoEditavel[]>([]);
  /** Encerramento da avaliação (ISO; null: sem data). */
  protected readonly encerraEm = signal<string | null>(null);
  /** E-mails dos gestores que recebem os relatórios (pode ficar vazio). */
  protected readonly gestores = signal<string[]>([]);
  protected readonly errosGestores = signal<Record<number, string>>({});

  protected readonly logo = signal<MudancaLogo>(null);
  protected readonly logoAtual = computed(() => (this.empresa.hasValue() ? this.empresa.value().logo : null));
  protected readonly logoPrevia = computed(() => logoEmPrevia(this.logo(), this.logoAtual()));

  protected usarCoresDoLogo(cores: string[]) {
    this.corPrimaria.set(cores[0] ?? '');
    this.corSecundaria.set(cores[1] ?? '');
  }

  protected readonly carregado = signal(false);
  protected readonly enviando = signal(false);
  protected readonly tentou = signal(false);
  protected readonly erro = signal<string | null>(null);
  private readonly errosServidor = signal<Partial<Record<Campo, string>>>({});

  protected readonly erroNome = computed(
    () => this.errosServidor().nome ?? (this.tentou() ? (this.formulario.nome().errors()[0]?.message ?? null) : null),
  );

  constructor() {
    // Veio do cadastro com a logo recusada: mostra o motivo aqui.
    const estado = inject(Location).getState() as { erroLogo?: string } | null;
    if (estado?.erroLogo) this.erro.set(estado.erroLogo);

    // Preenche o formulário uma vez, quando os dados chegam.
    effect(() => {
      if (!this.empresa.hasValue() || untracked(this.carregado)) return;
      const e = this.empresa.value();
      this.modelo.set({ nome: e.nome });
      this.corPrimaria.set(e.cores.primaria ?? '');
      this.corSecundaria.set(e.cores.secundaria ?? '');
      this.cargos.set(cargosDaEmpresa(e.cargos));
      this.encerraEm.set(e.encerramento.em);
      this.gestores.set(e.gestores);
      this.carregado.set(true);
      // Veio de um atalho (cargos, encerramento ou gestores): vai direto para a seção.
      const secao = this.rota.snapshot.fragment;
      if (secao === 'cargos-secao' || secao === 'encerramento-secao' || secao === 'gestores-secao') {
        setTimeout(() => document.getElementById(secao)?.scrollIntoView({ block: 'start' }), 150);
      }
    });
  }

  protected erroDoServidor(campo: Campo): string | null {
    return this.errosServidor()[campo] ?? null;
  }

  protected editou(campo: Campo) {
    if (this.errosServidor()[campo]) this.errosServidor.update(({ [campo]: _, ...resto }) => resto);
    this.erro.set(null);
  }

  protected async salvar(evento: Event) {
    evento.preventDefault();
    if (this.enviando()) return;
    this.tentou.set(true);
    if (this.erroNome()) return this.recusar('nome');
    if (this.corPrimaria() && !normalizarHex(this.corPrimaria())) return this.recusar('corPrimaria');
    if (this.corSecundaria() && !normalizarHex(this.corSecundaria())) return this.recusar('corSecundaria');
    const gestorErrado = problemaGestores(this.gestores());
    if (gestorErrado) return this.recusarGestor(gestorErrado.indice);

    this.enviando.set(true);
    try {
      await firstValueFrom(
        this.http.put<EmpresaDetalhe>(`${API_ADMIN}/empresas/${this.id()}`, {
          nome: this.modelo().nome.trim(),
          corPrimaria: normalizarHex(this.corPrimaria()),
          corSecundaria: normalizarHex(this.corSecundaria()),
          cargos: cargosParaEnvio(this.cargos()),
          encerraEm: this.encerraEm(),
          gestores: gestoresParaEnvio(this.gestores()),
        }),
      );
      await salvarLogo(this.http, this.id(), this.logo());
      await this.router.navigate(['/administrador/empresas', this.id()], { replaceUrl: true, state: { salva: true } });
    } catch (e) {
      const problema = problemaDe(e);
      const campos = (problema.campos ?? {}) as Partial<Record<Campo, string>>;
      const campo = (['nome', 'corPrimaria', 'corSecundaria', 'cargos'] as Campo[]).find((c) => campos[c]);
      const errosGestores = errosDeGestores(problema.campos as Record<string, string> | undefined);
      const linhaGestor = Object.keys(errosGestores).map(Number)[0];
      if (campo) {
        this.errosServidor.set(campos);
        this.recusar(campo);
      } else if (linhaGestor !== undefined) {
        this.errosGestores.set(errosGestores);
        this.recusarGestor(linhaGestor);
      } else {
        this.erro.set(problema.detail ?? 'Não foi possível salvar agora. Tente de novo.');
      }
    } finally {
      this.enviando.set(false);
    }
  }

  private recusarGestor(indice: number) {
    vibrar(30);
    document.getElementById('gestor-' + indice)?.focus();
  }

  private recusar(campo: Campo) {
    vibrar(30);
    if (campo === 'nome') this.campoNome()?.nativeElement.focus();
    else if (campo === 'cargos') document.getElementById('cargos-secao')?.focus();
    else document.getElementById(campo === 'corPrimaria' ? 'cor-primaria' : 'cor-secundaria')?.focus();
  }
}
