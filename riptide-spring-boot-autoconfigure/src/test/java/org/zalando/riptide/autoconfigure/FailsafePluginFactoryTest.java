package org.zalando.riptide.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.zalando.riptide.RequestArguments;
import org.zalando.riptide.failsafe.FailsafePlugin;
import org.zalando.riptide.failsafe.RetryException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class FailsafePluginFactoryTest {

    @Test
    void shouldRetrySocketFaultForIdempotentRequest() {
        assertRetries(HttpMethod.GET, new SocketTimeoutException(), 2);
    }

    @Test
    void shouldNotRetrySocketFaultForNonIdempotentRequest() {
        assertRetries(HttpMethod.POST, new SocketTimeoutException(), 1);
    }

    @Test
    void shouldRetryConnectionFault() {
        assertRetries(HttpMethod.POST, new UnknownHostException(), 2);
    }

    @Test
    void shouldRetryRetryException() {
        assertRetries(HttpMethod.GET, retryException(), 2);
    }

    private static void assertRetries(final HttpMethod method, final Throwable failure, final int expectedAttempts) {
        final AtomicInteger attempts = new AtomicInteger();
        final FailsafePlugin plugin = FailsafePluginFactory.create(client(), null, emptyList(), null);
        final RequestArguments arguments = RequestArguments.create().withMethod(method);

        assertThrows(CompletionException.class, () -> plugin.aroundAsync(ignored -> {
            attempts.incrementAndGet();
            return CompletableFuture.<ClientHttpResponse>failedFuture(failure);
        }).execute(arguments).join());

        assertEquals(expectedAttempts, attempts.get());
    }

    private static RiptideProperties.Client client() {
        final RiptideProperties properties = new RiptideProperties();
        final RiptideProperties.Client client = new RiptideProperties.Client();
        final RiptideProperties.Retry retry = new RiptideProperties.Retry();
        retry.setEnabled(true);
        retry.setMaxRetries(1);
        client.setRetry(retry);
        client.setTransientFaultDetection(new RiptideProperties.TransientFaultDetection(true));
        properties.getClients().put("example", client);
        return Defaulting.withDefaults(properties).getClients().get("example");
    }

    private static RetryException retryException() {
        try {
            final ClientHttpResponse response = mock(ClientHttpResponse.class);
            when(response.getStatusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE);
            when(response.getHeaders()).thenReturn(new HttpHeaders());
            return new RetryException(response);
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
    }

}
