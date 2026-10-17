package com.onda.marketplace.e2e;

import com.onda.marketplace.admin.ProviderAdminService;
import com.onda.marketplace.auth.*;
import com.onda.marketplace.provider.*;
import com.onda.marketplace.servicerequest.*;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@ActiveProfiles("e2e")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties={"marketplace.outbox.delay-ms=3600000","spring.jpa.properties.hibernate.generate_statistics=true"})
class E2EDomainProjectionTest {
    @Container static PostgreSQLContainer<?> db=new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:15-3.4").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",db::getJdbcUrl);p.add("spring.datasource.username",db::getUsername);p.add("spring.datasource.password",db::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ProviderProfileRepository profiles;
    @Autowired ProviderAdminService admin;
    @Autowired ProviderPublicService publicProfiles;
    @Autowired ServiceRequestService requests;
    @Autowired EntityManagerFactory emf;
    @BeforeEach void reset() { jdbc.execute("TRUNCATE users CASCADE"); }
    UUID user(String name) {
        var id=UUID.randomUUID();
        jdbc.update("insert into users(id,nome,email,senha_hash,role) values (?,?,?,'disabled','ROLE_PROVIDER')",id,name,id+"@example.test");
        return id;
    }
    void profile(UUID user,String status) {
        jdbc.update("insert into providers_profile(user_id,categoria,status_verificacao) values (?,'Elétrica',?)",user,status);
    }
    UUID request(UUID owner,UUID provider,String status,int hour,int responseMinutes,int lateMinutes,int value) {
        var id=UUID.randomUUID();var created=Instant.parse("2026-01-01T00:00:00Z").plusSeconds(hour*3600L);
        jdbc.update("insert into service_requests(id,cliente_id,categoria,status,idempotency_key,created_at,updated_at,iniciado_em) values (?,?,'Elétrica',?,?,?, ?,?)",
                id,owner,status,id.toString(),Timestamp.from(created),Timestamp.from(created),Timestamp.from(created.plusSeconds(lateMinutes*60L)));
        jdbc.update("insert into proposals(service_request_id,prestador_id,valor,prazo_dias,status,created_at,horario_proposto) values (?,?,?,1,'ACEITA',?,?)",
                id,provider,value,Timestamp.from(created.plusSeconds(responseMinutes*60L)),Timestamp.from(created));
        return id;
    }
    @Test void resumoAdminTemIdentidadeStatusENomeEmUmaConsulta() {
        UUID first=user("Prestador um"),second=user("Conta removida");
        profile(first,"VERIFICADO");profile(second,"SUSPENSO");
        jdbc.update("update users set ativo=false,excluido_em=now() where id=?",second);
        jdbc.update("update providers_profile set cpf_conciliado=false where user_id=?",first);
        var statistics=emf.unwrap(SessionFactory.class).getStatistics();statistics.clear();
        var rows=admin.listar(null);
        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(r->r.id().equals(first)).singleElement().satisfies(r->{
            assertThat(r.nome()).isEqualTo("Prestador um"); assertThat(r.cpfConciliado()).isFalse();
        });
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(admin.listar(ProviderStatus.SUSPENSO)).extracting(r->r.id()).containsExactly(second);
    }
    @Test void joinsPreservamTemposPontualidadeHistoricoEConsultaEmLote() {
        UUID owner=user("Cliente sintético"),provider=user("Prestador sintético");profile(provider,"VERIFICADO");
        UUID first=request(owner,provider,"CONCLUIDO",1,10,10,150);
        UUID second=request(owner,provider,"CANCELADO",2,20,45,300);
        var dto=publicProfiles.buscarPorUserId(provider);
        assertThat(dto.nome()).isEqualTo("Prestador sintético");
        assertThat(dto.tempoRespostaMin()).isEqualTo(15);assertThat(dto.pontualidadePct()).isEqualTo(50);
        assertThat(dto.precoMin()).isEqualByComparingTo("150");assertThat(dto.precoMax()).isEqualByComparingTo("300");
        UUID active=request(owner,provider,"EM_ANDAMENTO",3,5,0,200);
        var stats=emf.unwrap(SessionFactory.class).getStatistics();stats.clear();
        var history=requests.listarHistoricoDoPrestador(provider);
        assertThat(history).extracting(ProviderHistoryDto::id).containsExactly(second,first);
        assertThat(history).extracting(ProviderHistoryDto::clienteNome).containsOnly("Cliente sintético");
        assertThat(stats.getPrepareStatementCount()).isEqualTo(3);
        assertThat(requests.buscarPedidoAtivoDoPrestador(provider)).isEqualTo(active);
    }
    @Test void backfillMantemFiltrosDeVersaoEExclusaoComIds() {
        UUID missing=user("Sem hash"),current=user("Versão atual"),deleted=user("Excluído");
        for(UUID id:List.of(missing,current,deleted)) {
            profile(id,"EM_VERIFICACAO");jdbc.update("update providers_profile set cpf_cifrado='synthetic-cipher' where user_id=?",id);
        }
        jdbc.update("update users set cpf_hash='synthetic-hash',cpf_hash_versao=2 where id=?",current);
        jdbc.update("update users set ativo=false,excluido_em=now() where id=?",deleted);
        assertThat(profiles.semHashDoCpf(2)).extracting(ProviderCpfBackfill.PerfilSemHash::getUserId).containsExactly(missing);
        assertThat(profiles.comHashNaVersao(2)).extracting(ProviderCpfBackfill.PerfilSemHash::getUserId).containsExactly(current);
    }
}
