-- ---------------------------------------------------------------------------
-- V5 — Bytes das fotos no proprio banco
-- ---------------------------------------------------------------------------
-- Existe porque o disco do container e efemero: com armazenamento local, toda
-- foto de pet sumia a cada publicacao. A foto e o que faz quem achou o animal
-- reconhecer que e o bicho certo — perde-la nao e perder um enfeite.
--
-- Guardar imagem em banco costuma ser ma ideia, e aqui nao e, por dois numeros
-- medidos: as tres variantes de um pet somam ~28 kB (160, 400 e 1200px em
-- JPEG), e o upload e limitado a 4 MB antes do reprocessamento. Trinta mil
-- pets cabem em menos de 1 GB. O dia em que isso deixar de valer, a interface
-- ArmazenamentoFotos ja existe e a troca para S3 nao toca em mais nada.
--
-- Tabela separada de propósito: `pets` e lida em toda listagem do painel, e
-- um BLOB na mesma linha faria cada consulta carregar bytes que ninguem pediu.

CREATE TABLE fotos_arquivo (
    chave        CHAR(32)     NOT NULL,
    -- 'p' pequena (160px), 'm' media (400px), 'o' original (1200px).
    variante     CHAR(1)      NOT NULL,

    -- MEDIUMBLOB (16 MB) e nao LONGBLOB: o upload ja e recusado acima de 4 MB,
    -- e o maior arquivo que chega aqui e a variante de 1200px reencodada.
    -- LONGBLOB pediria 4 bytes de cabecalho por linha para um teto que nunca
    -- vai ser alcancado.
    conteudo     MEDIUMBLOB   NOT NULL,
    tamanho      INT          NOT NULL,
    criado_em    DATETIME(6)  NOT NULL,

    PRIMARY KEY (chave, variante)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
