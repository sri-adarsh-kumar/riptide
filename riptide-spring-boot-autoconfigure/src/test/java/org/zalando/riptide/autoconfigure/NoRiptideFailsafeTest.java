package org.zalando.riptide.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.util.ReflectionUtils.getUniqueDeclaredMethods;

final class NoRiptideFailsafeTest {
    @Test
    void shouldReadMicrometerPluginDefinitionWithoutFailsafePlugin() {
        assertNotNull(getUniqueDeclaredMethods(MicrometerPluginFactory.class));
    }

    @Test
    void shouldReadDefaultRiptideRegistrarDefinitionWithoutFailsafePlugin() {
        assertNotNull(getUniqueDeclaredMethods(DefaultRiptideRegistrar.class));
    }

    @Test
    void shouldRejectLegacyConfigurationWithoutFailsafe() {
        final RiptideProperties properties = new RiptideProperties();
        final RiptideProperties.Client client = new RiptideProperties.Client();
        final RiptideProperties.Retry retry = new RiptideProperties.Retry();
        final RiptideProperties.Threads threads = new RiptideProperties.Threads();
        threads.setEnabled(true);
        retry.setEnabled(true);
        retry.setThreads(threads);
        client.setRetry(retry);
        properties.getClients().put("example", client);

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new DefaultRiptideRegistrar(new Registry(new DefaultListableBeanFactory()),
                        Defaulting.withDefaults(properties)).register());

        assertTrue(exception.getMessage().contains("Policy-specific Failsafe executor configuration is no longer supported"));
        assertTrue(exception.getMessage().contains("failsafe.threads"));
    }
}
