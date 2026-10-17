package com.onda.marketplace.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class FcmConfigTest {
    final ApplicationContextRunner context=new ApplicationContextRunner().withUserConfiguration(FcmConfig.class)
            .withBean(ObjectMapper.class,ObjectMapper::new);
    @Test void disabledDoesNotRequireFirebaseCredentials() {
        context.run(c->assertThat(c.getBean(PushGateway.class).enabled()).isFalse());
    }
    @Test void enabledRequiresExplicitProject() {
        context.withPropertyValues("notification.push.enabled=true").run(c->assertThat(c).hasFailed());
    }
}
