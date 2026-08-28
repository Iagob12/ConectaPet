package br.com.conectapet.comum.util;

import java.time.Duration;

/**
 * Piso de tempo de resposta.
 *
 * Tempo constante de verdade e inatingivel em JVM com JIT e pool de conexoes.
 * O que da para garantir e um piso: toda resposta leva no minimo o mesmo tempo,
 * o que elimina o vazamento observavel entre "codigo existe" e "codigo nao
 * existe" nas rotas publicas de tag.
 */
public final class PisoDeTempo {

    private PisoDeTempo() {}

    public static <T> T aoMenos(Duration piso, java.util.function.Supplier<T> trabalho) {
        long inicio = System.nanoTime();
        try {
            return trabalho.get();
        } finally {
            long faltam = piso.toNanos() - (System.nanoTime() - inicio);
            if (faltam > 0) {
                try {
                    Thread.sleep(faltam / 1_000_000L, (int) (faltam % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
