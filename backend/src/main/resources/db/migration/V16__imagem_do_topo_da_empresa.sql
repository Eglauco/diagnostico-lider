-- A imagem do topo da página do RH (deitada), separada da foto da entrada (em pé ou quadrada).
-- Arquivo no bucket: {ambiente}/empresas/{id}/capa-v{versao}.jpg, versionado como a logomarca.
-- Ela também é a faixa da entrada no celular. Sem ela, vale a foto da entrada (e vice-versa).
alter table empresa
    add column capa_versao  int check (capa_versao > 0),
    add column capa_largura int check (capa_largura > 0),
    add column capa_altura  int check (capa_altura > 0),
    add column capa_foco_x  smallint not null default 50 check (capa_foco_x between 0 and 100),
    add column capa_foco_y  smallint not null default 50 check (capa_foco_y between 0 and 100);
