/**
 * O que está errado num e-mail, dito do jeito que a pessoa conserta ("Falta o final do domínio",
 * não "e-mail inválido"). A mesma regra existe no servidor (compartilhado/Emails.java): mude as
 * duas juntas. Vazio não é problema aqui: "obrigatório" é outra regra.
 */
export function problemaEmail(bruto: string | null | undefined): string | null {
  const email = (bruto ?? '').trim();
  if (!email) return null;
  if (email.length > 254) return 'E-mail muito longo.';
  if (/\s/.test(email)) return 'Tire os espaços do e-mail.';
  const arroba = email.indexOf('@');
  if (arroba < 0) return 'Falta o @ no e-mail (ex.: nome@empresa.com.br).';
  if (email.indexOf('@', arroba + 1) >= 0) return 'O e-mail tem mais de um @.';
  const dominio = email.slice(arroba + 1);
  if (arroba === 0) return 'Falta o nome antes do @ (ex.: nome@empresa.com.br).';
  if (!dominio) return 'Falta o domínio depois do @ (ex.: @empresa.com.br).';
  if (!dominio.includes('.')) return 'Falta o final do domínio (ex.: .com ou .com.br).';
  if (dominio.startsWith('.') || dominio.endsWith('.') || dominio.includes('..')) {
    return 'Confira o domínio do e-mail: sobra ou falta um ponto.';
  }
  return null;
}
