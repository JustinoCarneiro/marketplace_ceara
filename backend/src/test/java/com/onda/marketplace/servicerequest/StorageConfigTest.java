package com.onda.marketplace.servicerequest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class StorageConfigTest {
    final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(StorageConfig.class);

    @Test
    void missingCredentialsFailStartupOutsideDemo() {
        context.run(ctx -> assertThat(ctx).hasFailed());
    }
    @Test
    void cannotUseFakeStorageOutsideExplicitDemoProfiles() {
        context.withPropertyValues("storage.provider=stub").run(ctx -> assertThat(ctx).hasFailed());
    }
    @Test
    void demoWorksWithoutExternalAccounts() {
        context.withPropertyValues("spring.profiles.active=seed", "storage.provider=stub")
                .run(ctx -> assertThat(ctx).hasSingleBean(StorageService.class).hasNotFailed());
    }
    @Test
    void validS3ConfigurationCreatesRealAdapter() {
        configured().run(ctx -> assertThat(ctx.getBean(StorageService.class)).isInstanceOf(S3StorageService.class));
    }
    @Test
    void rejectsInsecureEndpointsAndExcessiveExpiry() {
        configured().withPropertyValues("storage.endpoint=http://example.org").run(ctx -> assertThat(ctx).hasFailed());
        configured().withPropertyValues("storage.read-url-ttl-seconds=901").run(ctx -> assertThat(ctx).hasFailed());
    }
    private ApplicationContextRunner configured() {
        return context.withPropertyValues("storage.provider=s3", "storage.bucket=private-media",
                "storage.access-key=test-access", "storage.secret-key=test-secret");
    }
}
