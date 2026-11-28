-- Foto de fundo da página do RH (arquivo no bucket: {ambiente}/empresas/{id}/fundo-v{versao}.jpg),
-- versionada como a logomarca. O ponto de foco (0 a 100, da esquerda e do topo) é o que fica sempre
-- à vista quando a foto é cortada: em pé na entrada do computador, deitada no topo da página.
alter table empresa
    add column fundo_versao  int check (fundo_versao > 0),
    add column fundo_largura int check (fundo_largura > 0),
    add column fundo_altura  int check (fundo_altura > 0),
    add column fundo_foco_x  smallint not null default 50 check (fundo_foco_x between 0 and 100),
    add column fundo_foco_y  smallint not null default 50 check (fundo_foco_y between 0 and 100);
