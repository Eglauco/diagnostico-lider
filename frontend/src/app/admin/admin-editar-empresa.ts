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
import { normalizarHex, siglaDe, usarPreviaDeCores } from './cores/cores';
import { PreviaMarca } from './cores/previa-marca';
import { SecaoCores } from './cores/secao-cores';
import { CampoLogo, MudancaLogo } from './logo/campo-logo';
import { logoEmPrevia, salvarLogo } from './logo/salvar-logo';

type Campo = 'nome' | 'corPrimaria' | 'corSecundaria' | 'cargos';

/** Editar nome, logo, cores e cargos. O link fica como está: pode já estar impresso nos materiais. */
@Component({
  selector: 'app-admin-editar-empresa',
  imports: [CampoLogo, FormField, PreviaMarca, RouterLink, SecaoCargos, SecaoCores],
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
      this.carregado.set(true);
      // Veio do "Editar cargos" da página da empresa: vai direto para a seção.
      if (this.rota.snapshot.fragment === 'cargos-secao') {
        setTimeout(() => document.getElementById('cargos-secao')?.scrollIntoView({ block: 'start' }), 150);
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

    this.enviando.set(true);
    try {
      await firstValueFrom(
        this.http.put<EmpresaDetalhe>(`${API_ADMIN}/empresas/${this.id()}`, {
          nome: this.modelo().nome.trim(),
          corPrimaria: normalizarHex(this.corPrimaria()),
          corSecundaria: normalizarHex(this.corSecundaria()),
          cargos: cargosParaEnvio(this.cargos()),
        }),
      );
      await salvarLogo(this.http, this.id(), this.logo());
      await this.router.navigate(['/administrador/empresas', this.id()], { replaceUrl: true, state: { salva: true } });
    } catch (e) {
      const problema = problemaDe(e);
      const campos = (problema.campos ?? {}) as Partial<Record<Campo, string>>;
      const campo = (['nome', 'corPrimaria', 'corSecundaria', 'cargos'] as Campo[]).find((c) => campos[c]);
      if (campo) {
        this.errosServidor.set(campos);
        this.recusar(campo);
      } else {
        this.erro.set(problema.detail ?? 'Não foi possível salvar agora. Tente de novo.');
      }
    } finally {
      this.enviando.set(false);
    }
  }

  private recusar(campo: Campo) {
    vibrar(30);
    if (campo === 'nome') this.campoNome()?.nativeElement.focus();
    else if (campo === 'cargos') document.getElementById('cargos-secao')?.focus();
    else document.getElementById(campo === 'corPrimaria' ? 'cor-primaria' : 'cor-secundaria')?.focus();
  }
}
