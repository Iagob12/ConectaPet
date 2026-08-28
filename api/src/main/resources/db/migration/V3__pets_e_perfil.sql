-- ---------------------------------------------------------------------------
-- V3 — Pets, saude, contatos de emergencia, visibilidade e assinatura
-- ---------------------------------------------------------------------------

CREATE TABLE assinaturas (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    usuario_id    BIGINT      NOT NULL,
    plano         VARCHAR(10) NOT NULL DEFAULT 'FREE',
    status        VARCHAR(20) NOT NULL DEFAULT 'ATIVA',
    iniciada_em   DATETIME(6) NOT NULL,
    expira_em     DATETIME(6) NULL,
    criado_em     DATETIME(6) NOT NULL,
    atualizado_em DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_assinatura_usuario (usuario_id, status),
    CONSTRAINT fk_assinatura_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT ck_assinatura_plano CHECK (plano IN ('FREE','PLUS')),
    CONSTRAINT ck_assinatura_status CHECK (status IN ('ATIVA','VENCIDA','CANCELADA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pets (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    uuid               BINARY(16)   NOT NULL,
    usuario_id         BIGINT       NOT NULL,
    nome               VARCHAR(60)  NOT NULL,
    especie            VARCHAR(20)  NOT NULL,
    raca               VARCHAR(60)  NULL,
    sexo               VARCHAR(20)  NULL,
    data_nascimento    DATE         NULL,
    peso_kg            DECIMAL(5,2) NULL,
    cor                VARCHAR(40)  NULL,
    castrado           BOOLEAN      NULL,
    numero_microchip   VARCHAR(20)  NULL,
    foto_chave         VARCHAR(120) NULL,   -- chave no object storage, nunca URL publica
    cidade             VARCHAR(80)  NULL,
    estado             CHAR(2)      NULL,
    observacoes        VARCHAR(500) NULL,
    criado_em          DATETIME(6)  NOT NULL,
    atualizado_em      DATETIME(6)  NOT NULL,
    excluido_em        DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pets_uuid (uuid),
    KEY ix_pets_usuario (usuario_id, excluido_em),
    CONSTRAINT fk_pets_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT ck_pets_especie CHECK (especie IN ('CACHORRO','GATO','OUTRO')),
    CONSTRAINT ck_pets_sexo CHECK (sexo IS NULL OR sexo IN ('MACHO','FEMEA','NAO_INFORMADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A tag so pode apontar para um pet depois que a tabela existe.
ALTER TABLE tags
    ADD CONSTRAINT fk_tags_pet FOREIGN KEY (pet_id) REFERENCES pets (id);

CREATE TABLE pet_saude (
    pet_id                 BIGINT       NOT NULL,
    alergias               VARCHAR(300) NULL,
    medicacao_continua     VARCHAR(300) NULL,
    condicoes              VARCHAR(300) NULL,
    cuidados_especiais     VARCHAR(300) NULL,
    veterinario_nome       VARCHAR(120) NULL,
    veterinario_telefone   VARCHAR(20)  NULL,
    clinica                VARCHAR(120) NULL,
    criado_em              DATETIME(6)  NOT NULL,
    atualizado_em          DATETIME(6)  NOT NULL,
    PRIMARY KEY (pet_id),
    CONSTRAINT fk_saude_pet FOREIGN KEY (pet_id) REFERENCES pets (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE contatos_emergencia (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    uuid           BINARY(16)   NOT NULL,   -- a regra de nunca expor id sequencial
    pet_id         BIGINT       NOT NULL,   -- vale aqui tambem; faltava no modelo original
    nome           VARCHAR(120) NOT NULL,
    telefone       VARCHAR(20)  NOT NULL,
    parentesco     VARCHAR(40)  NULL,
    ordem          INT          NOT NULL DEFAULT 0,
    criado_em      DATETIME(6)  NOT NULL,
    atualizado_em  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_contatos_uuid (uuid),
    KEY ix_contatos_pet (pet_id, ordem),
    CONSTRAINT fk_contatos_pet FOREIGN KEY (pet_id) REFERENCES pets (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Defaults do design system: telefone e WhatsApp visiveis, saude visivel,
-- microchip oculto.
CREATE TABLE visibilidade_perfil (
    pet_id                        BIGINT       NOT NULL,
    mostrar_telefone              BOOLEAN      NOT NULL DEFAULT TRUE,
    mostrar_whatsapp              BOOLEAN      NOT NULL DEFAULT TRUE,
    mostrar_contatos_emergencia   BOOLEAN      NOT NULL DEFAULT TRUE,
    mostrar_saude                 BOOLEAN      NOT NULL DEFAULT TRUE,
    mostrar_cidade                BOOLEAN      NOT NULL DEFAULT TRUE,
    mostrar_microchip             BOOLEAN      NOT NULL DEFAULT FALSE,
    mensagem_personalizada        VARCHAR(200) NULL,
    criado_em                     DATETIME(6)  NOT NULL,
    atualizado_em                 DATETIME(6)  NOT NULL,
    PRIMARY KEY (pet_id),
    -- Um perfil sem nenhum canal de contato e uma tela de emergencia inutil,
    -- o oposto do produto. A aplicacao recusa com 400; esta restricao e a
    -- segunda barreira, para o caso de alguem escrever no banco por fora.
    CONSTRAINT ck_visibilidade_canal CHECK (mostrar_telefone = TRUE OR mostrar_whatsapp = TRUE),
    CONSTRAINT fk_visibilidade_pet FOREIGN KEY (pet_id) REFERENCES pets (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
