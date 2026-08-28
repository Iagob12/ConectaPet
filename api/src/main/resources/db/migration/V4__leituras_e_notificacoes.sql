-- ---------------------------------------------------------------------------
-- V4 — Leituras da tag e outbox de notificacoes
-- ---------------------------------------------------------------------------

CREATE TABLE leituras (
    id                          BIGINT       NOT NULL AUTO_INCREMENT,
    uuid                        BINARY(16)   NOT NULL,
    tag_id                      BIGINT       NOT NULL,
    -- Denormalizado de proposito: o historico e sempre consultado por pet, e a
    -- tag pode ser desvinculada depois sem apagar o que ja aconteceu.
    pet_id                      BIGINT       NULL,
    ocorrida_em                 DATETIME(6)  NOT NULL,

    -- SERVIDOR: navegador humano, registrada ao servir o perfil.
    -- ROBO:     user-agent de robo (preview de link em WhatsApp, Telegram...).
    -- CLIENTE:  confirmada por sendBeacon. A UNICA que notifica o tutor.
    origem                      VARCHAR(10)  NOT NULL,

    -- HMAC com pimenta fora do banco, truncado. Nunca o IP em claro.
    ip_hash                     CHAR(32)     NULL,
    user_agent                  VARCHAR(300) NULL,

    cidade                      VARCHAR(80)  NULL,
    regiao                      VARCHAR(80)  NULL,
    pais                        VARCHAR(2)   NULL,

    -- Dados de terceiro que esta fazendo um favor. Expurgados em 90 dias,
    -- antes do resto da leitura, que vai a 12 meses.
    localizacao_compartilhada   BOOLEAN      NOT NULL DEFAULT FALSE,
    latitude                    DECIMAL(10,7) NULL,
    longitude                   DECIMAL(10,7) NULL,
    precisao_m                  INT          NULL,
    mensagem_de_quem_encontrou  VARCHAR(500) NULL,
    telefone_de_quem_encontrou  VARCHAR(20)  NULL,
    dados_terceiro_expurgados_em DATETIME(6) NULL,

    notificada_em               DATETIME(6)  NULL,
    criado_em                   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_leituras_uuid (uuid),
    KEY ix_leituras_tag (tag_id, ocorrida_em DESC),
    KEY ix_leituras_pet (pet_id, ocorrida_em DESC),
    KEY ix_leituras_expurgo (ocorrida_em),
    -- Deduplicacao da notificacao: mesma tag, mesmo IP, janela de 10 minutos.
    KEY ix_leituras_dedup (tag_id, ip_hash, ocorrida_em),
    CONSTRAINT fk_leituras_tag FOREIGN KEY (tag_id) REFERENCES tags (id),
    CONSTRAINT fk_leituras_pet FOREIGN KEY (pet_id) REFERENCES pets (id),
    CONSTRAINT ck_leituras_origem CHECK (origem IN ('SERVIDOR','CLIENTE','ROBO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Padrao outbox: a notificacao e gravada na mesma transacao do fato que a
-- originou e enviada depois por job. Assim a resposta ao usuario nunca depende
-- do servidor de e-mail estar de pe.
CREATE TABLE outbox_notificacoes (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    tipo            VARCHAR(40)  NOT NULL,
    canal           VARCHAR(20)  NOT NULL DEFAULT 'EMAIL',
    destinatario    VARCHAR(255) NOT NULL,
    -- Sem telefone, senha, token nem codigo de ativacao aqui dentro.
    conteudo        JSON         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDENTE',
    tentativas      INT          NOT NULL DEFAULT 0,
    processar_apos  DATETIME(6)  NOT NULL,
    processada_em   DATETIME(6)  NULL,
    ultimo_erro     VARCHAR(500) NULL,
    criado_em       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY ix_outbox_pendente (status, processar_apos),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDENTE','ENVIADA','FALHOU')),
    CONSTRAINT ck_outbox_canal CHECK (canal IN ('EMAIL','WHATSAPP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
