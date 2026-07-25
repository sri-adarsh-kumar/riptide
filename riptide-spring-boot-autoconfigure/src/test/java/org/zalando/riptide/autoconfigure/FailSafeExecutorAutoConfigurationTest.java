package org.zalando.riptide.autoconfigure;


import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.zalando.riptide.Http;
import org.zalando.riptide.Plugin;
import org.zalando.riptide.RequestExecution;
import org.zalando.logbook.autoconfigure.LogbookAutoConfiguration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.zalando.riptide.PassRoute.pass;

@RiptideClientTest
@ActiveProfiles("default")
@Slf4j
public class FailSafeExecutorAutoConfigurationTest {

    @Configuration
    @ImportAutoConfiguration({
            JacksonAutoConfiguration.class,
            LogbookAutoConfiguration.class,
            OpenTracingTestAutoConfiguration.class,
            MetricsTestAutoConfiguration.class,
    })
    static class ContextConfiguration {

        @Bean("customExecutorTestPlugin")
        Plugin customExecutorTestPlugin(final AtomicReference<String> terminalThread) {
            return new Plugin() {
                @Override
                public RequestExecution aroundNetwork(final RequestExecution execution) {
                    return arguments -> {
                        terminalThread.set(Thread.currentThread().getName());
                        return execution.execute(arguments);
                    };
                }
            };
        }

        @Bean
        AtomicReference<String> terminalThread() {
            return new AtomicReference<>();
        }
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    @Qualifier("custom-executor-test")
    private Http customExecutorTest;

    @Autowired
    private MockRestServiceServer server;

    @Autowired
    private AtomicReference<String> terminalThread;

    @Test
    public void shouldUseOneConfiguredFailsafeExecutor() {
        final var executor = applicationContext.getBean("customExecutorTestFailsafeExecutorService", ExecutorService.class);
        assertThat(executor)
                .isNotNull()
                .hasFieldOrPropertyWithValue("corePoolSize",2)
                .hasFieldOrPropertyWithValue("maximumPoolSize", 13);
        assertThat(applicationContext.containsBean("customExecutorTestRetryPolicyExecutorService")).isFalse();
        assertThat(applicationContext.containsBean("customExecutorTestCircuitBreakerExecutorService")).isFalse();
        assertThat(applicationContext.containsBean("customExecutorTestBackupRequestExecutorService")).isFalse();
        assertThat(applicationContext.containsBean("customExecutorTestTimeoutExecutorService")).isFalse();

        server.expect(requestTo("http://retry-test/"))
                .andRespond(withSuccess());
        try (ClientHttpResponse ignored = customExecutorTest.get("/").call(pass()).join()) {
            // The terminal plugin runs while the Failsafe chain executes the request.
        }

        assertThat(terminalThread.get())
                .startsWith("http-custom-executor-test-");
        server.verify();
    }

}
