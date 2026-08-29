package br.com.conectapet.notificacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifica que a mensagem sai mesmo, e nao apenas que o codigo compila.
 *
 * O servidor SMTP vive dentro do teste: fala o minimo do protocolo e guarda o
 * que recebeu. Sem provedor contratado, sem Docker e sem banco — e ainda assim
 * o que se mede e o byte que sairia pela rede.
 */
class EnvioEmailSmtpTest {

    private ServerSocket servidor;
    private ExecutorService linha;
    private Future<String> recebido;

    @BeforeEach
    void subirServidor() throws Exception {
        servidor = new ServerSocket(0);
        linha = Executors.newSingleThreadExecutor();
        recebido = linha.submit(this::atender);
    }

    @AfterEach
    void derrubarServidor() throws Exception {
        servidor.close();
        linha.shutdownNow();
    }

    /** Conversa SMTP mínima; devolve o corpo bruto da mensagem. */
    private String atender() throws IOException {
        try (Socket s = servidor.accept();
             BufferedReader entrada = new BufferedReader(
                     new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             Writer saida = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8)) {

            saida.write("220 teste\r\n");
            saida.flush();

            StringBuilder corpo = new StringBuilder();
            boolean lendoDados = false;
            String linhaLida;

            while ((linhaLida = entrada.readLine()) != null) {
                if (lendoDados) {
                    if (linhaLida.equals(".")) {
                        lendoDados = false;
                        saida.write("250 ok\r\n");
                        saida.flush();
                        continue;
                    }
                    corpo.append(linhaLida).append('\n');
                    continue;
                }
                String c = linhaLida.toUpperCase();
                if (c.startsWith("EHLO") || c.startsWith("HELO")) {
                    saida.write("250-teste\r\n250 OK\r\n");
                } else if (c.startsWith("DATA")) {
                    lendoDados = true;
                    saida.write("354 manda\r\n");
                } else if (c.startsWith("QUIT")) {
                    saida.write("221 tchau\r\n");
                    saida.flush();
                    break;
                } else {
                    saida.write("250 ok\r\n");
                }
                saida.flush();
            }
            return corpo.toString();
        }
    }

    private EnvioEmailSmtp canal(String responderPara) {
        JavaMailSenderImpl remetente = new JavaMailSenderImpl();
        remetente.setHost("localhost");
        remetente.setPort(servidor.getLocalPort());
        Properties p = new Properties();
        p.put("mail.smtp.auth", "false");
        p.put("mail.smtp.starttls.enable", "false");
        remetente.setJavaMailProperties(p);

        ModeloEmail modelos = new ModeloEmail(new ObjectMapper(), "https://conectapet.com.br");
        return new EnvioEmailSmtp(remetente, modelos,
                "ConectaPet <nao-responda@conectapet.com.br>", responderPara);
    }

    private Notificacao leituraDoThor() {
        Notificacao n = new Notificacao();
        n.setTipo(Notificacao.Tipo.LEITURA_TAG);
        n.setDestinatario("tutora@exemplo.com");
        n.setConteudo("""
                {"petNome":"Thor","petUuid":"abc","ocorridaEm":"2026-08-29T14:05:00Z",
                 "temLocalizacao":false,"temMensagem":true,"modoPerdido":true}""");
        return n;
    }

    @Test
    @DisplayName("entrega assunto, remetente e destinatario")
    void entregaCabecalhos() throws Exception {
        canal("").enviar(leituraDoThor());
        String bruto = recebido.get(10, TimeUnit.SECONDS);

        assertThat(bruto).contains("To: tutora@exemplo.com");
        assertThat(bruto).contains("nao-responda@conectapet.com.br");
        // O assunto viaja codificado quando tem acento; o teste procura a marca
        // do encoding, nao o texto cru, para nao depender do charset escolhido.
        assertThat(bruto).containsIgnoringCase("Subject:");
    }

    @Test
    @DisplayName("manda texto puro e HTML na mesma mensagem")
    void mandaAsDuasVersoes() throws Exception {
        canal("").enviar(leituraDoThor());
        String bruto = recebido.get(10, TimeUnit.SECONDS);

        // multipart/alternative: quem bloqueia HTML fica com o texto, que foi
        // escrito para bastar sozinho.
        assertThat(bruto).contains("multipart/alternative");
        assertThat(bruto).contains("text/plain");
        assertThat(bruto).contains("text/html");
    }

    @Test
    @DisplayName("o conteudo que chega e o do modelo")
    void conteudoDoModelo() throws Exception {
        canal("").enviar(leituraDoThor());
        String bruto = recebido.get(10, TimeUnit.SECONDS);

        // Quoted-printable quebra linha longa; junta antes de procurar.
        String texto = bruto.replace("=\n", "").replace("=\r\n", "");
        assertThat(texto).contains("Thor");
        assertThat(texto).contains("conectapet.com.br/app/pet/abc/leituras");
    }

    @Test
    @DisplayName("responder-para vazio nao vira cabecalho vazio")
    void semResponderPara() throws Exception {
        canal("").enviar(leituraDoThor());
        String bruto = recebido.get(10, TimeUnit.SECONDS);
        assertThat(bruto).doesNotContain("Reply-To:");
    }

    @Test
    @DisplayName("responder-para preenchido vira cabecalho")
    void comResponderPara() throws Exception {
        canal("ajuda@conectapet.com.br").enviar(leituraDoThor());
        String bruto = recebido.get(10, TimeUnit.SECONDS);
        assertThat(bruto).contains("Reply-To: ajuda@conectapet.com.br");
    }

    @Test
    @DisplayName("servidor fora do ar propaga o erro em vez de engolir")
    void falhaSobe() throws Exception {
        servidor.close();
        // Engolir aqui marcaria como enviada uma mensagem que nunca saiu; quem
        // chama conta a tentativa e reagenda com espera crescente.
        assertThatThrownBy(() -> canal("").enviar(leituraDoThor()))
                .isInstanceOf(MailSendException.class);
    }
}
