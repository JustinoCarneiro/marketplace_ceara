package com.onda.marketplace.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Os três remetentes de e-mail ao USUÁRIO (US35): SMTP, arquivo (dev/CI) e desligado. */
class UserMailSenderTest {

    @TempDir Path dir;

    // ── SMTP ──

    @Test
    void javaMail_montaAMensagemParaODestinatarioEEnvia() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        var sender = new JavaMailUserMailSender(smtp, "no-reply@onda.app");

        sender.enviar("ana@example.com", "Assunto", "Corpo com código ABCD-2345");

        ArgumentCaptor<SimpleMailMessage> msg = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(msg.capture());
        assertThat(msg.getValue().getFrom()).isEqualTo("no-reply@onda.app");
        assertThat(msg.getValue().getTo()).containsExactly("ana@example.com");
        assertThat(msg.getValue().getSubject()).isEqualTo("Assunto");
        assertThat(msg.getValue().getText()).contains("ABCD-2345");
        assertThat(sender.ativo()).isTrue();
    }

    @Test
    void javaMail_falhaDoSmtp_viraNotificationDeliveryException_semVazarEnderecoNemTexto() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        doThrow(new MailSendException("falha ao entregar para ana@example.com: ABCD-2345"))
                .when(smtp).send(any(SimpleMailMessage.class));
        var sender = new JavaMailUserMailSender(smtp, "no-reply@onda.app");

        assertThatThrownBy(() -> sender.enviar("ana@example.com", "Assunto", "Corpo ABCD-2345"))
                .isInstanceOf(NotificationDeliveryException.class)
                // a mensagem da exceção que sobe NÃO carrega o endereço nem o código
                .hasMessageNotContaining("ana@example.com")
                .hasMessageNotContaining("ABCD-2345");
    }

    // ── Arquivo (dev/CI) ──

    @Test
    void arquivo_gravaDestinoAssuntoETexto() throws Exception {
        var sender = new FileSinkUserMailSender(dir);

        sender.enviar("ana@example.com", "Seu código", "Código: ABCD-2345");

        try (Stream<Path> arquivos = Files.list(dir)) {
            List<Path> lista = arquivos.toList();
            assertThat(lista).hasSize(1);
            String conteudo = Files.readString(lista.get(0));
            assertThat(conteudo).contains("Para: ana@example.com").contains("Assunto: Seu código")
                    .contains("Código: ABCD-2345");
            assertThat(lista.get(0).getFileName().toString()).endsWith("-ana@example.com.txt");
        }
        assertThat(sender.ativo()).isTrue();
    }

    @Test
    void arquivo_oDestinoVemDeEntradaDoUsuario_entaoONomeDoArquivoNuncaSaiDoDiretorio() throws Exception {
        var sender = new FileSinkUserMailSender(dir);

        sender.enviar("../../../etc/passwd@x.com", "A", "B");
        sender.enviar("a/b\\c@x.com", "A", "B");

        try (Stream<Path> arquivos = Files.list(dir)) {
            assertThat(arquivos.toList()).hasSize(2)
                    .allSatisfy(p -> {
                        assertThat(p.getParent()).isEqualTo(dir);
                        assertThat(p.getFileName().toString()).doesNotContain("/").doesNotContain("\\");
                    });
        }
    }

    @Test
    void arquivo_criaODiretorioSeNaoExiste() {
        var sender = new FileSinkUserMailSender(dir.resolve("novo/subdir"));

        sender.enviar("ana@example.com", "A", "B");

        assertThat(dir.resolve("novo/subdir")).isNotEmptyDirectory();
    }

    // ── Desligado ──

    @Test
    void desligado_naoEnviaNada_eDeclaraQueNaoEstaAtivo() {
        var sender = new NoOpUserMailSender();

        assertThat(sender.ativo()).isFalse();
        assertThatCode(() -> sender.enviar("ana@example.com", "A", "código ABCD-2345")).doesNotThrowAnyException();
    }
}
