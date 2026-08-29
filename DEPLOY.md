# Colocar o ConectaPet no ar

Três serviços: banco, API e site. O `docker-compose.yml` sobe os três e os liga
entre si.

```bash
cp .env.producao.example .env
# preencha o .env (a seção abaixo diz o que cada linha faz)
docker compose up -d --build
```

## Antes: o que só você pode obter

| Item | Onde | Sem isso |
|---|---|---|
| Uma máquina | VPS, Fly, Railway, Render, Hetzner… | não há onde rodar |
| Um domínio | registro.br para `.com.br` | as tags apontariam para um IP que muda |
| Certificado TLS | Caddy ou Traefik na frente (Let's Encrypt) | o cookie de sessão trafega aberto |
| Provedor de e-mail | SES, Resend, Postmark | o tutor não recebe aviso nem recupera a senha |
| Bucket S3/R2 | Cloudflare R2, AWS S3 | as fotos não sobrevivem a trocar de máquina |

Os três primeiros são obrigatórios. Os dois últimos podem esperar — a pilha sobe
sem eles — mas cada um deixa um pedaço do produto sem funcionar, e a API avisa
no log quando o e-mail está desligado.

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

- **Backup do banco.** O volume sobrevive a redeploy, não a perder a máquina.
  Um `mysqldump` diário para fora do servidor é o mínimo.
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
