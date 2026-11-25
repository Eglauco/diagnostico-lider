import { problemaEmail } from '../../compartilhado/email';
import { mascararTelefone, problemaTelefone } from '../../compartilhado/entrada-telefone';
import { LinhaImportada, NovoConvite, StatusConvite, TipoEventoConvite } from '../admin-modelos';

export const OUTRO_CARGO = 'outro';

export const ROTULO_STATUS: Record<StatusConvite, string> = {
  AGUARDANDO_ENVIO: 'Aguardando envio',
  ENVIADO: 'Convite enviado',
  ABRIU: 'Abriu o link',
  COMECOU: 'Começou',
  CONCLUIDO: 'Concluiu',
  INATIVO: 'Inativo',
};

/** Cor do selo de status: cinza (ainda nada), azul-abeto (andando), verde (concluiu). */
export const CLASSE_STATUS: Record<StatusConvite, string> = {
  AGUARDANDO_ENVIO: 'bg-nevoa-2 text-abeto-2 ring-1 ring-linha',
  ENVIADO: 'bg-white text-abeto ring-1 ring-linha',
  ABRIU: 'bg-[#E8EEF9] text-[#1F4A9A]',
  COMECOU: 'bg-[#FFF3DA] text-[#7A4D00]',
  CONCLUIDO: 'bg-ascensao-suave text-[#17714A]',
  INATIVO: 'bg-nevoa-2 text-abeto-3 ring-1 ring-linha',
};

export const ROTULO_EVENTO: Record<TipoEventoConvite, string> = {
  CRIADO: 'Convite criado',
  EDITADO: 'Dados editados no painel',
  ENVIADO: 'E-mail de convite enviado',
  FALHA_ENVIO: 'O e-mail não saiu',
  RESULTADO_ENVIADO: 'Resultado enviado por e-mail',
  EMAIL_DEVOLVIDO: 'O e-mail voltou',
  EMAIL_SPAM: 'Marcou o e-mail como spam',
  COMECOU_RESPONDER: 'Começou a responder',
  VIU_RESULTADO: 'Viu o resultado',
  BAIXOU_PDF: 'Baixou o PDF',
  COMPARTILHOU_PDF: 'Compartilhou o PDF',
  PDF_BAIXADO_PAINEL: 'PDF individual baixado no painel',
  ABERTO: 'Abriu o link',
  DADOS_CONFIRMADOS: 'Confirmou os dados',
  DADOS_CORRIGIDOS: 'Corrigiu os dados',
  CONCLUIDO: 'Concluiu o diagnóstico',
  LEMBRETE: 'Lembrete enviado',
  INATIVADO: 'Líder inativado',
  REATIVADO: 'Líder reativado',
};

/** Uma linha da revisão da planilha, já no formato que a pessoa edita. cargo: id, OUTRO_CARGO ou ''. */
export interface LinhaRevisao {
  chave: string;
  linha: number;
  nome: string;
  email: string;
  whatsapp: string;
  cargo: string;
  /** O que veio escrito na planilha (para explicar a sugestão). */
  cargoTexto: string;
  sugestaoCargoId: number | null;
  jaConvidado: boolean;
  exemplo: boolean;
}

export type CampoRevisao = 'nome' | 'email' | 'whatsapp' | 'cargo';

export interface Problema {
  campo: CampoRevisao;
  mensagem: string;
}

export function linhaDeRevisao(l: LinhaImportada, i: number): LinhaRevisao {
  return {
    chave: `l${i}-${l.linha}`,
    linha: l.linha,
    nome: l.nome,
    email: l.email,
    whatsapp: l.whatsapp,
    cargo: l.cargoOutro ? OUTRO_CARGO : l.cargoId !== null ? String(l.cargoId) : '',
    cargoTexto: l.cargoTexto,
    sugestaoCargoId: l.sugestaoCargoId,
    jaConvidado: l.jaConvidado,
    exemplo: l.exemplo,
  };
}

/** Linha que fica de fora sem ser erro: o exemplo do modelo e quem já tem convite. */
export function foraDaLista(l: LinhaRevisao): string | null {
  if (l.exemplo) return 'Linha de exemplo do modelo: fica de fora.';
  if (l.jaConvidado) return 'Esse e-mail já tem convite: fica de fora.';
  return null;
}

/** As mesmas regras do servidor, para mostrar o problema na linha enquanto a pessoa edita. */
export function problemas(l: LinhaRevisao, repetidos: Set<string>, temCargos: boolean): Problema[] {
  const lista: Problema[] = [];
  const nome = l.nome.trim();
  if (nome.length < 2) lista.push({ campo: 'nome', mensagem: 'Escreva o nome do líder.' });
  else if (nome.length > 160) lista.push({ campo: 'nome', mensagem: 'Use até 160 caracteres.' });
  const email = l.email.trim().toLowerCase();
  if (!email) lista.push({ campo: 'email', mensagem: 'Escreva o e-mail.' });
  else if (problemaEmail(email)) lista.push({ campo: 'email', mensagem: problemaEmail(email)! });
  else if (repetidos.has(email)) lista.push({ campo: 'email', mensagem: 'E-mail repetido nesta lista.' });
  const whatsapp = problemaTelefone(l.whatsapp);
  if (whatsapp) lista.push({ campo: 'whatsapp', mensagem: whatsapp });
  if (temCargos && !l.cargo) {
    lista.push({
      campo: 'cargo',
      mensagem: l.cargoTexto ? `"${l.cargoTexto}" não está na lista de cargos.` : 'Escolha o cargo.',
    });
  }
  return lista;
}

/** E-mails que aparecem mais de uma vez entre as linhas que vão ser convidadas. */
export function emailsRepetidos(linhas: LinhaRevisao[]): Set<string> {
  const vistos = new Set<string>();
  const repetidos = new Set<string>();
  for (const l of linhas) {
    const email = l.email.trim().toLowerCase();
    if (!email) continue;
    if (vistos.has(email)) repetidos.add(email);
    vistos.add(email);
  }
  return repetidos;
}

export function paraEnvio(l: LinhaRevisao): NovoConvite {
  const outro = l.cargo === OUTRO_CARGO;
  return {
    nome: l.nome.trim(),
    email: l.email.trim().toLowerCase(),
    whatsapp: l.whatsapp.trim(),
    cargoId: l.cargo && !outro ? Number(l.cargo) : null,
    cargoOutro: outro,
  };
}

/** Máscara do WhatsApp no (input): reescreve o campo e devolve o valor formatado. */
export function mascarar(evento: Event): string {
  return mascararTelefone(evento.target);
}
