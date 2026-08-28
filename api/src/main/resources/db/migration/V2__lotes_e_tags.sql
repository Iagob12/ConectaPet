-- ---------------------------------------------------------------------------
-- V2 — Lotes e tags
-- ---------------------------------------------------------------------------

CREATE TABLE lotes_tag (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    nome              VARCHAR(80)  NOT NULL,
    quantidade        INT          NOT NULL,
    modelo            VARCHAR(20)  NOT NULL,
    -- O lote nasce NAO_CONFIRMADO e os codigos de ativacao seguem recuperaveis
    -- mediante reautenticacao ate o admin confirmar que recebeu o arquivo.
    -- Sem isso, uma conexao que cai no meio do download perde os codigos de um
    -- lote de tags ja gravadas fisicamente: prejuizo material, nao inconveniencia.
    status            VARCHAR(20)  NOT NULL DEFAULT 'NAO_CONFIRMADO',
    produzido_em      DATETIME(6)  NOT NULL,
    confirmado_em     DATETIME(6)  NULL,
    observacoes       VARCHAR(300) NULL,
    criado_em         DATETIME(6)  NOT NULL,
    atualizado_em     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_lote_status CHECK (status IN ('NAO_CONFIRMADO','CONFIRMADO')),
    CONSTRAINT ck_lote_modelo CHECK (modelo IN ('CLASSICA','SLIM','COLEIRA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tags (
    id                    BIGINT      NOT NULL AUTO_INCREMENT,
    uuid                  BINARY(16)  NOT NULL,

    -- Vai gravado na tag e aparece na URL. Publico por natureza.
    -- utf8mb4_bin: a collation padrao e insensivel a caixa e a acento, e dois
    -- codigos distintos poderiam colidir no unique durante a geracao do lote.
    codigo_publico        VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,

    -- Impresso no cartao dentro da embalagem. Guardado so como hash BCrypt.
    codigo_ativacao_hash  VARCHAR(72) NOT NULL,
    -- Em claro APENAS enquanto o lote esta NAO_CONFIRMADO. Apagado na confirmacao.
    codigo_ativacao_claro  VARCHAR(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,

    lote_id               BIGINT      NOT NULL,
    modelo                VARCHAR(20) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'CRIADA',
    usuario_id            BIGINT      NULL,
    pet_id                BIGINT      NULL,   -- FK adicionada em V3, quando pets existir
    modo_perdido          BOOLEAN     NOT NULL DEFAULT FALSE,
    reivindicada_em       DATETIME(6) NULL,
    enviada_em            DATETIME(6) NULL,
    desativada_em         DATETIME(6) NULL,
    criado_em             DATETIME(6) NOT NULL,
    atualizado_em         DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tags_uuid (uuid),
    UNIQUE KEY uk_tags_codigo_publico (codigo_publico),
    KEY ix_tags_usuario_status (usuario_id, status),
    KEY ix_tags_lote (lote_id),
    KEY ix_tags_pet (pet_id),
    CONSTRAINT fk_tags_lote FOREIGN KEY (lote_id) REFERENCES lotes_tag (id),
    CONSTRAINT fk_tags_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT ck_tags_modelo CHECK (modelo IN ('CLASSICA','SLIM','COLEIRA')),
    CONSTRAINT ck_tags_status CHECK (status IN
        ('CRIADA','ENVIADA','REIVINDICADA','ATIVA','MODO_PERDIDO','EM_TRANSFERENCIA','DESATIVADA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Tentativas de reivindicacao. Vive no banco, e nao em memoria, porque o limite
-- de 5 por hora precisa sobreviver a restart: em memoria, basta esperar o
-- container reiniciar para zerar o contador.
CREATE TABLE tentativas_reivindicacao (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    codigo_publico  VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    ip_hash         CHAR(32)    NOT NULL,
    sucesso         BOOLEAN     NOT NULL,
    ocorrida_em     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- Dois indices porque sao dois baldes independentes: um por IP e um GLOBAL
    -- por codigo. Se o balde por codigo tivesse IP na chave, um atacante com
    -- 200 IPs faria 1.000 tentativas por hora no mesmo codigo.
    KEY ix_tentativa_codigo (codigo_publico, ocorrida_em),
    KEY ix_tentativa_ip (ip_hash, ocorrida_em)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE codigos_transferencia (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    tag_id        BIGINT      NOT NULL,
    codigo_hash   CHAR(64)    NOT NULL,
    criado_por    BIGINT      NOT NULL,
    expira_em     DATETIME(6) NOT NULL,
    usado_em      DATETIME(6) NULL,
    cancelado_em  DATETIME(6) NULL,
    criado_em     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_transf_hash (codigo_hash),
    KEY ix_transf_tag (tag_id),
    CONSTRAINT fk_transf_tag FOREIGN KEY (tag_id) REFERENCES tags (id),
    CONSTRAINT fk_transf_criador FOREIGN KEY (criado_por) REFERENCES usuarios (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
