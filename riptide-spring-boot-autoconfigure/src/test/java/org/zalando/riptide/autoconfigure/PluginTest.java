package org.zalando.riptide.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;
import org.zalando.riptide.Http;
import org.zalando.riptide.OriginalStackTracePlugin;
import org.zalando.riptide.Plugin;
import org.zalando.riptide.failsafe.FailsafePlugin;
import org.zalando.riptide.logbook.LogbookPlugin;
import org.zalando.riptide.micrometer.MicrometerPlugin;
import org.zalando.riptide.opentelemetry.OpenTelemetryPlugin;
import org.zalando.riptide.opentracing.OpenTracingPlugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.mock;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

@SpringBootTest(classes = PluginTest.TestConfiguration.class, webEnvironment = NONE)
@TestPropertySource(properties = {
        "riptide.clients.bar.transient-fault-detection.enabled: true",
})
@Component
final class PluginTest {

    @Configuration
    @Import(DefaultTestConfiguration.class)
    public static class TestConfiguration {

        @Bean
        public Plugin githubPlugin() {
            return new CustomPlugin();
        }

    }

    static class CustomPlugin implements Plugin {

    }

    @Autowired
    @Qualifier("ecb")
    private Http ecb;

    @Autowired
    @Qualifier("foo")
    private Http foo;

    @Autowired
    @Qualifier("baz")
    private Http baz;

    @Autowired
    @Qualifier("github")
    private Http github;

    @Autowired
    @Qualifier("example")
    private Http example;

    @Autowired
    @Qualifier("custom-executor-test")
    private Http customExecutorTest;

    @Test
    void shouldUseFailsafePlugin() throws Exception {
        assertThat(getFailsafePlugins(foo), hasSize(1));
        assertThat(getPlugins(foo), contains(asList(
                instanceOf(Plugin.class), // internal plugin
                instanceOf(Plugin.class), // internal plugin
                instanceOf(Plugin.class), // internal plugin
                instanceOf(MicrometerPlugin.class),
                instanceOf(OpenTracingPlugin.class),
                instanceOf(FailsafePlugin.class))));
    }

    @Test
    void shouldUseBackupRequestPlugin() throws Exception {
        assertThat(getFailsafePlugins(baz), hasSize(1));
        assertThat(getPlugins(baz), contains(asList(
                instanceOf(Plugin.class), // internal plugin
                instanceOf(Plugin.class), // internal plugin
                instanceOf(Plugin.class), // internal plugin
                instanceOf(MicrometerPlugin.class),
                instanceOf(FailsafePlugin.class))));
    }

    @Test
    void shouldChainAllEnabledFailsafePoliciesInOnePlugin() throws Exception {
        final List<FailsafePlugin> failsafePlugins = getFailsafePlugins(customExecutorTest);
        assertThat(failsafePlugins, hasSize(1));

        final FailsafePlugin failsafePlugin = failsafePlugins.get(0);

        assertThat(getPolicies(failsafePlugin), contains(
                instanceOf(dev.failsafe.Timeout.class),
                instanceOf(org.zalando.riptide.failsafe.BackupRequest.class),
                instanceOf(dev.failsafe.RetryPolicy.class),
                instanceOf(dev.failsafe.RetryPolicy.class),
                instanceOf(dev.failsafe.RetryPolicy.class),
                instanceOf(dev.failsafe.CircuitBreaker.class)));
    }

    @Test
    void shouldUseOriginalStackTracePlugin() throws Exception {
        assertThat(getPlugins(example), contains(asList(
                instanceOf(Plugin.class), // internal plugin
                instanceOf(Plugin.class), // internal plugin
                instanceOf(Plugin.class), // internal plugin
                instanceOf(MicrometerPlugin.class),
                instanceOf(LogbookPlugin.class),
                instanceOf(OpenTracingPlugin.class),
                instanceOf(OriginalStackTracePlugin.class))));
    }

    @Test
    void shouldUseOpenTelemetryPlugin() throws Exception {
        assertThat(getPlugins(github), hasItem(instanceOf(OpenTelemetryPlugin.class)));
    }

    private List<Plugin> getPlugins(final Http http) throws Exception {
        final Field field = http.getClass().getDeclaredField("plugin");
        field.setAccessible(true);

        @SuppressWarnings("unchecked") final Plugin plugin = (Plugin) field.get(http);

        final Field plugins = plugin.getClass().getDeclaredField("plugins");
        plugins.setAccessible(true);

        @SuppressWarnings("unchecked") final List<Plugin> list = (List<Plugin>) plugins.get(plugin);

        return list;
    }

    private List<FailsafePlugin> getFailsafePlugins(final Http http) throws Exception {
        return getPlugins(http).stream()
                .filter(FailsafePlugin.class::isInstance)
                .map(FailsafePlugin.class::cast)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Object> getPolicies(final FailsafePlugin plugin) throws Exception {
        final Field field = FailsafePlugin.class.getDeclaredField("policies");
        field.setAccessible(true);

        final Iterable<org.zalando.riptide.failsafe.RequestPolicy> policies =
                (Iterable<org.zalando.riptide.failsafe.RequestPolicy>) field.get(plugin);
        final List<Object> preparedPolicies = new ArrayList<>();
        for (final org.zalando.riptide.failsafe.RequestPolicy policy : policies) {
            preparedPolicies.add(policy.prepare(mock(org.zalando.riptide.RequestArguments.class)));
        }
        return preparedPolicies;
    }

}
