package org.zalando.riptide.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.zalando.riptide.failsafe.FailsafePlugin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.beans.factory.support.BeanDefinitionBuilder.genericBeanDefinition;
import static org.zalando.riptide.autoconfigure.RiptideProperties.BackupRequest;
import static org.zalando.riptide.autoconfigure.RiptideProperties.Client;
import static org.zalando.riptide.autoconfigure.RiptideProperties.Failsafe;
import static org.zalando.riptide.autoconfigure.RiptideProperties.Retry;
import static org.zalando.riptide.autoconfigure.RiptideProperties.Threads;
import static org.zalando.riptide.autoconfigure.RiptideProperties.Timeouts;

final class DefaultRiptideRegistrarTest {

    @Test
    void shouldRegisterSharedFailsafeExecutor() {
        final RiptideProperties properties = clientWithRetry();
        properties.getClients().get("example").setFailsafe(failsafe(threads()));

        final DefaultListableBeanFactory registry = register(properties);

        assertFalse(registry.containsBeanDefinition("exampleRetryPolicyExecutorService"));
        assertFalse(registry.containsBeanDefinition("exampleTimeoutExecutorService"));
        assertTrue(registry.containsBeanDefinition("exampleFailsafeExecutorService"));
    }

    @Test
    void shouldUseSingleEnabledLegacyExecutor() {
        final RiptideProperties properties = clientWithRetry();
        properties.getClients().get("example").getRetry().setThreads(threads());

        final DefaultListableBeanFactory registry = register(properties);

        assertTrue(registry.containsBeanDefinition("exampleFailsafeExecutorService"));
    }

    @Test
    void shouldRegisterFailsafePluginForBackupRequest() {
        final RiptideProperties properties = new RiptideProperties();
        final Client client = new Client();
        client.setBackupRequest(backupRequest());
        properties.getClients().put("example", client);

        final DefaultListableBeanFactory registry = register(properties);

        assertTrue(registry.containsBeanDefinition("exampleFailsafePlugin"));
    }

    @Test
    void shouldRejectMultipleEnabledLegacyExecutors() {
        final RiptideProperties properties = clientWithRetry();
        properties.getClients().get("example").getRetry().setThreads(threads());
        properties.getClients().get("example").setTimeouts(timeouts(threads()));

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> register(properties));

        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.Matchers.containsString(
                "Multiple Failsafe executors configured"));
    }

    @Test
    void shouldRejectSharedAndLegacyExecutors() {
        final RiptideProperties properties = clientWithRetry();
        properties.getClients().get("example").setFailsafe(failsafe(threads()));
        properties.getClients().get("example").getRetry().setThreads(threads());

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> register(properties));

        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.Matchers.containsString(
                "Configure only riptide.*.failsafe.threads"));
    }

    @Test
    void shouldIgnoreDisabledExecutorSettings() {
        final RiptideProperties properties = clientWithRetry();
        properties.getClients().get("example").setFailsafe(failsafe(disabledThreads()));
        properties.getClients().get("example").getRetry().setThreads(disabledThreads());

        final DefaultListableBeanFactory registry = register(properties);

        assertFalse(registry.containsBeanDefinition("exampleFailsafeExecutorService"));
    }

    @Test
    void shouldUseInheritedLegacyExecutor() {
        final RiptideProperties properties = new RiptideProperties();
        properties.getDefaults().getRetry().setThreads(threads());
        properties.getClients().put("example", new Client());
        properties.getClients().get("example").setRetry(retry());

        final DefaultListableBeanFactory registry = register(properties);

        assertTrue(registry.containsBeanDefinition("exampleFailsafeExecutorService"));
    }

    @Test
    void shouldNotResolveFailsafeDependenciesForCustomPlugin() {
        final RiptideProperties properties = clientWithRetry();
        properties.getClients().get("example").setFailsafe(failsafe(threads()));
        properties.getClients().get("example").getRetry().setThreads(threads());
        final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("exampleFailsafePlugin",
                genericBeanDefinition(FailsafePlugin.class).getBeanDefinition());

        assertDoesNotThrow(() -> register(properties, beanFactory));

        assertFalse(beanFactory.containsBeanDefinition("exampleFailsafeExecutorService"));
    }

    private static RiptideProperties clientWithRetry() {
        final RiptideProperties properties = new RiptideProperties();
        final Client client = new Client();
        client.setRetry(retry());
        properties.getClients().put("example", client);
        return properties;
    }

    private static DefaultListableBeanFactory register(final RiptideProperties rawProperties) {
        final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        register(rawProperties, beanFactory);
        return beanFactory;
    }

    private static void register(final RiptideProperties rawProperties, final DefaultListableBeanFactory beanFactory) {
        new DefaultRiptideRegistrar(new Registry(beanFactory), rawProperties,
                Defaulting.withDefaults(rawProperties)).register();
    }

    private static Threads threads() {
        final Threads threads = new Threads();
        threads.setEnabled(true);
        threads.setMinSize(2);
        threads.setMaxSize(4);
        threads.setQueueSize(0);
        return threads;
    }

    private static Threads disabledThreads() {
        final Threads threads = new Threads();
        threads.setEnabled(false);
        return threads;
    }

    private static Failsafe failsafe(final Threads threads) {
        final Failsafe failsafe = new Failsafe();
        failsafe.setThreads(threads);
        return failsafe;
    }

    private static Retry retry() {
        final Retry retry = new Retry();
        retry.setEnabled(true);
        return retry;
    }

    private static BackupRequest backupRequest() {
        final BackupRequest backupRequest = new BackupRequest();
        backupRequest.setEnabled(true);
        return backupRequest;
    }

    private static Timeouts timeouts(final Threads threads) {
        final Timeouts timeouts = new Timeouts();
        timeouts.setEnabled(true);
        timeouts.setGlobal(TimeSpan.of(1, java.util.concurrent.TimeUnit.SECONDS));
        timeouts.setThreads(threads);
        return timeouts;
    }

}
