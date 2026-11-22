-- =====================================================================
-- Questionário de exemplo: "Carreira do líder" v1 (3 perguntas).
-- Substituir pelo conteúdo definitivo criando a versão 2.
-- =====================================================================

insert into questionario (codigo, titulo)
values ('carreira-lider', 'Diagnóstico de carreira do líder');

insert into questionario_versao (questionario_id, numero, status, publicada_em)
select id, 1, 'PUBLICADA', now() from questionario where codigo = 'carreira-lider';

-- Dimensões -----------------------------------------------------------
insert into dimensao (versao_id, codigo, nome, descricao, ordem)
select v.id, d.codigo, d.nome, d.descricao, d.ordem
from questionario_versao v
join questionario q on q.id = v.questionario_id and q.codigo = 'carreira-lider'
cross join (values
    ('DIRECAO',         'Direção',         'Clareza sobre aonde você quer chegar.',            1),
    ('DESENVOLVIMENTO', 'Desenvolvimento', 'O quanto você investe em crescer como líder.',     2),
    ('VISIBILIDADE',    'Visibilidade',    'Como o seu trabalho é percebido por quem decide.', 3)
) as d (codigo, nome, descricao, ordem)
where v.numero = 1;

-- Perguntas -----------------------------------------------------------
insert into pergunta (versao_id, dimensao_id, codigo, enunciado, ajuda, tipo, ordem)
select v.id, dim.id, p.codigo, p.enunciado, p.ajuda, 'ESCOLHA_UNICA', p.ordem
from questionario_versao v
join questionario q on q.id = v.questionario_id and q.codigo = 'carreira-lider'
cross join (values
    ('P01', 'DIRECAO',
     'Quando você pensa nos próximos dois anos da sua carreira, o que mais se parece com a sua realidade?',
     null, 1),
    ('P02', 'DESENVOLVIMENTO',
     'Quando foi a última vez que você investiu de forma estruturada no seu desenvolvimento como líder?',
     'Curso, mentoria, programa de liderança ou leitura com plano de aplicação.', 2),
    ('P03', 'VISIBILIDADE',
     'Como você sente que o seu trabalho é percebido pela liderança acima de você?',
     null, 3)
) as p (codigo, dimensao, enunciado, ajuda, ordem)
join dimensao dim on dim.versao_id = v.id and dim.codigo = p.dimensao
where v.numero = 1;

-- Opções (pontos: 4 = carreira em movimento, 1 = carreira travada) ------
insert into opcao (pergunta_id, texto, pontos, ordem)
select p.id, o.texto, o.pontos, o.ordem
from pergunta p
join questionario_versao v on v.id = p.versao_id and v.numero = 1
join questionario q on q.id = v.questionario_id and q.codigo = 'carreira-lider'
join (values
    ('P01', 'Tenho um plano claro, com metas e prazos.',            4, 1),
    ('P01', 'Sei aonde quero chegar, mas não sei como.',            3, 2),
    ('P01', 'Tenho algumas ideias, nada concreto.',                 2, 3),
    ('P01', 'Sinceramente, não sei para onde estou indo.',          1, 4),
    ('P02', 'Nos últimos 3 meses.',                                 4, 1),
    ('P02', 'Neste último ano.',                                    3, 2),
    ('P02', 'Há mais de um ano.',                                   2, 3),
    ('P02', 'Não lembro.',                                          1, 4),
    ('P03', 'Sou reconhecido e lembrado para novos desafios.',      4, 1),
    ('P03', 'Sou reconhecido, mas as oportunidades não chegam.',    3, 2),
    ('P03', 'Entrego bem, mas me sinto invisível.',                 2, 3),
    ('P03', 'Sinto que o meu trabalho não é valorizado.',           1, 4)
) as o (pergunta, texto, pontos, ordem) on o.pergunta = p.codigo;

-- Faixas de resultado ---------------------------------------------------
insert into faixa_resultado (versao_id, codigo, titulo, resumo, descricao, pontuacao_minima, pontuacao_maxima)
select v.id, f.codigo, f.titulo, f.resumo, f.descricao, f.minima, f.maxima
from questionario_versao v
join questionario q on q.id = v.questionario_id and q.codigo = 'carreira-lider'
cross join (values
    ('TRAVADA', 'Carreira travada',
     'Você continua entregando, mas a sua carreira parou de andar e já começou a perder altura.',
     'Os sinais são claros: falta direção, o desenvolvimento ficou para depois e o seu trabalho não está sendo percebido por quem decide. Carreira travada não é carreira acabada. O primeiro movimento é reconhecer em que ponto você está, e você acabou de fazer isso.',
     0, 39),
    ('PLATO', 'Carreira no platô',
     'Você chegou longe, mas o ritmo diminuiu. O próximo salto não vai acontecer sozinho.',
     'O platô é o ponto mais traiçoeiro da carreira: tudo parece estável, as entregas continuam boas, mas as oportunidades param de chegar. Sem uma decisão consciente, o platô vira declínio, e quase sempre quem percebe primeiro são os outros.',
     40, 69),
    ('ASCENSAO', 'Carreira em ascensão',
     'Sua carreira está em movimento. O desafio agora é sustentar a subida.',
     'Você tem direção, investe no próprio desenvolvimento e o seu trabalho é percebido por quem decide. Líderes nesse ponto da curva costumam errar por excesso de confiança: deixam de preparar a próxima curva enquanto a atual ainda está subindo.',
     70, 100)
) as f (codigo, titulo, resumo, descricao, minima, maxima)
where v.numero = 1;

insert into faixa_recomendacao (faixa_id, ordem, texto)
select f.id, r.ordem, r.texto
from faixa_resultado f
join questionario_versao v on v.id = f.versao_id and v.numero = 1
join questionario q on q.id = v.questionario_id and q.codigo = 'carreira-lider'
join (values
    ('TRAVADA', 1, 'Separe uma hora nesta semana para responder por escrito: o que eu quero para a minha carreira e o que me impede hoje?'),
    ('TRAVADA', 2, 'Converse com alguém de confiança, de fora da sua área, sobre como o seu trabalho é percebido.'),
    ('TRAVADA', 3, 'Retome o seu desenvolvimento com um passo pequeno e concreto, com data marcada para começar.'),
    ('PLATO',   1, 'Escreva em uma frase onde você quer estar daqui a dois anos e o que precisa ser verdade para isso acontecer.'),
    ('PLATO',   2, 'Escolha a competência que mais separa você do próximo nível e monte um plano de 90 dias para desenvolvê-la.'),
    ('PLATO',   3, 'Torne seus resultados visíveis: mostre à sua liderança o impacto do seu trabalho, não só as entregas.'),
    ('ASCENSAO', 1, 'Defina agora qual será o seu próximo salto, antes que a curva atual se estabilize.'),
    ('ASCENSAO', 2, 'Transforme o que você aprendeu em método e ensine o seu time. Isso libera você para desafios maiores.'),
    ('ASCENSAO', 3, 'Pergunte a quem está acima de você o que ainda falta para o próximo nível.')
) as r (faixa, ordem, texto) on r.faixa = f.codigo;

-- Empresa e campanhas de exemplo ----------------------------------------
insert into empresa (nome) values ('Empresa Demonstração');

-- Link geral (vários clientes, sem vínculo com empresa): /
insert into campanha (codigo, nome, questionario_id)
select 'geral', 'Diagnóstico aberto', id from questionario where codigo = 'carreira-lider';

-- Link vinculado a uma empresa (alimenta o relatório consolidado): /c/demo
insert into campanha (codigo, nome, questionario_id, empresa_id)
select 'demo', 'Empresa Demonstração — líderes 2026', q.id, e.id
from questionario q, empresa e
where q.codigo = 'carreira-lider' and e.nome = 'Empresa Demonstração';
