package com.onda.marketplace.notification;

import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.provider.CpfEncryptor;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class PushStore {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final CpfEncryptor cipher;
    PushStore(JdbcTemplate jdbc, UserRepository users, CpfEncryptor cipher) {
        this.jdbc = jdbc; this.users = users; this.cipher = cipher;
    }
    @Transactional
    public String reserve(UUID userId, String token) {
        users.findByIdComTrava(userId).filter(u->u.isAtivo() && !u.isExcluido())
                .orElseThrow(()->new BusinessException("ACCOUNT_UNAVAILABLE","Conta indisponível."));
        Integer attempts=jdbc.queryForObject("SELECT count(*) FROM push_registration_fences WHERE user_id=? AND created_at>CURRENT_TIMESTAMP-INTERVAL '1 hour'",Integer.class,userId);
        if(attempts!=null && attempts>=120) throw new BusinessException("PUSH_REGISTRATION_LIMIT","Tente ativar notificações mais tarde.");
        byte[] secret=new byte[32];new java.security.SecureRandom().nextBytes(secret);
        String capability=HexFormat.of().formatHex(secret);
        jdbc.update("INSERT INTO push_registration_fences(capability_hash,user_id,token_hash) VALUES(?,?,?)",hash(capability),userId,hash(token));
        return capability;
    }
    @Transactional
    public void register(UUID userId, String token, String revocationKey) {
        users.findByIdComTrava(userId).filter(u -> u.isAtivo() && !u.isExcluido())
                .orElseThrow(() -> new BusinessException("ACCOUNT_UNAVAILABLE", "Conta indisponível."));
        var reservation=jdbc.query("""
            SELECT revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP AS valid
            FROM push_registration_fences WHERE capability_hash=? AND user_id=? AND token_hash=? FOR UPDATE
            """,(rs,n)->rs.getBoolean(1),hash(revocationKey),userId,hash(token));
        if(reservation.isEmpty() || !reservation.getFirst())
            throw new BusinessException("PUSH_REGISTRATION_REVOKED","Ativação expirada ou revogada. Tente novamente.");
        jdbc.update("DELETE FROM push_devices WHERE user_id=? AND updated_at<=CURRENT_TIMESTAMP-INTERVAL '1 day'",userId);
        jdbc.update("""
            INSERT INTO push_devices(id,user_id,token_hash,token_cipher,binding,revocation_hash,updated_at)
            VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP)
            ON CONFLICT(token_hash) DO UPDATE SET user_id=EXCLUDED.user_id,
            token_cipher=EXCLUDED.token_cipher, revocation_hash=EXCLUDED.revocation_hash, updated_at=CURRENT_TIMESTAMP,
            binding=CASE WHEN push_devices.user_id=EXCLUDED.user_id THEN push_devices.binding ELSE EXCLUDED.binding END
            """, UUID.randomUUID(), userId, hash(token), cipher.encrypt(token), UUID.randomUUID(), hash(revocationKey));
        Integer count = jdbc.queryForObject("SELECT count(*) FROM push_devices WHERE user_id=?", Integer.class, userId);
        if (count != null && count > 5) throw new BusinessException("PUSH_DEVICE_LIMIT", "Limite de dispositivos atingido.");
    }
    @Transactional
    public void unregister(UUID userId, String token) {
        users.findByIdComTrava(userId);
        jdbc.update("DELETE FROM push_devices WHERE user_id=? AND token_hash=?", userId, hash(token));
    }
    @Transactional
    public void revoke(String token, String revocationKey) {
        jdbc.update("UPDATE push_registration_fences SET revoked_at=CURRENT_TIMESTAMP WHERE capability_hash=? AND token_hash=?",hash(revocationKey),hash(token));
        jdbc.update("DELETE FROM push_devices WHERE token_hash=? AND revocation_hash=?", hash(token),hash(revocationKey));
    }
    static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 indisponível"); }
    }
    @Transactional
    public void enqueue(PushRequested event) {
        jdbc.update("""
            INSERT INTO push_deliveries(id,device_id,binding,kind,reference_id)
            SELECT gen_random_uuid(),d.id,d.binding,?,? FROM push_devices d JOIN users u ON u.id=d.user_id
            WHERE u.ativo=TRUE AND u.excluido_em IS NULL AND d.updated_at > CURRENT_TIMESTAMP - INTERVAL '1 day'
            AND d.user_id IN (SELECT cliente_id FROM service_requests WHERE id=?
                UNION SELECT prestador_id FROM proposals WHERE service_request_id=? AND status='ACEITA')
            AND (?::uuid IS NULL OR d.user_id<>?::uuid)
            ORDER BY d.id FOR KEY SHARE OF d
            ON CONFLICT(device_id,binding,kind,reference_id) DO NOTHING
            """, event.kind(), event.referenceId(), event.requestId(), event.requestId(), event.excludeUserId(), event.excludeUserId());
    }
    record Delivery(UUID id, UUID deviceId, UUID binding, String kind, UUID reference, UUID claim, int attempts) {}
    Delivery claim() {
        return jdbc.query("""
            UPDATE push_deliveries SET claim_token=?, attempts=attempts+1, available_at=CURRENT_TIMESTAMP+INTERVAL '5 minutes'
            WHERE id=(SELECT id FROM push_deliveries WHERE status='PENDING' AND available_at<=CURRENT_TIMESTAMP
                ORDER BY available_at FOR UPDATE SKIP LOCKED LIMIT 1)
            RETURNING id,device_id,binding,kind,reference_id,claim_token,attempts
            """, (rs,n) -> new Delivery(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),
                    rs.getString(4),rs.getObject(5,UUID.class),rs.getObject(6,UUID.class),rs.getInt(7)),UUID.randomUUID())
                .stream().findFirst().orElse(null);
    }
    String token(Delivery d) {
        var encrypted = jdbc.query("""
            SELECT d.token_cipher FROM push_devices d JOIN users u ON u.id=d.user_id
            JOIN push_deliveries p ON p.device_id=d.id
            WHERE p.id=? AND p.claim_token=? AND d.binding=? AND u.ativo=TRUE AND u.excluido_em IS NULL
            AND p.created_at > CURRENT_TIMESTAMP-INTERVAL '30 minutes'
            AND d.updated_at > CURRENT_TIMESTAMP-INTERVAL '1 day'
            """,(rs,n)->rs.getString(1),d.id(),d.claim(),d.binding());
        return encrypted.isEmpty() ? null : cipher.decrypt(encrypted.getFirst());
    }
    void finish(Delivery d, PushGateway.Result result, long retryAfter) {
        if (result == PushGateway.Result.UNREGISTERED)
            jdbc.update("DELETE FROM push_devices WHERE id=? AND binding=? AND EXISTS(SELECT 1 FROM push_deliveries WHERE id=? AND claim_token=?)",d.deviceId(),d.binding(),d.id(),d.claim());
        String state = result == PushGateway.Result.SENT ? "SENT" : result == PushGateway.Result.RETRY && d.attempts()<5 ? "PENDING" : "FAILED";
        jdbc.update("""
            UPDATE push_deliveries SET status=?,claim_token=NULL,
            available_at=CURRENT_TIMESTAMP+(? * INTERVAL '1 second')
            WHERE id=? AND claim_token=?
            """,state,Math.max(retryAfter,(long)60 * (1L << Math.min(d.attempts()-1,4))),d.id(),d.claim());
    }
    boolean hasAdminDevice() {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM push_devices d JOIN users u ON u.id=d.user_id
            WHERE u.role='ROLE_ADMIN' AND u.ativo=TRUE AND u.excluido_em IS NULL
                AND d.updated_at>CURRENT_TIMESTAMP-INTERVAL '1 day')
            """,Boolean.class));
    }
    List<UUID> adminDevices() {
        return jdbc.query("""
            SELECT d.id FROM push_devices d JOIN users u ON u.id=d.user_id
            WHERE u.role='ROLE_ADMIN' AND u.ativo=TRUE AND u.excluido_em IS NULL
                AND d.updated_at>CURRENT_TIMESTAMP-INTERVAL '1 day'
            """,(rs,n)->rs.getObject(1,UUID.class));
    }
    String adminToken(UUID id) {
        var tokens=jdbc.query("""
            SELECT d.token_cipher FROM push_devices d JOIN users u ON u.id=d.user_id
            WHERE d.id=? AND u.role='ROLE_ADMIN' AND u.ativo=TRUE AND u.excluido_em IS NULL
            """,(rs,n)->rs.getString(1),id);
        return tokens.isEmpty()?null:cipher.decrypt(tokens.getFirst());
    }
    void cleanup() {
        jdbc.update("DELETE FROM push_registration_fences WHERE created_at<CURRENT_TIMESTAMP-INTERVAL '2 days'");
        jdbc.update("DELETE FROM push_deliveries WHERE created_at<CURRENT_TIMESTAMP-INTERVAL '7 days'");
        jdbc.update("DELETE FROM push_devices WHERE updated_at<CURRENT_TIMESTAMP-INTERVAL '1 day'");
    }
}
