-- O primeiro login com Google vincula a identidade estavel (sub) a uma conta
-- ConectaPet ja existente. E-mail sozinho nao basta: em dominios corporativos,
-- um endereco pode ser atribuido a outra pessoa no futuro.
ALTER TABLE usuarios
    ADD COLUMN google_subject VARCHAR(255) NULL AFTER senha_hash,
    ADD UNIQUE KEY uk_usuarios_google_subject (google_subject);
