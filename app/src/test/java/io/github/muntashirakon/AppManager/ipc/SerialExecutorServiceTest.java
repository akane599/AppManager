// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.ipc;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class SerialExecutorServiceTest {
    @Test
    public void throwingTaskDoesNotStrandQueuedWork() {
        ArrayDeque<Runnable> workers = new ArrayDeque<>();
        SerialExecutorService executor = new SerialExecutorService(workers::add);
        List<Integer> completed = new ArrayList<>();
        executor.execute(() -> { throw new IllegalStateException("task failure"); });
        executor.execute(() -> completed.add(2));
        assertThrows(IllegalStateException.class, () -> workers.remove().run());
        workers.remove().run();
        assertEquals(Arrays.asList(2), completed);
    }

    @Test
    public void orderlyShutdownDrainsTasksInOrderAndRejectsNewWork() throws Exception {
        ArrayDeque<Runnable> workers = new ArrayDeque<>();
        SerialExecutorService executor = new SerialExecutorService(workers::add);
        List<Integer> completed = new ArrayList<>();
        executor.execute(() -> completed.add(1));
        executor.execute(() -> completed.add(2));
        executor.shutdown();
        assertFalse(executor.isTerminated());
        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {}));
        workers.remove().run();
        assertEquals(Arrays.asList(1, 2), completed);
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
    }

    @Test
    public void awaitingTerminationAllowsWorkerToFinish() throws Exception {
        ExecutorService backing = Executors.newSingleThreadExecutor();
        SerialExecutorService executor = new SerialExecutorService(backing);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                try { release.await(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            executor.execute(() -> {});
            executor.shutdown();
            release.countDown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
            backing.shutdownNow();
        }
    }

    @Test
    public void idleExecutorIsNotTerminatedUntilShutdown() throws Exception {
        SerialExecutorService executor = new SerialExecutorService(Runnable::run);
        assertFalse(executor.awaitTermination(0, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(executor.isTerminated());
    }

    @Test
    public void shutdownNowReturnsUnstartedWorkWithoutRunningIt() throws Exception {
        ArrayDeque<Runnable> workers = new ArrayDeque<>();
        SerialExecutorService executor = new SerialExecutorService(workers::add);
        Runnable task = () -> fail("Cancelled task ran");
        executor.execute(task);
        assertEquals(Arrays.asList(task), executor.shutdownNow());
        workers.remove().run();
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
    }
}
