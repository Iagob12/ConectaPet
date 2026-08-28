-- ---------------------------------------------------------------------------
-- V1 — Usuarios, sessao e consentimento
--
-- Convencoes de todo o esquema:
--  - id interno BIGINT AUTO_INCREMENT, nunca exposto na API;
--  - uuid BINARY(16) para o identificador publico, senao da para enumerar
--    usuarios contando de 1 em 1;
--  - DATETIME(6) em UTC, nao TIMESTAMP: TIMESTAMP converte pelo fuso da sessao
--    do MySQL e produz bug silencioso quando o servidor muda de fuso;
--  - utf8mb4_bin em coluna de codigo, porque a collation padrao do MySQL 8 e
--    insensivel a maiuscula e a acento, e dois codigos distintos poderiam
--    colidir no indice unico durante a geracao de um lote.
-- ---------------------------------------------------------------------------

CREATE TABLE usuarios (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    uuid                    BINARY(16)   NOT NULL,
    email                   VARCHAR(255) NOT NULL,
    senha_hash              VARCHAR(72)  NOT NULL,
    nome                    VARCHAR(120) NOT NULL,
    telefone_principal      VARCHAR(20)  NULL,
    telefone_secundario     VARCHAR(20)  NULL,
    whatsapp                VARCHAR(20)  NULL,
    papel                   VARCHAR(20)  NOT NULL DEFAULT 'TUTOR',
    email_verificado_em     DATETIME(6)  NULL,
    ativo                   BOOLEAN      NOT NULL DEFAULT TRUE,
    anonimizado_em          DATETIME(6)  NULL,
    criado_em               DATETIME(6)  NOT NULL,
    atualizado_em           DATETIME(6)  NOT NULL,
    excluido_em             DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usuarios_uuid (uuid),
    UNIQUE KEY uk_usuarios_email (email),
    CONSTRAINT ck_usuarios_papel CHECK (papel IN ('TUTOR','ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Telefones ficam guardados em E.164; a forma de exibicao e derivada na
-- aplicacao. Sem isso, cada cliente reimplementa normalizacao de telefone
-- brasileiro e erra no nono digito e no DDD com zero.

CREATE TABLE refresh_tokens (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    usuario_id        BIGINT       NOT NULL,
    token_hash        CHAR(64)     NOT NULL,       -- SHA-256 em hex, nunca o token
    familia           BINARY(16)   NOT NULL,       -- rotacao: reuso revoga a familia toda
    substituido_por   BIGINT       NULL,
    expira_em         DATETIME(6)  NOT NULL,
    usado_em          DATETIME(6)  NULL,
    revogado_em       DATETIME(6)  NULL,
    criado_em         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_hash (token_hash),
    KEY ix_refresh_usuario (usuario_id, revogado_em),
    KEY ix_refresh_familia (familia),
    CONSTRAINT fk_refresh_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tokens_verificacao_email (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    usuario_id   BIGINT      NOT NULL,
    token_hash   CHAR(64)    NOT NULL,
    email_alvo   VARCHAR(255) NOT NULL,
    expira_em    DATETIME(6) NOT NULL,
    usado_em     DATETIME(6) NULL,
    criado_em    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_verif_hash (token_hash),
    KEY ix_verif_usuario (usuario_id),
    CONSTRAINT fk_verif_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tokens_reset_senha (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    usuario_id   BIGINT      NOT NULL,
    token_hash   CHAR(64)    NOT NULL,
    expira_em    DATETIME(6) NOT NULL,
    usado_em     DATETIME(6) NULL,
    criado_em    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reset_hash (token_hash),
    KEY ix_reset_usuario (usuario_id),
    CONSTRAINT fk_reset_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Consentimento com data, versao do documento e IP pseudonimizado.
CREATE TABLE aceites_termos (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    usuario_id     BIGINT      NULL,
    email          VARCHAR(255) NULL,      -- para aceite anonimo (lista de espera)
    documento      VARCHAR(40) NOT NULL,   -- TERMOS_USO, POLITICA_PRIVACIDADE
    versao         VARCHAR(20) NOT NULL,
    ip_hash        CHAR(32)    NULL,
    aceito_em      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_aceites_usuario (usuario_id),
    CONSTRAINT fk_aceites_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lista_espera (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    email        VARCHAR(255) NOT NULL,
    tipo_pet     VARCHAR(20)  NULL,
    ip_hash      CHAR(32)     NULL,
    criado_em    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lista_espera_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE log_auditoria (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    ator_uuid      BINARY(16)   NULL,   -- pseudonimo estavel; sobrevive a anonimizacao
    acao           VARCHAR(60)  NOT NULL,
    recurso_tipo   VARCHAR(40)  NOT NULL,
    recurso_uuid   BINARY(16)   NULL,
    detalhe        JSON         NULL,   -- nunca senha, token, codigo de ativacao ou telefone
    ip_hash        CHAR(32)     NULL,
    ocorrida_em    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY ix_auditoria_ator (ator_uuid, ocorrida_em),
    KEY ix_auditoria_recurso (recurso_tipo, recurso_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
