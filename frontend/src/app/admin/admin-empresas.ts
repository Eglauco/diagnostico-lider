import { Component, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';

import { quando } from '../compartilhado/formatos';
import { API_ADMIN, EmpresaResumo } from './admin-modelos';
import { QrSvg } from './qr/qr-svg';

/** Empresas cadastradas, cada uma com o seu link e o seu QR Code. */
@Component({
  selector: 'app-admin-empresas',
  imports: [QrSvg, RouterLink],
  templateUrl: './admin-empresas.html',
  styleUrl: './admin.css',
  host: { class: 'block' },
})
export class AdminEmpresas {
  protected readonly empresas = httpResource<EmpresaResumo[]>(() => `${API_ADMIN}/empresas`);
  protected readonly copiado = signal<number | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly quando = quando;

  protected async copiarLink(empresa: EmpresaResumo) {
    if (!empresa.link) return;
    try {
      await navigator.clipboard.writeText(empresa.link.url);
      this.copiado.set(empresa.id);
      setTimeout(() => this.copiado.set(null), 2000);
    } catch {
      this.erro.set(`Copie o link manualmente: ${empresa.link.url}`);
    }
  }
}
