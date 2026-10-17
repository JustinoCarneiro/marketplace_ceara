package com.onda.marketplace.performance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onda.marketplace.auth.*;
import com.onda.marketplace.message.MessageRepository;
import com.onda.marketplace.notification.NotificationService;
import com.onda.marketplace.payment.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Benchmark fechado opt-in: banco descartável próprio, HTTP somente loopback e dados sintéticos. */
@org.springframework.test.context.ActiveProfiles("test")
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver", "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
        "marketplace.outbox.delay-ms=3600000", "marketplace.background-check.delay-ms=3600000",
        "notification.push.enabled=false", "marketplace.gateway.mercadopago.enabled=false",
        "marketplace.ai.enabled=false", "storage.provider=stub", "notification.push.delay-ms=3600000", "marketplace.cpf-backfill.enabled=false",
        "spring.mail.host=", "management.health.mail.enabled=false", "logging.level.root=WARN"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerformanceBaselineTest {
    @Container static PostgreSQLContainer<?> db = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:15-3.4").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        db.start(); // PER_CLASS: o contexto Spring pode ser criado antes do callback Testcontainers.
        p.add("spring.datasource.url", db::getJdbcUrl);
        p.add("spring.datasource.username", db::getUsername);
        p.add("spring.datasource.password", db::getPassword);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.zaxxer.hikari.HikariDataSource pool;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    @Autowired TransactionRepository transactions;
    @Autowired OutboxEventRepository events;
    @Autowired OutboxEventWriter writer;
    final Map<String,Object> report = new LinkedHashMap<>();
    final List<Actor> actors = new ArrayList<>();
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).version(HttpClient.Version.HTTP_1_1).build();
    record Actor(UUID userId, String token) {}
    record Pending(UUID requestId, long start) {}
    final Queue<Double> geoTimes = new ConcurrentLinkedQueue<>();

    @BeforeAll void fixtures() throws Exception {
        report.put("revision",System.getProperty("performance.label","working-tree"));
        report.put("environment", Map.of("java",System.getProperty("java.version"), "processors",Runtime.getRuntime().availableProcessors(),
                "heapMaxBytes",Runtime.getRuntime().maxMemory(), "database","postgis/postgis:15-3.4", "transport","HTTP loopback; same host closed-loop", "startedAt",Instant.now().toString()));
        jdbc.update("""
            INSERT INTO users(nome,email,senha_hash,role)
            SELECT 'Sintetico '||i,'synthetic-'||i||'@example.test','not-a-login-password','ROLE_PROVIDER'
              FROM generate_series(1,10000) i
            """);
        jdbc.update("""
            INSERT INTO providers_profile(user_id,categoria,status_verificacao,localizacao)
            SELECT id,CASE WHEN n%2=0 THEN 'Elétrica' ELSE 'Hidráulica' END,'VERIFICADO',
                ST_SetSRID(ST_MakePoint(-38.526+(n%100-50)*0.002,-3.731+((n/100)%100-50)*0.002),4326)::geography
             FROM (SELECT id,split_part(split_part(email,'@',1),'-',2)::int n FROM users WHERE email LIKE 'synthetic-%@example.test') s
            """);
        jdbc.execute("ANALYZE users"); jdbc.execute("ANALYZE providers_profile");
        for(int i=0;i<64;i++) {
            User u=users.saveAndFlush(User.builder().nome("Ator sintetico").email("actor-"+i+"@example.test")
                    .senhaHash("not-a-login-password").role(i%2==0?UserRole.ROLE_CLIENT:UserRole.ROLE_PROVIDER).build());
            actors.add(new Actor(u.getId(),jwt.generateAccessToken(u,u.getRole())));
        }
        report.put("fixture",Map.of("providers",10000,"actors",64,"chatStreams",32,"queueEvents",400));
    }
    static String tag(String prefix) {
        var value=new StringBuilder(prefix);
        for(char c:UUID.randomUUID().toString().toCharArray()) value.append(c>='0' && c<='9' ? (char)('g'+c-'0') : c);
        return value.toString(); // Sem números que possam acionar o mascaramento de telefone.
    }
    URI uri(String path) { return URI.create("http://127.0.0.1:"+port+"/api/v1"+path); }
    HttpResponse<String> request(String method,String path,Actor actor,String body) throws Exception {
        var b=HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+actor.token());
        if(body==null) b.GET(); else b.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    static void parallel(int workers,int count,IntConsumer task) throws Exception {
        AtomicInteger next=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(workers)) {
            List<Future<?>> jobs=new ArrayList<>();
            for(int w=0;w<workers;w++) jobs.add(pool.submit(()->{ int i; while((i=next.getAndIncrement())<count) task.accept(i); }));
            for(var f:jobs) f.get(90,TimeUnit.SECONDS);
        }
    }
    static double ms(long start) { return (System.nanoTime()-start)/1_000_000.0; }
    Map<String,Object> metrics(Collection<Double> values,long start,int errors) {
        var sorted=values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        assertThat(sorted).isNotEmpty();
        double seconds=ms(start)/1000;
        return Map.of("samples",sorted.length,"errors",errors,"durationSeconds",seconds,"throughputPerSecond",sorted.length/seconds,
                "p50Ms",percentile(sorted,.5),"p95Ms",percentile(sorted,.95),"p99Ms",percentile(sorted,.99),"maxMs",sorted[sorted.length-1]);
    }
    static double percentile(double[] sorted,double p) { return sorted[(int)Math.ceil(p*sorted.length)-1]; }

    @Test @Order(1) void geobusca() throws Exception {
        String path="/providers/nearby?lat=-3.731&lng=-38.526&raio=5000&limite=20";
        for(int i=0;i<200;i++) assertThat(request("GET",path,actors.get(i%64),null).statusCode()).isEqualTo(200);
        var plan=jdbc.queryForObject("""
            EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON)
            SELECT pp.user_id FROM providers_profile pp JOIN users u ON u.id=pp.user_id
             WHERE u.ativo AND pp.status_verificacao='VERIFICADO' AND pp.cpf_conciliado
               AND pp.user_id<>? AND ST_DWithin(pp.localizacao,ST_SetSRID(ST_MakePoint(-38.526,-3.731),4326)::geography,5000)
             ORDER BY ST_Distance(pp.localizacao,ST_SetSRID(ST_MakePoint(-38.526,-3.731),4326)::geography) LIMIT 20
            """,String.class,actors.get(0).userId());
        report.put("geoPlan",json.readTree(plan));
        AtomicInteger errors=new AtomicInteger(); long start=System.nanoTime();
        parallel(20,1000,i->{
            long t=System.nanoTime();
            try {
                var r=request("GET",path+(i%2==0?"&categoria=El%C3%A9trica":""),actors.get(i%64),null);
                if(r.statusCode()!=200 || !json.readTree(r.body()).isArray() || json.readTree(r.body()).size()!=20) errors.incrementAndGet();
            } catch(Exception ex) { errors.incrementAndGet(); }
            geoTimes.add(ms(t));
        });
        var m=metrics(geoTimes,start,errors.get()); report.put("geo",m); save();
        assertThat(errors).hasValue(0);
        assertThat((double)m.get("p95Ms")).isLessThan(Double.parseDouble(System.getProperty("performance.geo-p95-ms","300")));
    }

    @Test @Order(2) void chatConcorrenteERecuperacao() throws Exception {
        List<UUID> requests=new ArrayList<>();
        for(int i=0;i<32;i++) {
            UUID id=UUID.randomUUID(); requests.add(id);
            jdbc.update("insert into service_requests(id,cliente_id,categoria,status,idempotency_key) values (?,?,'Teste','ACEITO',?)",id,actors.get(i*2).userId(),id.toString());
            jdbc.update("insert into proposals(service_request_id,prestador_id,valor,prazo_dias,status) values (?,?,10,1,'ACEITA')",id,actors.get(i*2+1).userId());
        }
        Queue<Double> timings=new ConcurrentLinkedQueue<>(); Queue<String> failures=new ConcurrentLinkedQueue<>();
        Map<String,Pending> pending=new ConcurrentHashMap<>(); Set<String> delivered=ConcurrentHashMap.newKeySet();
        List<Stream> streams=new ArrayList<>(); long start=System.nanoTime();
        try {
            for(int i=0;i<32;i++) streams.add(new Stream(requests.get(i),actors.get(i*2),pending,delivered,timings,failures));
            parallel(16,160,i->{
                UUID id=requests.get(i%32); String tag=tag("load-"); pending.put(tag,new Pending(id,System.nanoTime()));
                try { if(request("POST","/service-requests/"+id+"/messages",actors.get((i%32)*2+1),json.writeValueAsString(Map.of("conteudo",tag))).statusCode()!=201) failures.add("post-status"); }
                catch(Exception ex) { failures.add("post-error"); }
            });
            waitFor(()->timings.size()==160,30); assertThat(failures).isEmpty(); assertThat(timings).hasSize(160);
            waitFor(()->pool.getHikariPoolMXBean().getActiveConnections()==0,5);
            report.put("chatIdleDatabaseConnections",pool.getHikariPoolMXBean().getActiveConnections());
            report.put("chat",metrics(timings,start,failures.size())); save();
            // Perde a conexão, envia offline, reabre e recupera o histórico persistido.
            for(int i=0;i<16;i++) streams.get(i).close();
            for(int i=0;i<16;i++) {
                UUID id=requests.get(i); String offline=tag("offline-");
                assertThat(request("POST","/service-requests/"+id+"/messages",actors.get(i*2+1),json.writeValueAsString(Map.of("conteudo",offline))).statusCode()).isEqualTo(201);
                var reopened=new Stream(id,actors.get(i*2),pending,delivered,timings,failures); streams.add(reopened);
                var history=request("GET","/service-requests/"+id+"/messages",actors.get(i*2),null);
                assertThat(history.statusCode()).isEqualTo(200);
                assertThat(json.readTree(history.body()).findValuesAsText("conteudo")).contains(offline);
                String live=tag("reconnected-"); pending.put(live,new Pending(id,System.nanoTime()));
                assertThat(request("POST","/service-requests/"+id+"/messages",actors.get(i*2+1),json.writeValueAsString(Map.of("conteudo",live))).statusCode()).isEqualTo(201);
            }
            waitFor(()->timings.size()==176,30); assertThat(failures).isEmpty(); assertThat(timings).hasSize(176);
            report.put("chatRecovery",Map.of("reconnectedStreams",16,"offlineRecovered",16,"liveAfterReconnect",16,"errors",failures.size())); save();
        } finally {
            report.put("chatDiagnostics",Map.of("delivered",timings.size(),"errors",failures.size(),"errorKinds",new HashSet<>(failures)));
            save(); for(var s:streams) s.close();
        }
    }
    static void waitFor(java.util.function.BooleanSupplier done,int seconds) throws InterruptedException {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
        while(!done.getAsBoolean() && System.nanoTime()<deadline) Thread.sleep(20);
        assertThat(done.getAsBoolean()).isTrue();
    }
    final class Stream implements AutoCloseable {
        final InputStream input; final Thread reader; final CountDownLatch ready=new CountDownLatch(1);
        volatile boolean closed;
        Stream(UUID id,Actor actor,Map<String,Pending> pending,Set<String> delivered,Queue<Double> times,Queue<String> failures) throws Exception {
            var req=HttpRequest.newBuilder(uri("/service-requests/"+id+"/messages/stream")).header("Authorization","Bearer "+actor.token())
                    .header("Accept","text/event-stream").timeout(Duration.ofSeconds(60)).build();
            var response=http.send(req,HttpResponse.BodyHandlers.ofInputStream());
            input=response.body(); assertThat(response.statusCode()).isEqualTo(200);
            reader=Thread.ofVirtual().start(()->{
                try(var lines=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))) {
                    String line,event="";StringBuilder data=new StringBuilder();
                    while((line=lines.readLine())!=null) {
                        if(line.startsWith("event:")) event=line.substring(6).trim();
                        else if(line.startsWith("data:")) data.append(line.substring(5).trim());
                        else if(line.isEmpty()) {
                            if(event.equals("ready")) ready.countDown();
                            if(event.equals("message")) {
                                JsonNode msg=json.readTree(data.toString()); String messageId=msg.path("id").asText();
                                Pending expected=pending.get(msg.path("conteudo").asText());
                                if(expected!=null) {
                                    if(!id.equals(expected.requestId())) failures.add("cross-request-delivery");
                                    else if(delivered.add(messageId)) times.add(ms(expected.start()));
                                    else failures.add("duplicate-delivery");
                                }
                            }
                            data.setLength(0); event="";
                        }
                    }
                } catch(Exception ex) { if(!closed) failures.add("stream-error"); }
            });
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
        }
        public void close() throws Exception { closed=true; input.close(); reader.join(2000); }
    }

    @Test @Order(3) void filaComOitoWorkers() throws Exception {
        events.deleteAll();
        Set<UUID> called=ConcurrentHashMap.newKeySet(); AtomicInteger duplicates=new AtomicInteger();
        Queue<Double> timings=new ConcurrentLinkedQueue<>(); Map<UUID,Long> queued=new ConcurrentHashMap<>();
        for(int i=0;i<400;i++) {
            UUID request=UUID.randomUUID();
            jdbc.update("insert into service_requests(id,cliente_id,categoria,status,idempotency_key) values (?,?,'Teste','CONCLUIDO',?)",request,actors.get(0).userId(),request.toString());
            var tx=new Transaction(request,BigDecimal.TEN,BigDecimal.ONE,new BigDecimal("0.10"),PaymentMethod.PIX,request.toString()); tx.reter();
            tx=transactions.saveAndFlush(tx);
            events.saveAndFlush(new OutboxEvent("transaction",tx.getId(),"PAYMENT_RELEASED","{}"));
            queued.put(tx.getId(),System.nanoTime());
        }
        GatewayService gateway=new GatewayService() {
            public String cobrar(Transaction t) { throw new AssertionError("unexpected charge"); }
            public void reembolsar(Transaction t) { throw new AssertionError("unexpected refund"); }
            public void liberar(Transaction t) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                if(!called.add(t.getId())) duplicates.incrementAndGet();
                try { Thread.sleep(10); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
                timings.add(ms(queued.get(t.getId())));
            }
        };
        long start=System.nanoTime();
        parallel(8,8,i->{
            var worker=new OutboxProcessor(transactions,gateway,mock(NotificationService.class),writer);
            for(int j=0;j<10;j++) worker.processOutbox();
        });
        report.put("queueChecks",Map.of("expected",400,"uniqueCalls",called.size(),"duplicates",duplicates.get(),
                "completed",events.findByStatus(OutboxStatus.PROCESSADO).size(),"claimedRemaining",events.findByStatus(OutboxStatus.PROCESSANDO).size())); save();
        assertThat(called).hasSize(400); assertThat(duplicates).hasValue(0);
        assertThat(events.findByStatus(OutboxStatus.PROCESSADO)).hasSize(400);
        assertThat(events.findByStatus(OutboxStatus.PROCESSANDO)).isEmpty();
        assertThat(transactions.findByStatusPagamento(TransactionStatus.LIBERADO)).hasSize(400);
        var result=new LinkedHashMap<>(metrics(timings,start,duplicates.get()));
        result.put("metric","queue wait from enqueue through external stub completion (not provider latency)");
        result.put("workerCount",8); result.put("gatewayStubDelayMs",10); report.put("queue",result); save();
    }
    @AfterAll void closeClient() throws Exception { save(); http.close(); }
    void save() throws Exception {
        Path output=Path.of(System.getProperty("performance.report-dir","target/performance"));
        Files.createDirectories(output); json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("metrics.json").toFile(),report);
    }
}
