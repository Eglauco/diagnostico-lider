import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { LogoEmpresa } from '../../compartilhado/tema-marca';
import { API_ADMIN } from '../admin-modelos';
import { MudancaLogo } from './campo-logo';

/** Envia (ou apaga) a logomarca depois que os dados da empresa foram salvos. */
export async function salvarLogo(http: HttpClient, empresaId: number | string, mudanca: MudancaLogo): Promise<void> {
  if (mudanca?.tipo === 'nova') {
    const formulario = new FormData();
    formulario.append('arquivo', mudanca.logo.png, 'logomarca.png');
    await firstValueFrom(http.put(`${API_ADMIN}/empresas/${empresaId}/logo`, formulario));
  } else if (mudanca?.tipo === 'remover') {
    await firstValueFrom(http.delete(`${API_ADMIN}/empresas/${empresaId}/logo`));
  }
}

/** Logo que a prévia deve mostrar: a nova (ainda não salva), nenhuma, ou a que já existe. */
export function logoEmPrevia(mudanca: MudancaLogo, atual: LogoEmpresa | null): LogoEmpresa | null {
  if (mudanca?.tipo === 'nova') return { url: mudanca.logo.url, largura: mudanca.logo.largura, altura: mudanca.logo.altura };
  return mudanca?.tipo === 'remover' ? null : atual;
}
