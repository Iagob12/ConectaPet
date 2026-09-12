export interface Artigo {
  slug: string;
  titulo: string;
  resumo: string;
  categoria: string;
  tempo: string;
  imagem: string;
  alt: string;
  publicado: string;
  atualizado: string;
}

export const artigos: Artigo[] = [
  {
    slug: 'cachorro-fugiu-como-procurar',
    titulo: 'Meu cachorro fugiu: como procurar nas primeiras horas',
    resumo: 'Um plano de busca prático para organizar contatos, procurar perto de casa e divulgar sem perder informações importantes.',
    categoria: 'Pet perdido',
    tempo: '8 min de leitura',
    imagem: '/imagens/home-resgate-conectapet.webp',
    alt: 'Tutor usando o celular para ajudar na busca de um cachorro perdido',
    publicado: '2026-09-12',
    atualizado: '2026-09-12',
  },
  {
    slug: 'como-funciona-nfc-no-celular',
    titulo: 'Como funciona o NFC no celular para ler uma tag pet',
    resumo: 'Veja onde aproximar o aparelho, como ativar o NFC e o que fazer quando a notificação não aparece.',
    categoria: 'Tecnologia NFC',
    tempo: '6 min de leitura',
    imagem: '/imagens/ativacao-passo-2-aproximar.webp',
    alt: 'Parte traseira de um celular sendo aproximada de uma tag NFC para pet',
    publicado: '2026-09-12',
    atualizado: '2026-09-12',
  },
  {
    slug: 'o-que-colocar-na-identificacao-do-pet',
    titulo: 'O que colocar na identificação do pet',
    resumo: 'Os dados que realmente ajudam no reencontro e o que é melhor manter privado no perfil do animal.',
    categoria: 'Identificação pet',
    tempo: '6 min de leitura',
    imagem: '/imagens/pet-perfil-exemplo.webp',
    alt: 'Exemplo de perfil digital com foto e contatos de um cachorro identificado',
    publicado: '2026-09-12',
    atualizado: '2026-09-12',
  },
  {
    slug: 'achei-um-cachorro-perdido-o-que-fazer',
    titulo: 'Achei um cachorro perdido: o que fazer?',
    resumo: 'Um passo a passo seguro para proteger o animal, procurar o tutor e divulgar o encontro.',
    categoria: 'Pet encontrado',
    tempo: '6 min de leitura',
    imagem: '/imagens/home-resgate-conectapet.webp',
    alt: 'Pessoa usando o celular para identificar um cachorro encontrado',
    publicado: '2026-09-11',
    atualizado: '2026-09-12',
  },
  {
    slug: 'tag-nfc-plaquinha-microchip-ou-gps',
    titulo: 'Tag NFC, plaquinha, microchip ou GPS?',
    resumo: 'Entenda a função de cada identificação e como combinar soluções sem confundir contato com rastreamento.',
    categoria: 'Identificação pet',
    tempo: '7 min de leitura',
    imagem: '/imagens/home-ativacao-nfc.webp',
    alt: 'Celular sendo aproximado de uma identificação NFC para pet',
    publicado: '2026-09-11',
    atualizado: '2026-09-12',
  },
  {
    slug: 'como-evitar-que-seu-cachorro-se-perca',
    titulo: 'Como evitar que seu cachorro se perca',
    resumo: 'Cuidados simples para passeios, portões, identificação e atualização dos contatos do tutor.',
    categoria: 'Prevenção',
    tempo: '6 min de leitura',
    imagem: '/imagens/pet-perfil-exemplo.webp',
    alt: 'Cachorro identificado com perfil digital para contato do tutor',
    publicado: '2026-09-11',
    atualizado: '2026-09-12',
  },
];

export const caminhoDoArtigo = (artigo: Artigo) => `/blog/${artigo.slug}`;
