/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.statsd;

import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import org.reactivestreams.Subscriber;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

public class StatsdFunctionTimer<T> extends CumulativeFunctionTimer<T> implements StatsdPollable {
    private final StatsdLineBuilder lineBuilder;
    private final Subscriber<String> publisher;
    private final AtomicReference<Long> lastCount = new AtomicReference<>(0L);
    private final AtomicReference<Double> lastTime = new AtomicReference<>(0.0);

    StatsdFunctionTimer(Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction,
                        TimeUnit totalTimeFunctionUnits, TimeUnit baseTimeUnit,
                        StatsdLineBuilder lineBuilder, Subscriber<String> publisher) {
        super(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnits, baseTimeUnit);
        this.lineBuilder = lineBuilder;
        this.publisher = publisher;
    }

    @Override
    public void poll() {
        lastCount.updateAndGet(prevCount -> {
            long count = (long) count();
            long newTimingsCount = count - prevCount;

            if(newTimingsCount > 0) {
                lastTime.updateAndGet(prevTime -> {
                    double totalTime = totalTime(TimeUnit.MILLISECONDS);
                    double newTimingsSum = totalTime - prevTime;

                    // We can't know what the individual timing samples were, so we approximate each one
                    // by calculating the average of the sum of all new timings seen by the number of new timing
                    // occurrences.
                    double timingAverage = newTimingsSum / newTimingsCount;
                    for (int i = 0; i < newTimingsCount; i++) {
                        publisher.onNext(lineBuilder.timing(timingAverage));
                    }

                    return totalTime;
                });
            }
            return count;
        });
    }
}
