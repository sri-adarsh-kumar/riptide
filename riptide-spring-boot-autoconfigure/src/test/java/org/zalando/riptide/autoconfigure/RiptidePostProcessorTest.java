package org.zalando.riptide.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

final class RiptidePostProcessorTest {

    @Test
    void shouldPassRawAndEffectivePropertiesToRegistrarFactory() {
        final AtomicReference<RiptideProperties> raw = new AtomicReference<>();
        final AtomicReference<RiptideProperties> effective = new AtomicReference<>();
        final RiptidePostProcessor unit = new RiptidePostProcessor((registry, rawProperties,
                effectiveProperties) -> {
            raw.set(rawProperties);
            effective.set(effectiveProperties);
            return () -> { };
        });
        final MockEnvironment environment = new MockEnvironment()
                .withProperty("riptide.defaults.failsafe.threads.enabled", "true")
                .withProperty("riptide.clients.example.retry.enabled", "true");

        unit.setEnvironment(environment);
        unit.postProcessBeanDefinitionRegistry(new DefaultListableBeanFactory());

        assertThat(raw.get().getClients().get("example").getFailsafe(), is(nullValue()));
        assertThat(effective.get().getClients().get("example").getFailsafe().getThreads().getEnabled(), is(true));
    }

}
