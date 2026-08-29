# ConectaPet — site

Astro 5 em SSR, servindo três coisas do mesmo processo: a vitrine em `/`, a página de
resgate em `/p/{codigo}` e o painel em `/app` e `/admin`.

O navegador nunca fala com a API direto: a página busca no servidor e devolve HTML pronto.
Isso resolve CORS, mantém o token de sessão fora do alcance de qualquer JavaScript de
página, e faz os formulários funcionarem sem JavaScript nenhum.

## Rodar

```bash
npm run dev      # http://localhost:4321, precisa da API em :8080
npm run build    # gera dist/server
npm test         # constrói e roda os 32 testes
```

A API é apontada por `API_URL` **em tempo de execução**:

```bash
API_URL=https://api.conectapet.com.br PORT=4321 node dist/server/entry.mjs
```

## Antes de subir para produção

**Exporte `URL_SITE` antes do `astro build`**, com o mesmo endereço que o cliente digita:

```bash
URL_SITE=https://conectapet.com.br npm run build
```

Não é detalhe de configuração. Sem isso, a lista de hosts permitidos fica vazia, o Astro
descarta o cabeçalho `Host` e passa a calcular a origem da requisição como
`http://localhost`. A proteção contra CSRF compara essa origem com o `Origin` que o
navegador manda, nunca casa, e **todo envio de formulário vira 403** — login, cadastro e
ativação inclusive. O site sobe, as páginas abrem, e nada que dependa de POST funciona.

Em desenvolvimento não aparece, porque ali a origem vem da requisição. Só quebra no build
de produção. Há um teste que trava isso (`formulário da própria origem passa`).

## Os testes

`test/lib.test.ts` cobre as decisões que o front toma sozinho: validação do código da tag,
repasse de cookies na renovação de sessão, e formatação de data no fuso de São Paulo.

`test/paginas.test.ts` sobe o **build de produção** com uma API dublada e exercita as telas
por HTTP — que é como um cliente as exercita. Testar o render por dentro não pegaria
redirecionamento, cookie nem o que acontece quando a API cai.

O que esses testes protegem, em ordem de quanto dói quebrar:

- A página de resgate mostra o pet, o `tel:` e o `wa.me`, e não entra em índice de busca.
- Tag sem perfil vai direto para o cadastro; código inexistente vai para o mesmo lugar,
  para a página não virar um verificador de quais códigos existem.
- Com a API fora do ar, a tela **não culpa a conexão de quem está lendo** e mostra o código
  da tag, que é a única coisa que ainda serve.
- Com a API travada, a espera tem teto: falhar avisando é melhor que carregar para sempre.
- Rotas de sessão mandam para o login guardando o destino.
