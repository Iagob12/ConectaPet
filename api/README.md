# ConectaPet API

API de identificação por tag NFC para cães e gatos. Java 21 + Spring Boot 3.4 + MySQL 8.

O contrato é `../contrato/openapi.yaml`. Ele é a fonte única: divergência entre código e
contrato é bug, não interpretação.

## Subir local

```bash
cp .env.example .env      # preencha JWT_SEGREDO e IP_PIMENTA
docker compose up -d      # MySQL 8.4 na porta 3306
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

No perfil `dev` a aplicação gera um lote de 10 tags de teste e **imprime os códigos de
ativação no console**. Isso só acontece em `dev`: em qualquer outro ambiente seria
vazamento do segredo que impede quem manuseia a encomenda de se cadastrar como dono.

- API: `http://localhost:8080`
- OpenAPI navegável: `http://localhost:8080/docs` (desligado em `prod`)

## Testes

```bash
./mvnw test                  # tudo, exige Docker para os de integração
./mvnw -Psem-docker test     # só os unitários
```

Os testes de integração usam **MySQL real via Testcontainers**, não H2. Collation e índice
único se comportam diferente, e a collation binária de `codigo_publico` é justamente o que
impede dois códigos distintos de colidirem no `UNIQUE` durante a geração de um lote — em
H2 o teste passaria e a produção quebraria.

## Decisões que o código materializa

**Dois códigos.** `codigoPublico` (10 caracteres) vai gravado na tag e aparece na URL.
`codigoAtivacao` (8 caracteres) é impresso no cartão dentro da embalagem e guardado só
como hash BCrypt de custo 12. Sem o segundo, qualquer pessoa que manuseie a encomenda no
transporte poderia se cadastrar como dono.

**Alfabeto de 30 símbolos:** `23456789ABCDEFGHJKMNPQRSTVWXYZ`. Não é Base32 de
biblioteca — o Crockford já tira `I L O U` e aqui tiramos também `0` e `1`. Geração e
validação são manuais de propósito; trocar por um decoder de Base32 quebra os códigos já
gravados nas tags.

**Indistinguibilidade.** Código público inexistente e código de ativação errado devolvem
o mesmo `403`, com o mesmo corpo e o mesmo custo de tempo — o BCrypt roda mesmo quando a
tag não existe. Se a API distinguisse, criar uma conta bastaria para enumerar todos os
códigos e coletar o telefone de todos os tutores.

**Dois baldes de tentativa, independentes.** Um por IP e um **global por código**, sem IP
na chave. Com IP na chave, um atacante com 200 endereços faria 1.000 tentativas por hora
no mesmo código. Só tentativas *falhas* contam: quem compra o Kit Multipet ativa quatro
tags seguidas e não pode ser trancado por isso. O contador vive no banco, não em memória,
porque em memória bastaria esperar um restart — ou disparar um deploy — para zerá-lo.

**Rotação de refresh com janela de 10s.** Fora da janela, apresentar um token já usado é
reuso: revoga a família inteira. Dentro dela, é só a segunda aba do mesmo usuário
renovando ao mesmo tempo, e emitir um token novo custa menos que derrubar a sessão de
alguém legítimo.

**IP pseudonimizado por HMAC com pimenta fora do banco.** Salt fixo dentro do banco não
serviria: o IPv4 tem 4 bilhões de valores, e quem obtivesse o banco calcularia a tabela
inteira uma vez e reverteria todos os IPs de todas as leituras.

**Sessão em cookie `HttpOnly`, nunca em `localStorage`.** Qualquer XSS levaria junto os
dados de contato de todos os pets do tutor. `SameSite=Lax` funciona porque site e API
vivem sob o mesmo domínio-raiz; previews usam `preview-*.conectapet.com.br` em vez de
`SameSite=None`, que é o tipo de configuração que vaza para produção.

**`ddl-auto: validate` em todos os perfis.** O esquema é responsabilidade exclusiva do
Flyway; `validate` faz a aplicação morrer cedo se entidade e tabela divergirem.

**Lote nasce `NAO_CONFIRMADO`.** Os códigos de ativação seguem recuperáveis mediante
reautenticação até o admin confirmar que recebeu o arquivo. Sem isso, uma conexão que cai
no meio do download perde os códigos de um lote de tags já gravadas fisicamente.

## Estado atual

Implementado: fundação, autenticação, máquina de estados da tag e reivindicação.

Pendente, na ordem: perfil do pet e visibilidade → leituras e notificações → transferência
e migração → administrativo.

O que ainda não existe em código, mas já está no contrato: endpoints públicos de perfil,
`/status`, leituras, lista de espera, pets, conta e administrativo.
