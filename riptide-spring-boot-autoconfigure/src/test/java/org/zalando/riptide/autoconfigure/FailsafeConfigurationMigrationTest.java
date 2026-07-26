package org.zalando.riptide.autoconfigure;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

final class FailsafeConfigurationMigrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"retry", "circuit-breaker", "backup-request", "timeouts"})
    void shouldRejectLegacyFailsafeExecutorConfiguration(final String policy) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RiptideAutoConfiguration.class))
                .withPropertyValues(
                        "riptide.clients.example." + policy + ".enabled=true",
                        "riptide.clients.example." + policy + ".threads.enabled=true")
                .run(context -> assertThat(hasMigrationError(context.getStartupFailure())).isTrue());
    }

    private static boolean hasMigrationError(final Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null
                    && cause.getMessage().contains("Policy-specific Failsafe executor configuration is no longer supported")
                    && cause.getMessage().contains("failsafe.threads")) {
                return true;
            }
        }
        return false;
    }

}
