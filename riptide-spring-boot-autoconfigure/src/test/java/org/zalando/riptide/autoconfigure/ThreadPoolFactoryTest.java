package org.zalando.riptide.autoconfigure;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

final class ThreadPoolFactoryTest {

    @Test
    void shouldCreateFixedThreadPoolWithBoundedQueue() {
        final ThreadPoolExecutor executor = ThreadPoolFactory.create("example",
                new RiptideProperties.Threads(true, 2, 2, TimeSpan.of(1, MINUTES), 3));

        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getMaximumPoolSize()).isEqualTo(2);
        assertThat(executor.getQueue()).isInstanceOf(ArrayBlockingQueue.class);

        executor.shutdown();
    }

}
