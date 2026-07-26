package org.zalando.riptide.autoconfigure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.zalando.riptide.Http;
import org.zalando.riptide.failsafe.FailsafePlugin;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.beans.factory.support.BeanDefinitionBuilder.genericBeanDefinition;
import static org.zalando.riptide.autoconfigure.RiptideProperties.BackupRequest;
import static org.zalando.riptide.autoconfigure.RiptideProperties.CircuitBreaker;
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
    void shouldRegisterFailsafePluginForBackupRequest() {
        final RiptideProperties properties = new RiptideProperties();
        final Client client = new Client();
        client.setBackupRequest(backupRequest());
        properties.getClients().put("example", client);

        final DefaultListableBeanFactory registry = register(properties);

        assertTrue(registry.containsBeanDefinition("exampleFailsafePlugin"));
    }

    @ParameterizedTest
    @MethodSource("legacySettings")
    void shouldRejectLegacyFailsafeExecutors(final String ignored, final Consumer<Client> configure) {
        final RiptideProperties properties = new RiptideProperties();
        final Client client = new Client();
        configure.accept(client);
        properties.getClients().put("example", client);

        assertMigrationError(properties);
    }

    @Test
    void shouldRejectSeveralLegacyFailsafeExecutors() {
        final RiptideProperties properties = clientWithRetry();
        final Client client = properties.getClients().get("example");
        client.getRetry().setThreads(threads());
        client.setTimeouts(timeouts(threads()));

        assertMigrationError(properties);
    }

    @Test
    void shouldRejectInheritedLegacyFailsafeExecutor() {
        final RiptideProperties properties = new RiptideProperties();
        properties.getDefaults().getRetry().setThreads(threads());
        final Client client = new Client();
        client.setRetry(retry());
        properties.getClients().put("example", client);

        assertMigrationError(properties);
    }

    @Test
    void shouldRejectSharedAndLegacyFailsafeExecutors() {
        final RiptideProperties properties = clientWithRetry();
        final Client client = properties.getClients().get("example");
        client.setFailsafe(failsafe(threads()));
        client.getRetry().setThreads(threads());

        assertMigrationError(properties);
    }

    @Test
    void shouldRejectLegacyFailsafeExecutorWithCustomPlugin() {
        final RiptideProperties properties = clientWithRetry();
        properties.getClients().get("example").getRetry().setThreads(threads());
        final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("exampleFailsafePlugin",
                genericBeanDefinition(FailsafePlugin.class).getBeanDefinition());

        assertMigrationError(properties, beanFactory);
    }

    @Test
    void shouldRejectLegacyFailsafeExecutorWithCustomHttp() {
        final RiptideProperties properties = clientWithRetry();
        properties.getClients().get("example").getRetry().setThreads(threads());
        final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("exampleHttp", genericBeanDefinition(Http.class).getBeanDefinition());

        assertMigrationError(properties, beanFactory);
    }

    @ParameterizedTest
    @MethodSource("legacySettings")
    void shouldIgnoreLegacyExecutorForDisabledPolicy(final String ignored, final Consumer<Client> configure) {
        final RiptideProperties properties = new RiptideProperties();
        final Client client = new Client();
        configure.accept(client);
        disablePolicy(client, ignored);
        properties.getClients().put("example", client);

        final DefaultListableBeanFactory beanFactory = assertDoesNotThrow(() -> register(properties));

        assertFalse(beanFactory.containsBeanDefinition("exampleFailsafeExecutorService"));
    }

    @ParameterizedTest
    @MethodSource("legacySettings")
    void shouldIgnoreDisabledLegacyExecutor(final String ignored, final Consumer<Client> configure) {
        final RiptideProperties properties = new RiptideProperties();
        final Client client = new Client();
        configure.accept(client);
        disableThreads(client, ignored);
        properties.getClients().put("example", client);

        final DefaultListableBeanFactory beanFactory = assertDoesNotThrow(() -> register(properties));

        assertFalse(beanFactory.containsBeanDefinition("exampleFailsafeExecutorService"));
    }

    @Test
    void shouldNotRegisterFailsafeDependenciesWithoutPolicies() {
        final RiptideProperties properties = new RiptideProperties();
        final Client client = new Client();
        client.setFailsafe(failsafe(threads()));
        properties.getClients().put("example", client);

        final DefaultListableBeanFactory beanFactory = register(properties);

        assertFalse(beanFactory.containsBeanDefinition("exampleFailsafePlugin"));
        assertFalse(beanFactory.containsBeanDefinition("exampleFailsafeExecutorService"));
    }

    @Test
    void shouldLetCustomPluginSuppressExecutorForValidConfiguration() {
        final RiptideProperties properties = clientWithRetry();
        properties.getClients().get("example").setFailsafe(failsafe(threads()));
        final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("exampleFailsafePlugin",
                genericBeanDefinition(FailsafePlugin.class).getBeanDefinition());

        register(properties, beanFactory);

        assertFalse(beanFactory.containsBeanDefinition("exampleFailsafeExecutorService"));
    }

    @Test
    void shouldLetCustomHttpSuppressFailsafeDependenciesForValidConfiguration() {
        final RiptideProperties properties = clientWithRetry();
        properties.getClients().get("example").setFailsafe(failsafe(threads()));
        final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("exampleHttp", genericBeanDefinition(Http.class).getBeanDefinition());

        register(properties, beanFactory);

        assertFalse(beanFactory.containsBeanDefinition("exampleFailsafePlugin"));
        assertFalse(beanFactory.containsBeanDefinition("exampleFailsafeExecutorService"));
    }

    private static Stream<Arguments> legacySettings() {
        return Stream.of(
                Arguments.of("retry", (Consumer<Client>) client -> client.setRetry(retry(threads()))),
                Arguments.of("circuit-breaker", (Consumer<Client>) client -> client.setCircuitBreaker(circuitBreaker(threads()))),
                Arguments.of("backup-request", (Consumer<Client>) client -> client.setBackupRequest(backupRequest(threads()))),
                Arguments.of("timeouts", (Consumer<Client>) client -> client.setTimeouts(timeouts(threads())))
        );
    }

    private static void assertMigrationError(final RiptideProperties properties) {
        assertMigrationError(properties, new DefaultListableBeanFactory());
    }

    private static void assertMigrationError(final RiptideProperties properties,
            final DefaultListableBeanFactory beanFactory) {
        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> register(properties, beanFactory));

        assertTrue(exception.getMessage().contains("Policy-specific Failsafe executor configuration is no longer supported"));
        assertTrue(exception.getMessage().contains("failsafe.threads"));
        assertFalse(beanFactory.containsBeanDefinition("exampleFailsafeExecutorService"));
    }

    private static RiptideProperties clientWithRetry() {
        final RiptideProperties properties = new RiptideProperties();
        final Client client = new Client();
        client.setRetry(retry());
        properties.getClients().put("example", client);
        return properties;
    }

    private static DefaultListableBeanFactory register(final RiptideProperties properties) {
        final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        register(properties, beanFactory);
        return beanFactory;
    }

    private static void register(final RiptideProperties properties, final DefaultListableBeanFactory beanFactory) {
        new DefaultRiptideRegistrar(new Registry(beanFactory), Defaulting.withDefaults(properties)).register();
    }

    private static void disablePolicy(final Client client, final String policy) {
        switch (policy) {
            case "retry" -> client.getRetry().setEnabled(false);
            case "circuit-breaker" -> client.getCircuitBreaker().setEnabled(false);
            case "backup-request" -> client.getBackupRequest().setEnabled(false);
            case "timeouts" -> client.getTimeouts().setEnabled(false);
            default -> throw new IllegalArgumentException(policy);
        }
    }

    private static void disableThreads(final Client client, final String policy) {
        switch (policy) {
            case "retry" -> client.getRetry().getThreads().setEnabled(false);
            case "circuit-breaker" -> client.getCircuitBreaker().getThreads().setEnabled(false);
            case "backup-request" -> client.getBackupRequest().getThreads().setEnabled(false);
            case "timeouts" -> client.getTimeouts().getThreads().setEnabled(false);
            default -> throw new IllegalArgumentException(policy);
        }
    }

    private static Threads threads() {
        final Threads threads = new Threads();
        threads.setEnabled(true);
        threads.setMinSize(2);
        threads.setMaxSize(4);
        threads.setQueueSize(0);
        return threads;
    }

    private static Failsafe failsafe(final Threads threads) {
        final Failsafe failsafe = new Failsafe();
        failsafe.setThreads(threads);
        return failsafe;
    }

    private static Retry retry() {
        return retry(null);
    }

    private static Retry retry(final Threads threads) {
        final Retry retry = new Retry();
        retry.setEnabled(true);
        retry.setThreads(threads);
        return retry;
    }

    private static CircuitBreaker circuitBreaker(final Threads threads) {
        final CircuitBreaker circuitBreaker = new CircuitBreaker();
        circuitBreaker.setEnabled(true);
        circuitBreaker.setThreads(threads);
        return circuitBreaker;
    }

    private static BackupRequest backupRequest() {
        return backupRequest(null);
    }

    private static BackupRequest backupRequest(final Threads threads) {
        final BackupRequest backupRequest = new BackupRequest();
        backupRequest.setEnabled(true);
        backupRequest.setThreads(threads);
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
