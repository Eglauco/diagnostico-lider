-- =====================================================================
-- Relatório consolidado da empresa: textos escritos para a organização.
-- O resumo, a descrição e as recomendações das faixas falam com o líder
-- ("você"); aqui fica o que a empresa lê e o que ela pode fazer.
-- O relatório não diz quem está em cada faixa: as ações são para o grupo,
-- oferecidas a todos, nunca "chame o líder X".
-- Conteúdo de exemplo da versão 1: revisar junto com o questionário definitivo.
-- =====================================================================

alter table faixa_resultado add column leitura_empresa text;
alter table dimensao add column acao_empresa text;

create table faixa_acao_empresa (
    faixa_id bigint not null references faixa_resultado (id),
    ordem    int    not null,
    texto    text   not null,
    primary key (faixa_id, ordem)
);

update faixa_resultado f
set leitura_empresa = l.texto
from questionario_versao v
join questionario q on q.id = v.questionario_id and q.codigo = 'carreira-lider'
cross join (values
    ('TRAVADA',
     'Líderes que continuam entregando, mas cuja carreira parou de andar: falta direção, o desenvolvimento ficou para depois e o trabalho não chega a quem decide. É o grupo com maior risco de desmotivação e de saída.'),
    ('PLATO',
     'Líderes que chegaram longe, mas perderam ritmo. As entregas seguem boas e tudo parece estável, porém as oportunidades pararam de chegar. Sem uma decisão consciente, o platô vira declínio.'),
    ('ASCENSAO',
     'Líderes com direção, que investem no próprio desenvolvimento e são percebidos por quem decide. O risco aqui é outro: excesso de confiança e a perda desses talentos para o mercado.')
) as l (codigo, texto)
where f.versao_id = v.id and v.numero = 1 and f.codigo = l.codigo;

insert into faixa_acao_empresa (faixa_id, ordem, texto)
select f.id, a.ordem, a.texto
from faixa_resultado f
join questionario_versao v on v.id = f.versao_id and v.numero = 1
join questionario q on q.id = v.questionario_id and q.codigo = 'carreira-lider'
join (values
    ('TRAVADA', 1, 'Ofereça conversas de carreira a todos os líderes nos próximos 30 dias. Quem está travado raramente pede ajuda, e travamento sem conversa costuma virar desmotivação ou pedido de demissão.'),
    ('TRAVADA', 2, 'Disponibilize um modelo simples de plano de carreira, com metas e prazos, para cada líder construir com a liderança direta.'),
    ('TRAVADA', 3, 'Abra uma porta de entrada fácil para o desenvolvimento, como mentoria ou uma trilha curta, com inscrição aberta e data para começar.'),
    ('PLATO',   1, 'Deixe claro o que se espera de um líder em cada nível. No platô, a falta de clareza sobre o próximo passo é o que mais trava.'),
    ('PLATO',   2, 'Proponha a cada líder um plano de 90 dias focado na competência que separa o nível atual do próximo.'),
    ('PLATO',   3, 'Crie oportunidades de exposição: projetos entre áreas, apresentações para a diretoria e participação em decisões.'),
    ('ASCENSAO', 1, 'Fortaleça o plano de sucessão e torne visíveis os próximos desafios disponíveis, antes que a curva atual desses líderes se estabilize.'),
    ('ASCENSAO', 2, 'Crie espaço para que líderes experientes sejam mentores de outros líderes e registrem as boas práticas do time.'),
    ('ASCENSAO', 3, 'Revise reconhecimento e retenção. Líderes em ascensão são os mais procurados pelo mercado.')
) as a (faixa, ordem, texto) on a.faixa = f.codigo;

update dimensao d
set acao_empresa = a.texto
from questionario_versao v
join questionario q on q.id = v.questionario_id and q.codigo = 'carreira-lider'
cross join (values
    ('DIRECAO',
     'Inclua conversas de carreira no calendário da liderança, ao menos duas por ano, e mostre com clareza os caminhos possíveis dentro da empresa.'),
    ('DESENVOLVIMENTO',
     'Garanta tempo e orçamento para desenvolvimento, com planos individuais acompanhados pela liderança, e não só cursos avulsos.'),
    ('VISIBILIDADE',
     'Crie rituais em que os líderes apresentem resultados a quem decide e dê retorno frequente sobre como o trabalho deles é percebido.')
) as a (codigo, texto)
where d.versao_id = v.id and v.numero = 1 and d.codigo = a.codigo;
