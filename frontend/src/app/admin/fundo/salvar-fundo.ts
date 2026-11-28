import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { API_ADMIN } from '../admin-modelos';
import { MudancaFundo, TipoFoto } from './campo-fundo';

/** Envia (ou apaga, ou só refoca) uma foto da página do RH depois que os dados da empresa foram salvos. */
export async function salvarFundo(http: HttpClient, empresaId: number | string, tipo: TipoFoto, mudanca: MudancaFundo): Promise<void> {
  const url = `${API_ADMIN}/empresas/${empresaId}/${tipo}`;
  if (mudanca?.tipo === 'nova') {
    const formulario = new FormData();
    formulario.append('arquivo', mudanca.foto.jpeg, `${tipo}.jpg`);
    formulario.append('focoX', String(mudanca.foco.x));
    formulario.append('focoY', String(mudanca.foco.y));
    await firstValueFrom(http.put(url, formulario));
  } else if (mudanca?.tipo === 'foco') {
    await firstValueFrom(http.put(`${url}/foco`, mudanca.foco));
  } else if (mudanca?.tipo === 'remover') {
    await firstValueFrom(http.delete(url));
  }
}
