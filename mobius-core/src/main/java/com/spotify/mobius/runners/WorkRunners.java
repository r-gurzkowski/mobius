/*
 * -\-\-
 * Mobius
 * --
 * Copyright (c) 2017-2020 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */
package com.spotify.mobius.runners;

import static com.spotify.mobius.internal_util.Preconditions.checkNotNull;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;

/**
 * Interface for posting runnables to be executed on a thread. The runnables must all be executed on
 * the same thread for a given WorkRunner.
 */
public class WorkRunners {
  private WorkRunners() {}

  @Nonnull
  public static WorkRunner immediate() {
    return new ImmediateWorkRunner();
  }

  @Nonnull
  public static WorkRunner singleThread() {
    return from(Executors.newSingleThreadExecutor(THREAD_FACTORY));
  }

  @Nonnull
  public static WorkRunner fixedThreadPool(int n) {
    return from(Executors.newFixedThreadPool(n, THREAD_FACTORY));
  }

  @Nonnull
  public static WorkRunner cachedThreadPool() {
    return from(Executors.newCachedThreadPool(THREAD_FACTORY));
  }

  @Nonnull
  public static WorkRunner from(ExecutorService service) {
    return new ExecutorServiceWorkRunner(checkNotNull(service));
  }

  private static final MyThreadFactory THREAD_FACTORY = new MyThreadFactory();

  private static class MyThreadFactory implements ThreadFactory {

    private static final AtomicLong threadCount = new AtomicLong(0);

    @Override
    public Thread newThread(Runnable runnable) {
      Thread thread = Executors.defaultThreadFactory().newThread(checkNotNull(runnable));

      thread.setName(
          String.format(Locale.ENGLISH, "mobius-thread-%d", threadCount.incrementAndGet()));

      return thread;
    }
  }
}
