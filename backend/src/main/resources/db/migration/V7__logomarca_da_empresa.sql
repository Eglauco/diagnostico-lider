-- Logomarca da empresa (arquivo no bucket: {ambiente}/empresas/{id}/logo-v{versao}.png).
-- A versão muda a cada envio: o endereço da imagem muda junto e o cache dos navegadores
-- pode guardar cada versão para sempre. Proporção guardada para o layout não "pular".
alter table empresa
    add column logo_versao  int check (logo_versao > 0),
    add column logo_largura int check (logo_largura > 0),
    add column logo_altura  int check (logo_altura > 0);
