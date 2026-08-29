# Colocar o ConectaPet no ar

Duas rotas, e o repositório serve as duas.

| Rota | Como | Quando escolher |
|---|---|---|
| **Vercel + Render** | `vercel.json` e `render.yaml` | é a escolhida. Menos servidor para cuidar |
| **Um servidor só** | `docker-compose.yml` + `deploy/provisionar.sh` | tudo numa VPS, inclusive o banco |

A segunda está descrita mais abaixo. Esta seção é a primeira.

## Vercel (site) + Render (API) + MySQL gerenciado

São três contratos, não dois: **o Render não oferece MySQL**, só PostgreSQL.
O banco precisa vir de outro lugar — Aiven, Railway ou TiDB Cloud servem, e o
`BANCO_URL` aponta para lá.

### 1. O banco

Crie um MySQL 8 e guarde a URL JDBC, o usuário e a senha. Exija SSL:

```
jdbc:mysql://SEU-HOST:PORTA/conectapet?sslMode=REQUIRED
```

O Flyway cria as 18 tabelas sozinho na primeira subida da API.

### 2. A API, no Render

New → Blueprint → aponte para este repositório. Ele lê o `render.yaml`, cria o
serviço a partir do `api/Dockerfile` e pergunta as variáveis marcadas
`sync: false`. `JWT_SEGREDO` e `IP_PIMENTA` ele sorteia — melhor do que colar
um segredo que já passou por um chat.

**O plano gratuito não serve.** Ele hiberna após 15 minutos sem tráfego e leva
uns 50 segundos para acordar. Nesse tempo, quem encostou o celular na tag vê
"nossos servidores não responderam". Para um produto de resgate, o plano pago é
o mínimo viável.

**As fotos precisam de bucket aqui.** No Render o disco do container é efêmero:
`FOTO_ARMAZENAMENTO=local` perderia as fotos a cada deploy. Por isso o
`render.yaml` já vem com `s3`.

### 3. O site, na Vercel

Importe o repositório e aponte o **diretório raiz para `web/`** — é de lá que a Vercel
lê o `vercel.json`, então ele mora em `web/vercel.json` e não na raiz do repositório.

**A região não é fixada.** Escolher onde as funções rodam exige plano Pro; no Hobby elas
ficam na região padrão da conta. Isso enfraquece o argumento de latência que levou a
escolher São Paulo — mas como a API está em Ohio de qualquer forma, o site renderizando
nos EUA até reduz o caminho entre os dois. Quando houver Pro, `"regions": ["gru1"]` no
`vercel.json` traz o site para São Paulo.

Variáveis a definir no painel:

| Variável | Valor | Quando é lida |
|---|---|---|
| `URL_SITE` | `https://seu-dominio` | **build** — ver o aviso abaixo |
| `API_URL` | `https://conectapet-api.onrender.com` | execução |
| `API_URL_PUBLICA` | o mesmo | vai ao navegador |

`URL_SITE` é lida em tempo de build. Se você trocar depois, **refaça o deploy** —
sem isso o site sobe, as páginas abrem e todo formulário responde 403.

### 4. Ligar as pontas

De volta ao Render, ajuste três variáveis com o domínio final da Vercel:

- `URL_SITE` e `CORS_ORIGENS` → o domínio do site
- `URL_PUBLICA_TAG` → `https://seu-dominio/p/`

`CORS_ORIGENS` errado é a falha que não aparece: o navegador bloqueia a
confirmação de leitura, nada registra erro, e o tutor nunca é avisado.

### O que essa rota custa

O Render não tem região na América do Sul. Ohio, a menos ruim, fica a uns
120 ms de São Paulo — então a página de resgate renderiza no Brasil mas
consulta a API do outro hemisfério antes de responder. Se isso incomodar em
campo, o caminho é mover a API para um provedor com região brasileira: o
`api/Dockerfile` é o mesmo e nada no código prende ao Render.

---

# Alternativa: tudo num servidor só

Três serviços: banco, API e site. O `docker-compose.yml` sobe os três e os liga
entre si.

```bash
cp .env.producao.example .env
# preencha o .env (a seção abaixo diz o que cada linha faz)
docker compose up -d --build
```

## Antes: o que só você pode obter

| Item | Recomendado | Sem isso |
|---|---|---|
| Uma máquina | VPS **em São Paulo**, 2 vCPU / 4 GB | não há onde rodar |
| Um domínio | registro.br, para `.com.br` | as tags apontariam para um IP que muda |
| Provedor de e-mail | Resend (grátis até 3 mil/mês) ou Amazon SES | o tutor não recebe aviso nem recupera a senha |
| Bucket | Cloudflare R2 (10 GB grátis, sem taxa de saída) | as fotos não sobrevivem a trocar de máquina |

Os dois primeiros são obrigatórios. Os outros podem esperar — a pilha sobe sem
eles — mas cada um deixa um pedaço do produto sem funcionar, e a API avisa no
log quando o e-mail está desligado. O certificado não entra na lista: o Caddy,
que já vem na pilha, resolve sozinho.

**Por que São Paulo e não a Europa.** Um servidor alemão custa uns R$10 a menos
por mês e acrescenta cerca de 200 ms a cada carregamento. A página de resgate é
aberta por um estranho na rua, no 4G, com um cachorro desconhecido no colo —
nesse contexto, latência é a diferença entre esperar e desistir. Para tudo o
mais do produto a diferença seria irrelevante; para essa tela, não é.

**4 GB, não 2.** A construção da imagem da API roda Maven, que pede muito mais
memória do que a aplicação em si. Em 2 GB o build morre com um "Killed" que não
explica nada. O `deploy/provisionar.sh` cria área de troca para cobrir o pico,
mas com folga de memória você não precisa pensar nisso.

## Preparar o servidor

Num Ubuntu recém-criado, como root:

```bash
sudo bash deploy/provisionar.sh
```

Ele instala o Docker, cria área de troca, fecha o firewall deixando só SSH, 80
e 443, e agenda um backup diário do banco. Não cria a máquina, não registra o
domínio e não preenche o `.env` — isso é seu.

## Preencher o `.env`

Duas linhas merecem atenção especial.

**`URL_PUBLICA_TAG`** é a única do arquivo que **não tem conserto por software.**
Ela vai gravada no chip NFC. Uma vez gravada e entregue ao cliente, mudar o
domínio quebra todas as tags em campo — não há atualização que conserte um chip
que já está numa coleira. Confira antes de gravar o primeiro lote.

**`CORS_ORIGENS`** é a que falha em silêncio. O navegador de quem achou o pet
chama a API direto para confirmar a leitura, e é essa chamada que dispara o
aviso ao dono. Com o CORS errado, o navegador bloqueia, a página não acusa nada
e **o tutor simplesmente nunca é avisado.** A API recusa subir se ela apontar
para `localhost`, mas não tem como saber se o domínio está errado.

Gere os segredos, não invente:

```bash
openssl rand -base64 48   # JWT_SEGREDO
openssl rand -base64 32   # IP_PIMENTA
openssl rand -base64 24   # MYSQL_PASSWORD e MYSQL_ROOT_PASSWORD
```

## O proxy

Já vem na pilha: o Caddy termina o TLS e encaminha os dois nomes. Ele pede e
renova o certificado no Let's Encrypt sozinho — não há passo manual.

```
conectapet.com.br      → site
api.conectapet.com.br  → api
```

**Antes de subir**, o DNS dos dois nomes precisa apontar para o IP desta
máquina, e as portas 80 e 443 precisam estar abertas. É assim que o Let's
Encrypt confirma que o domínio é seu; sem isso o Caddy fica tentando em laço.

Só o proxy publica porta. Banco, API e site conversam pela rede interna do
compose e não ficam acessíveis de fora por outro caminho.

**A API precisa de nome público mesmo assim.** O navegador de quem achou o pet
busca a foto e confirma a leitura direto nela — não é só o site que fala com a
API. Por isso o site recebe dois endereços: `API_URL` interno, para as chamadas
que ele mesmo faz, e `API_URL_PUBLICA` para o que vai ao navegador. Dar a volta
pelo domínio público nas duas faria o site depender de DNS externo e do próprio
proxy para alcançar um container ao lado.

## Depois de subir

1. **Cadastre-se pelo site** como tutor normal, com o e-mail que será o de admin.
2. Ponha esse e-mail em `ADMIN_EMAIL` e `docker compose up -d` de novo. A conta
   é promovida a administrador na inicialização — nenhuma senha passa pelo
   arquivo de configuração.
3. Em `/admin`, gere o primeiro lote e **confira o endereço** na coluna que vai
   para o NFC antes de gravar qualquer chip.
4. Grave uma tag, encoste o celular e percorra o fluxo inteiro. É o teste que
   nenhuma suíte substitui.

## O que a pilha não resolve

- **Levar o backup para fora.** O `provisionar.sh` agenda um dump diário, mas
  ele fica no próprio servidor — o que não ajuda no dia em que a máquina é o
  problema. Copie `/var/backups/conectapet` para outro lugar (o mesmo bucket
  das fotos serve).
- **Migração dos dados de desenvolvimento.** Não existe: o banco de produção
  começa vazio, e o Flyway cria o esquema na primeira subida.
- **Escala.** Um container de cada. Antes de precisar de mais, o gargalo será o
  banco, não a aplicação.

## Verificar que está no ar

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://conectapet.com.br/
curl -s -o /dev/null -w "%{http_code}\n" https://api.conectapet.com.br/actuator/health
docker compose ps          # os três "healthy"
docker compose logs api    # confere que não há aviso de configuração
```

Se a API não subir, ela diz o motivo na primeira linha do erro: a validação de
configuração lista de uma vez tudo o que estiver apontando para desenvolvimento.
