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
package com.spotify.mobius.rx2;

import static com.spotify.mobius.internal_util.Preconditions.checkNotNull;

import com.spotify.mobius.Connectable;
import com.spotify.mobius.Connection;
import com.spotify.mobius.functions.Consumer;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.ObservableTransformer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Cancellable;
import io.reactivex.subjects.PublishSubject;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;

/**
 * Contains utility methods for converting back and forth between {@link ObservableTransformer}s and
 * {@link Connectable}s.
 */
public final class RxConnectables {
  private RxConnectables() {}

  public static <I, O> Connectable<I, O> fromTransformer(
      final ObservableTransformer<I, O> transformer) {
    checkNotNull(transformer);

    Connectable<I, O> actualConnectable =
        new Connectable<I, O>() {
          @Nonnull
          @Override
          public Connection<I> connect(final Consumer<O> output) {
            final PublishSubject<I> subject = PublishSubject.create();
            final AtomicBoolean disposed = new AtomicBoolean();

            final Disposable disposable =
                subject
                    .compose(transformer)
                    .subscribe(
                        new io.reactivex.functions.Consumer<O>() {
                          @Override
                          public void accept(O e) {
                            synchronized (disposed) {
                              if (!disposed.get()) {
                                output.accept(e);
                              }
                            }
                          }
                        });

            return new Connection<I>() {
              @Override
              public void accept(I effect) {
                subject.onNext(effect);
              }

              @Override
              public void dispose() {
                synchronized (disposed) {
                  disposed.set(true);
                }
                disposable.dispose();
              }
            };
          }
        };

    return new DiscardAfterDisposeConnectable<>(actualConnectable);
  }

  public static <I, O> ObservableTransformer<I, O> toTransformer(
      final Connectable<I, O> connectable) {
    return new ObservableTransformer<I, O>() {
      @Override
      public ObservableSource<O> apply(final Observable<I> upstream) {
        return Observable.create(
            new ObservableOnSubscribe<O>() {
              @Override
              public void subscribe(final ObservableEmitter<O> emitter) throws Exception {
                Consumer<O> output =
                    new Consumer<O>() {
                      @Override
                      public void accept(O value) {
                        emitter.onNext(value);
                      }
                    };
                final AtomicBoolean disposed = new AtomicBoolean();
                final Connection<I> input =
                    connectable.connect(
                        i -> {
                          synchronized (disposed) {
                            if (!disposed.get()) {
                              output.accept(i);
                            }
                          }
                        });
                final Disposable disposable =
                    upstream.subscribe(
                        new io.reactivex.functions.Consumer<I>() {
                          @Override
                          public void accept(I f) {
                            input.accept(f);
                          }
                        },
                        new io.reactivex.functions.Consumer<Throwable>() {
                          @Override
                          public void accept(Throwable throwable) throws Exception {
                            emitter.onError(throwable);
                          }
                        },
                        new Action() {
                          @Override
                          public void run() {
                            emitter.onComplete();
                          }
                        });

                emitter.setCancellable(
                    new Cancellable() {
                      @Override
                      public void cancel() throws Exception {
                        synchronized (disposed) {
                          disposed.set(true);
                        }
                        disposable.dispose();
                        input.dispose();
                      }
                    });
              }
            });
      }
    };
  }
}
