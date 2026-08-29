# ConectaPet API

API de identificação por tag NFC para cães e gatos. Java 21 + Spring Boot 3.4 + MySQL 8.

O contrato é `../contrato/openapi.yaml`. Ele é a fonte única: divergência entre código e
contrato é bug, não interpretação.

## Onde ficam as credenciais do banco

Tudo em **`api/.env`** — um arquivo só, lido pelos dois lados: o Docker Compose usa para
subir o MySQL, e a aplicação usa para se conectar nele. Ele está no `.gitignore` e nunca
é versionado.

```bash
cp .env.example .env
```

As variáveis que importam:

| Variável | Para que serve |
|---|---|
| `BANCO_URL` | Endereço JDBC. Trocar aqui aponta para outro banco |
| `MYSQL_USER` / `MYSQL_PASSWORD` | Usuário e senha da aplicação |
| `MYSQL_ROOT_PASSWORD` / `MYSQL_DATABASE` / `MYSQL_PORT` | Só o Compose usa, ao criar o container |
| `JWT_SEGREDO` | Assinatura do token. Mínimo 32 bytes |
| `IP_PIMENTA` | HMAC que pseudonimiza o IP de quem lê a tag |

`JWT_SEGREDO` e `IP_PIMENTA` **não têm valor padrão** de propósito: sem eles a aplicação
se recusa a subir, em vez de rodar com um segredo previsível.

**Já usa um MySQL seu, fora do Docker?** Mexa só em `BANCO_URL`, `MYSQL_USER` e
`MYSQL_PASSWORD`, e pule o `docker compose up`. O banco precisa existir; as tabelas o
Flyway cria sozinho na primeira subida.

**Em produção**, não existe `.env`: as mesmas variáveis entram como variáveis de ambiente
do serviço de hospedagem. O nome é idêntico, então nada no código muda.

## Subir local

```bash
cp .env.example .env      # os segredos já vêm gerados
docker compose up -d      # MySQL 8.4 na porta 3306
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Se aparecer `Communications link failure`, o `.env` foi lido e o problema é o banco:
confira se o container está de pé com `docker compose ps`. Se aparecer
`Could not resolve placeholder`, é o contrário — o `.env` não foi encontrado.

No perfil `dev` a aplicação gera um lote de 10 tags de teste e **imprime os códigos de
ativação no console**. Isso só acontece em `dev`: em qualquer outro ambiente seria
vazamento do segredo que impede quem manuseia a encomenda de se cadastrar como dono.

- API: `http://localhost:8080`
- OpenAPI navegável: `http://localhost:8080/docs` (desligado em `prod`)

## Testes

```bash
./mvnw test                    # 64 unitários, em segundos, sem Docker
./mvnw verify                  # acrescenta os 57 de integração (sobe MySQL via Docker)
./mvnw verify -Psem-docker     # o ciclo completo, pulando os de integração
```

**Sem Docker na máquina?** Um MySQL já rodando serve igual — aponte a suíte para ele:

```bash
./mvnw verify -Dteste.banco.url="jdbc:mysql://127.0.0.1:3306/conectapet_teste" -Dteste.banco.usuario=conectapet -Dteste.banco.senha=SUA_SENHA
```

**Use um schema separado.** Os testes truncam todas as tabelas entre casos; apontar para o
banco de desenvolvimento apagaria os dados dele. A suíte recusa um schema cujo nome não
termine em `_teste` — é o tipo de engano que só se percebe depois de perdido.

Os de integração ficam no `verify`, e não no `test`, para o ciclo curto continuar curto.

Eles são classes `*IT`, que o surefire não enxerga — por isso existe o failsafe. **Sem o
failsafe eles não rodavam em comando nenhum**: estavam escritos, marcados com
`@Tag("integracao")` e inalcançáveis, e o "precisa de Docker" escondia que faltava também
o executor. Hoje `./mvnw verify` os alcança e eles falham por falta de Docker, que é o
comportamento certo.

Os testes de integração usam **MySQL real via Testcontainers**, não H2. Collation e índice
único se comportam diferente, e a collation binária de `codigo_publico` é justamente o que
impede dois códigos distintos de colidirem no `UNIQUE` durante a geração de um lote — em
H2 o teste passaria e a produção quebraria.

## Decisões que o código materializa

**Um código para ativar.** `codigoPublico` (10 caracteres) vai gravado na tag, aparece no
fim da URL e é o único que o cliente precisa: quem encosta o celular numa tag sem perfil
cria a conta e assume a tag ali mesmo.

Existe um `codigoAtivacao` (8 caracteres) gerado junto e guardado como hash BCrypt, mas
**ele não é exigido**. Foi decisão de produto, tomada com o custo na mesa: o fluxo curto
dispensa imprimir e casar um cartão com cada tag, e em troca abre uma janela — NFC
atravessa papelão, então quem manuseia a encomenda fechada consegue ler a tag e se
cadastrar antes do cliente. A proteção que resta é operacional (marcar as tags como
enviadas só no despacho) e o `409` de tag com dono, que ao menos torna o roubo visível
para quem recebe a caixa.

O hash continua gravado e o CSV do lote continua emitindo o código: voltar a exigi-lo é
mudança de código, sem migração, sem regravar tag e sem reimprimir nada.

**Alfabeto de 30 símbolos:** `23456789ABCDEFGHJKMNPQRSTVWXYZ`. Não é Base32 de
biblioteca — o Crockford já tira `I L O U` e aqui tiramos também `0` e `1`. Geração e
validação são manuais de propósito; trocar por um decoder de Base32 quebra os códigos já
gravados nas tags.

**Indistinguibilidade, onde ela ainda vale.** Nas rotas públicas (`/api/public/tags/...`),
código inexistente e tag não ativada devolvem exatamente a mesma resposta, com piso de
tempo igual. É o que impede varrer o espaço de códigos para descobrir quais existem.

Na reivindicação a distinção passou a existir junto com a remoção do código de ativação:
quem digita um código válido descobre, pelo `409`, que ele já tem dono. Não é vazamento
novo — `/p/{codigo}` já mostra o perfil de uma tag ativa a qualquer pessoa. O que continua
travado é o outro lado: código inexistente não devolve nada além de "inválido", e o limite
de tentativas é o que contém a varredura.

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

**Critério de ATIVA.** A tag vira `ATIVA` sozinha quando o pet vinculado tem nome
preenchido **e** um canal de contato de fato utilizável. "Utilizável" exige as duas coisas:
a chave ligada na visibilidade **e** o telefone existindo no cadastro do tutor — só a chave,
sem número, produziria uma tag ativa cuja página de resgate não aciona ninguém. Se o dado
some depois, a tag volta para `REIVINDICADA`.

**Registrar leitura ≠ notificar tutor.** `GET` do perfil registra sempre, marcando a
origem (`SERVIDOR` ou `ROBO`) e **nunca** notifica. Só o `POST` de confirmação, enviado
pelo cliente via `sendBeacon`, notifica — com deduplicação por hash de IP em 10 minutos.
Filtrar notificação por user-agent seria uma corrida que se perde: cada rede social tem o
seu e eles mudam. Com a separação, o robô de preview do WhatsApp nunca dispara "seu pet foi
encontrado", e o próprio tutor deixa de receber push a cada aproximação enquanto testa a
tag no cadastro.

**Perfil público montado campo a campo.** A entidade JPA nunca é serializada. Campo oculto
não vira `null` nem string vazia: ele não existe no JSON, porque a página não pode ter
rótulo órfão. Uma resposta só para "não ativada" e "código não existe", num método único —
se fossem dois caminhos, alguém acabaria acrescentando um detalhe em um deles.

**Telefone em dois campos, derivados de E.164 guardado.** O `wa.me` exige
`5511999990000` e a tela exige `(11) 99999-0000`. Números com forma ambígua são
recusados em vez de "corrigidos": aceitar `01999990000` gravaria um número válido e
diferente do pretendido, e a página de resgate ligaria para um estranho.

**Retenção em duas etapas.** Coordenada, mensagem e telefone de quem encontrou saem em 90
dias — são dados de um terceiro que só quis ajudar. O resto da leitura vive 12 meses.

**Transferir titularidade ≠ migrar perfil.** A especificação original chamava os dois pelo
mesmo nome, e eles são opostos. Transferir titularidade desvincula a tag do pet e do tutor
anterior e entrega uma tag em branco ao novo dono — **sem apagar o registro do pet**, que
pode ter outras tags. Migrar perfil é o que o FAQ do site promete: mesmo tutor, tag nova,
tudo preservado, uma troca de ponteiro.

**O código de transferência é um portador.** Guardado como SHA-256, validade de 15 minutos,
um único ativo por tag, consumido por `UPDATE` condicional — ler-depois-gravar deixaria
uma janela em que duas pessoas com o mesmo código passariam e a tag trocaria de dono duas
vezes. Não vai por e-mail: e-mail é o canal mais provável de estar comprometido.

**Auditoria com pseudônimo.** Reivindicação, transferência, migração, desativação e
alteração de visibilidade deixam rastro. O ator é gravado como UUID, nunca e-mail ou
telefone — assim a trilha sobrevive à anonimização da conta sem virar dado pessoal. O
registro participa da transação de quem chamou: auditoria de algo que foi desfeito é pior
que auditoria nenhuma.

**Reautenticação para operação sensível.** Baixar os códigos de um lote ou confirmá-lo
exige digitar a senha de novo, mesmo com a sessão de admin aberta: um computador destravado
não pode virar acesso ao segredo que protege um lote inteiro. O token de elevação vale 5
minutos, vive em memória e é vinculado ao usuário que o criou.

**Marcar tag como enviada exige lote confirmado.** Enviar tag cujo CSV não foi conferido
significa mandar ao cliente um chaveiro cujo código de ativação talvez não esteja impresso
em lugar nenhum — e ele só descobre com a caixa na mão.

**A taxa de ativação usa as tags ENVIADAS como base**, não as produzidas: tag parada em
estoque não teve chance de ser ativada, e contá-la no denominador faria a métrica parecer
pior do que é. As leituras contam só origem `CLIENTE`.

**Foto: o EXIF sai porque a imagem é reencodada do zero.** Não há uma chamada "remover
EXIF" — ao decodificar para `BufferedImage` e escrever de novo com `metadata` nulo, os
metadados da origem simplesmente não são copiados. Isso importa muito: a foto do pet quase
sempre carrega as coordenadas GPS da casa do tutor, e ela vai para uma página pública.

**Formato validado pelos bytes, nunca pela extensão nem pelo `Content-Type`.** Os dois são
escolhidos por quem envia; um `.jpg` que na verdade é HTML com script, servido do nosso
domínio, seria XSS armazenado.

**As dimensões são lidas do cabeçalho antes de decodificar.** Um PNG de 60 KB pode declarar
30000×30000 e estourar a memória da aplicação inteira ao ser aberto — bomba de
descompressão. Checar só o tamanho do arquivo não protege disso.

**A foto é servida pela API, sob a mesma regra de visibilidade do perfil.** Tag desativada
ou perfil oculto derrubam a foto junto. Bucket público não daria isso: a URL continuaria
funcionando para sempre para quem a tivesse copiado. A chave é aleatória, não derivada do
UUID do pet, senão vazar um id daria a URL da foto de graça.

## Estado atual

Implementado: fundação, autenticação, máquina de estados da tag, reivindicação, perfil do
pet, saúde, contatos, visibilidade, modo perdido, endpoints públicos, leituras,
notificações por outbox, lista de espera, expurgo por retenção, transferência de
titularidade, migração de perfil e trilha de auditoria.

O passo 2 do plano está completo. Verificado subindo a aplicação contra um MySQL 8.4
gerenciado: migrations aplicadas, fluxo de reivindicação, perfil, visibilidade, modo
perdido e ciclo do lote exercitados de ponta a ponta.

O envio de e-mail é um stub em log, e o armazenamento de fotos é em disco — serve para
desenvolvimento, mas **não sobrevive a redeploy**: trocar por S3/R2 antes de produção é
uma classe nova implementando `ArmazenamentoFotos`.

**Encerrar conta anonimiza, não apaga a linha.** A escolha estava no desenho do banco desde
a primeira migração: `anonimizado_em` existe em `usuarios`, e a auditoria grava o ator como
UUID justamente para sobreviver a isso sem virar dado pessoal. Apagar a linha levaria junto
o rastro de quem reivindicou e transferiu cada tag — que é o que permite responder depois
"de quem era esta tag quando ela mudou de dono". Some o que identifica: nome, e-mail,
telefones, fotos (do armazenamento, não só da linha) e os perfis dos pets. As tags do tutor
são **desativadas**, não apenas desvinculadas: uma tag viva depois disso ficaria na coleira
apontando para um perfil que não existe mais. Encerrar exige a senha de novo, pelo mesmo
motivo da reautenticação do administrativo.

**Perfil `dev`:** cria `admin@conectapet.local` / `admin-de-desenvolvimento` e um lote
de 10 tags com os códigos no console. O envio de e-mail é um stub que registra em log — trocar por SES ou Resend
não toca nenhum outro arquivo.

## O que precisa estar definido em produção

A aplicação **recusa subir** fora dos perfis `dev` e `test` se encontrar configuração de
desenvolvimento, e lista tudo o que estiver errado de uma vez:

| Variável | Por que impede a subida |
|---|---|
| `URL_PUBLICA_TAG` | vai gravada no chip. Errada, toda tag do lote nasce inútil — e isso não tem conserto por software |
| `URL_SITE` | vai nos links de e-mail. Errada, quem esquece a senha recebe um link que não abre |
| `CORS_ORIGENS` | a confirmação de leitura é um `fetch` do navegador para outra origem. Errada, o navegador bloqueia, o front engole o erro e **o tutor nunca é avisado** |
| `EMAIL_PROVEDOR` | `log` não impede a subida, mas registra um aviso: nada é entregue |

Também recusa cookie sem `Secure`, o seed de tags ligado, e o corpo dos e-mails indo para
o log (ele carrega o link de redefinir senha).

Esta guarda existe por causa de uma classe de defeito que já apareceu duas vezes aqui:
coisas que funcionam em dev, falham em produção e falham **caladas**. Nenhuma dava erro na
subida — o servidor ficava de pé, as telas abriam, e o que quebrava só aparecia com
cliente na frente. Ficar de pé funcionando pela metade é pior do que não subir, porque
ninguém investiga um serviço que responde 200.

## Ligar o envio de e-mail

Por padrão o envio é um stub que registra no console (`conectapet.email.provedor=log`).
Para enviar de verdade, o provedor precisa falar SMTP — SES, Resend, Postmark ou o
servidor do próprio domínio, tanto faz:

```bash
EMAIL_PROVEDOR=smtp
EMAIL_REMETENTE="ConectaPet <nao-responda@conectapet.com.br>"
EMAIL_RESPONDER_PARA=ajuda@conectapet.com.br   # opcional; vazio desliga
SMTP_HOST=email-smtp.sa-east-1.amazonaws.com
SMTP_PORTA=587
SMTP_USUARIO=...
SMTP_SENHA=...
```

**O padrão é `log` de propósito.** Um ambiente de homologação que herdasse SMTP por
engano mandaria "alguém leu a tag do seu pet" para cliente de verdade. Ligar envio é uma
decisão explícita, por ambiente.

Os dois canais são mutuamente exclusivos: o mapa de canais tem uma chave por canal, então
declarar os dois derruba a aplicação na subida — melhor do que subir com um deles escolhido
por sorteio.

Falha de envio **sobe** em vez de ser engolida: quem chama conta a tentativa, registra o
erro e reagenda com espera crescente (1, 2, 4, 8 minutos, e desiste na quinta). Engolir
marcaria como enviada uma mensagem que nunca saiu.

## Onde ficam as fotos

Por padrão, em disco (`FOTO_ARMAZENAMENTO=local`). Serve para desenvolver e **não
sobrevive a um redeploy** — a foto é o que faz quem achou o pet reconhecer o bicho, então
perdê-la não é perder um enfeite.

Em produção, object storage:

```bash
FOTO_ARMAZENAMENTO=s3
FOTO_S3_BUCKET=fotos-conectapet
FOTO_S3_ENDPOINT=https://<conta>.r2.cloudflarestorage.com   # vazio = AWS
FOTO_S3_REGIAO=auto                                          # na AWS, a região real
FOTO_S3_CHAVE=...
FOTO_S3_SEGREDO=...
```

Serve S3 e qualquer compatível — R2, MinIO, Backblaze. Com endpoint preenchido, o cliente
liga *path-style* (esses serviços não usam subdomínio por bucket) e **desliga a assinatura
em blocos**: a AWS aceita, o Backblaze recusa, e o efeito de não desligar é a foto ser
gravada com os cabeçalhos de bloco no meio dos bytes — corrompida, e só se descobre quando
alguém tenta vê-la.

**O bucket nunca é público.** Nada no código devolve URL de objeto: os bytes voltam para a
API, que os serve sob a mesma regra de visibilidade do perfil. Com bucket público, a foto
continuaria acessível depois de o tutor ocultar o perfil ou desativar a tag — e o endereço
dela já teria vazado para quem abriu a página. A credencial também não precisa de
permissão de listagem: as chaves são conhecidas, uma pasta por foto.

## O primeiro administrador, em produção

O seed do perfil `dev` não serve fora dele: ele imprime códigos de ativação no console,
que é justamente o segredo que impede quem manuseia a encomenda de virar dono da tag.

Em produção não há criação de conta de admin por configuração. O caminho é:

1. Cadastre-se pelo site como qualquer tutor, escolhendo a própria senha.
2. Defina `ADMIN_EMAIL` com esse e-mail.
3. Suba a API. A conta é promovida a `ADMIN` na inicialização.

Nenhuma senha passa pela configuração, e a variável não cria conta: se o e-mail não
tiver cadastro, o serviço registra um aviso e segue. Promover um cadastro inexistente
seria abrir um administrador fantasma. Depois de promovida, a variável pode ficar — ela
é idempotente.

## O que vai gravado no NFC

`URL_PUBLICA_TAG` (padrão `https://conectapet.com.br/p/`) é o prefixo do endereço que o
administrativo manda gravar em cada tag. **Ele é imutável na prática**: uma vez gravado
no chip e entregue ao cliente, mudar o domínio quebra todas as tags em campo. Confira
antes de gravar o primeiro lote — a tela de códigos avisa se o endereço apontar para
`localhost`, mas não tem como saber se o domínio está errado.
