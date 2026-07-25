package org.zalando.riptide.autoconfigure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.zalando.logbook.autoconfigure.LogbookAutoConfiguration;

import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

@RiptideClientTest
@ActiveProfiles("legacy-failsafe")
@ExtendWith(OutputCaptureExtension.class)
final class LegacyFailSafeExecutorAutoConfigurationTest {

    @Configuration
    @ImportAutoConfiguration({
            JacksonAutoConfiguration.class,
            LogbookAutoConfiguration.class,
            OpenTracingTestAutoConfiguration.class,
            MetricsTestAutoConfiguration.class,
    })
    static class ContextConfiguration {
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldUseSingleLegacyExecutorAndWarn(final CapturedOutput output) {
        final ExecutorService executor = applicationContext.getBean("legacyFailsafeExecutorService", ExecutorService.class);

        assertThat(executor)
                .hasFieldOrPropertyWithValue("corePoolSize", 2)
                .hasFieldOrPropertyWithValue("maximumPoolSize", 4);
        assertThat(output).contains("deprecated", "riptide.clients.legacy.retry.threads", "failsafe.threads");
    }

}
