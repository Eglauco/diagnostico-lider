import { Component, computed, inject, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { Location } from '@angular/common';
import { RouterLink } from '@angular/router';

import { FAIXAS, ORDEM_FAIXAS } from '../compartilhado/faixas';
import { dataCompleta } from '../compartilhado/formatos';
import { API_ADMIN, Convites } from './admin-modelos';
import { linkWhatsappComTexto, mensagemConvite } from './convite';
import { EmpresaAtual } from './empresa/empresa-atual';
import { KitQr } from './qr/kit-qr';

/**
 * Aba "Visão geral" da empresa: o que ela precisa para divulgar (link, convite pronto e QR Code) e
 * um resumo dos líderes, do diagnóstico e dos cargos, cada um levando à sua aba.
 */
@Component({
  selector: 'app-admin-empresa',
  imports: [KitQr, RouterLink],
  templateUrl: './admin-empresa.html',
  styleUrl: './admin.css',
  host: { class: 'block' },
})
export class AdminEmpresa {
  private readonly atual = inject(EmpresaAtual);

  /** A mesma empresa do cabeçalho (EmpresaLayout): um pedido só. */
  protected readonly empresa = this.atual.empresa;
  /** Resumo do cadastro de líderes (o funil) para o cartão da página. */
  protected readonly convites = httpResource<Convites>(() => {
    const id = this.atual.id();
    return id ? `${API_ADMIN}/empresas/${id}/convites` : undefined;
  });
  private readonly dados = computed(() => (this.empresa.hasValue() ? this.empresa.value() : undefined));

  /** Acabou de ser cadastrada ou editada: mostra a confirmação uma vez só. */
  protected readonly confirmacao = signal(this.lerConfirmacao());

  protected readonly convite = computed(() => {
    const e = this.dados();
    return e?.link ? mensagemConvite(e.nome, e.link.url) : '';
  });
  protected readonly whatsapp = computed(() => linkWhatsappComTexto(this.convite()));
  protected readonly distribuicao = computed(() => {
    const ind = this.dados()?.indicadores;
    const total = ORDEM_FAIXAS.reduce((soma, f) => soma + (ind?.porFaixa[f] ?? 0), 0);
    return ORDEM_FAIXAS.map((codigo) => {
      const quantidade = ind?.porFaixa[codigo] ?? 0;
      return { codigo, ...FAIXAS[codigo], quantidade, percentual: total ? (quantidade / total) * 100 : 0 };
    });
  });

  protected readonly copiado = signal<'link' | 'convite' | 'rh' | null>(null);
  protected readonly erro = signal<string | null>(null);

  /** Cargos como escada: o nível mais alto em cima (nivel 1 = primeiro nível de liderança). */
  protected readonly cargosNaEscada = computed(() =>
    (this.dados()?.cargos ?? []).map((cargo, i) => ({ ...cargo, nivel: i + 1 })).reverse(),
  );

  /** O estado do histórico sobrevive ao recarregar: limpa a marca depois de ler. */
  private lerConfirmacao(): 'criada' | 'salva' | null {
    const location = inject(Location);
    const { criada, salva, ...resto } = (location.getState() ?? {}) as { criada?: boolean; salva?: boolean };
    if (criada || salva) location.replaceState(location.path(), '', resto);
    return criada ? 'criada' : salva ? 'salva' : null;
  }

  protected dataDeCadastro(iso: string): string {
    return dataCompleta(iso).replace(/ às .*$/, '');
  }

  protected async copiar(texto: string, chave: 'link' | 'convite' | 'rh') {
    this.erro.set(null);
    try {
      await navigator.clipboard.writeText(texto);
      this.copiado.set(chave);
      setTimeout(() => this.copiado.set(null), 2000);
    } catch {
      this.erro.set('O navegador não deixou copiar. Selecione o texto e copie manualmente.');
    }
  }
}
