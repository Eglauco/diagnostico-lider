import { httpResource } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';

import { API_ADMIN, EmpresaDetalhe } from '../admin-modelos';

/**
 * A empresa aberta no painel, compartilhada entre o cabeçalho e as abas (Visão geral, Diagnóstico,
 * Líderes, Marca e cargos). Vive enquanto a empresa estiver aberta: fornecida pelo EmpresaLayout.
 */
@Injectable()
export class EmpresaAtual {
  readonly id = signal<string | null>(null);

  readonly empresa = httpResource<EmpresaDetalhe>(() => {
    const id = this.id();
    return id ? `${API_ADMIN}/empresas/${id}` : undefined;
  });

  /** Depois de mudar algo (marca, prazo, líderes): o cabeçalho e as abas pegam os números novos. */
  recarregar() {
    this.empresa.reload();
  }
}
