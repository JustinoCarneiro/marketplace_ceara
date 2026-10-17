package com.onda.marketplace.architecture;

import jakarta.persistence.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.Arrays;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Fronteiras escolhidas do monólito: entidades guardam IDs, FKs continuam no PostgreSQL. */
class DomainReferencesTest {
    @ParameterizedTest
    @CsvSource({
        "com.onda.marketplace.proposal.Proposal,serviceRequestId,service_request_id",
        "com.onda.marketplace.message.Message,serviceRequestId,service_request_id",
        "com.onda.marketplace.servicerequest.ServiceRequest,clienteId,cliente_id",
        "com.onda.marketplace.provider.ProviderProfile,userId,user_id"
    })
    void fronteiraEntreDominiosUsaUuid(String name,String fieldName,String columnName) throws Exception {
        var entity=Class.forName(name);
        assertThat(Arrays.stream(entity.getDeclaredFields())
                .filter(f->f.getType().isAnnotationPresent(Entity.class))
                .filter(f->!f.getType().getPackageName().equals(entity.getPackageName())).map(f->f.getName()).toList())
                .as("associações entre domínios de %s",name).isEmpty();
        var field=entity.getDeclaredField(fieldName);
        assertThat(field.getType()).isEqualTo(UUID.class);
        assertThat(field.getAnnotation(Column.class).name()).isEqualTo(columnName);
        assertThat(field.getAnnotation(Column.class).nullable()).isFalse();
    }
}
