/// <reference types="astro/client" />

import type { Opcoes, Resposta } from './lib/api';

declare global {
  namespace App {
    interface Locals {
      /** Chama a API ja com o cookie da sessao e renova sozinha se expirou. */
      api: <T>(caminho: string, opcoes?: Opcoes) => Promise<Resposta<T>>;
      /** Idem, devolvendo a resposta crua — para imagem e outros nao-JSON. */
      apiBruto: (caminho: string, opcoes?: Opcoes) => Promise<Response>;
      /** Ha indicio de sessao no cookie. Quem manda mesmo e a API. */
      autenticado: boolean;
    }
  }
}

export {};
