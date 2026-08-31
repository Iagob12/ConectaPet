import type { APIContext } from 'astro';

export type Conta = {
  uuid: string; email: string; nome: string;
  telefonePrincipalExibicao: string | null; telefonePrincipalE164: string | null;
  telefoneSecundarioExibicao: string | null; telefoneSecundarioE164: string | null;
  whatsappExibicao: string | null; whatsappE164: string | null;
  emailVerificado: boolean; papel: string;
  /** Sempre 'FREE' nesta versao: nao ha plano pago. O campo fica para o dia
   *  em que houver, mas nenhuma tela decide nada com ele. */
  plano: string;
  limiteContatos: number;
};

export type Pet = {
  uuid: string; nome: string; especie: string; raca: string | null; sexo: string | null;
  dataNascimento: string | null; pesoKg: string | null; cor: string | null;
  castrado: boolean | null; numeroMicrochip: string | null;
  cidade: string | null; estado: string | null; observacoes: string | null;
  temFoto: boolean; pronto: boolean; oQueFalta: string | null;
};

export type Tag = {
  uuid: string; codigoPublico: string; modelo: string; status: string;
  modoPerdido: boolean; urlPublica: string; petUuid: string | null;
  reivindicadaEm: string | null; enviadaEm: string | null; desativadaEm: string | null;
};

export type Visibilidade = {
  mostrarTelefone: boolean; mostrarWhatsapp: boolean; mostrarContatosEmergencia: boolean;
  mostrarSaude: boolean; mostrarCidade: boolean; mostrarMicrochip: boolean;
  mensagemPersonalizada: string | null;
};

export type Saude = {
  alergias: string | null; medicacaoContinua: string | null; condicoes: string | null;
  cuidadosEspeciais: string | null; veterinarioNome: string | null;
  veterinarioTelefone: string | null; clinica: string | null;
};

export type Contato = {
  uuid: string; nome: string; telefone: string; parentesco: string | null; ordem: number;
};

export type Leitura = {
  uuid: string; ocorridaEm: string; origem: string;
  cidade: string | null; regiao: string | null; pais: string | null;
  localizacaoCompartilhada: boolean;
  latitude: string | null; longitude: string | null; precisaoM: number | null;
  mensagemDeQuemEncontrou: string | null; telefoneDeQuemEncontrou: string | null;
};

/**
 * Manda para o login guardando aonde a pessoa queria ir.
 *
 * Sem o `next`, quem clica num link do painel com a sessao vencida faz login e
 * cai na lista de pets, tendo que reencontrar sozinha a tela onde estava.
 */
export function paraLogin(ctx: APIContext): Response {
  const destino = ctx.url.pathname + ctx.url.search;
  return ctx.redirect('/entrar?next=' + encodeURIComponent(destino));
}

/** Data e hora em pt-BR, no fuso de Sao Paulo. */
export function quando(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit', timeZone: 'America/Sao_Paulo',
  });
}

export function dia(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('pt-BR', {
    day: '2-digit', month: 'short', year: 'numeric', timeZone: 'America/Sao_Paulo',
  });
}

export const ESPECIES: Record<string, string> = {
  CACHORRO: 'Cachorro', GATO: 'Gato', OUTRO: 'Outro',
};

/**
 * Como a leitura chegou. Sao os tres estados do backend, e a diferenca importa
 * para o tutor: so CLIENTE prova que uma pessoa de verdade abriu a pagina.
 */
export const ORIGENS: Record<string, string> = {
  CLIENTE: 'Alguém abriu a página do seu pet',
  SERVIDOR: 'A página foi carregada',
  ROBO: 'Pré-visualização automática de link',
};

/** Campo de formulario que pode vir vazio: string vazia vira null para a API. */
export function texto(d: FormData, nome: string): string | null {
  const v = String(d.get(nome) ?? '').trim();
  return v === '' ? null : v;
}

export function marcado(d: FormData, nome: string): boolean {
  return d.get(nome) != null;
}
