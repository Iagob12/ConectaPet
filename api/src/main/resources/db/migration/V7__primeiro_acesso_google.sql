-- Contas criadas diretamente pelo Google ainda nao possuem senha local.
-- Guardar uma senha aleatoria fingiria que ela existe e deixaria operacoes de
-- reautenticacao impossiveis de explicar. A pessoa pode definir uma senha pelo
-- fluxo de recuperacao quando quiser usar tambem o acesso por e-mail.
ALTER TABLE usuarios
    MODIFY COLUMN senha_hash VARCHAR(72) NULL;
