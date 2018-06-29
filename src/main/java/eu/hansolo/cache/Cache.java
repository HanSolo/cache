/*
 * Copyright (c) 2018 by Gerrit Grunwald
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.hansolo.cache;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class Cache<K,V> {
    private ScheduledExecutorService      executor;
    private ConcurrentHashMap<K, V>       cache;
    private ConcurrentHashMap<K, Instant> timeoutMap;
    private long                          timeout;
    private TimeUnit                      timeUnit;
    private ChronoUnit                    chronoUnit;
    private int                           limit;


    // ******************** Constructors **************************************
    public Cache(final long TIMEOUT, final TimeUnit TIME_UNIT) {
        this(TIMEOUT, TIME_UNIT, Integer.MAX_VALUE);
    }
    public Cache(final int LIMIT) {
        this(0, TimeUnit.MILLISECONDS, Integer.MAX_VALUE);
    }
    public Cache(final long TIMEOUT, final TimeUnit TIME_UNIT, final int LIMIT) {
        this();

        if (TIMEOUT < 0) { throw new IllegalArgumentException("\"Timeout cannot be negative\""); }
        if (TIMEOUT > 0 && null == TIME_UNIT) { throw new IllegalArgumentException("TimeUnit cannot be null if timeout is > 0"); }
        if (LIMIT < 1) { throw new IllegalArgumentException("Limit cannot be smaller than 1"); }

        timeout    = TIMEOUT;
        timeUnit   = TIME_UNIT;
        chronoUnit = convertToChronoUnit(timeUnit);
        limit      = LIMIT;
    }
    public Cache() {
        executor   = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        }); // Daemon Service
        cache      = new ConcurrentHashMap<>();
        timeoutMap = new ConcurrentHashMap<>();
    }


    // ******************** Methods *******************************************
    public long getLimit() { return limit; }

    public long getTimeout() { return timeout; }

    public TimeUnit getTimeUnit() { return timeUnit; }

    public int getSize() { return cache.size(); }

    public V getIfPresent(final K KEY) { return cache.getOrDefault(KEY, null); }

    public Optional<V> get(final K KEY) { return Optional.ofNullable(getIfPresent(KEY)); }
    public void put(final K KEY, final V VALUE) {
        cache.putIfAbsent(KEY, VALUE);
        timeoutMap.putIfAbsent(KEY, Instant.now());
    }
    public void remove(final K KEY) {
        cache.remove(KEY);
        timeoutMap.remove(KEY);
    }

    public static CacheBuilder builder(final Cache CACHE) {
        return new CacheBuilder(CACHE);
    }

    private ChronoUnit convertToChronoUnit(final TimeUnit TIME_UNIT) {
        switch(TIME_UNIT) {
            case NANOSECONDS : return ChronoUnit.NANOS;
            case MICROSECONDS: return ChronoUnit.MICROS;
            case MILLISECONDS: return ChronoUnit.MILLIS;
            case SECONDS     : return ChronoUnit.SECONDS;
            case MINUTES     : return ChronoUnit.MINUTES;
            case HOURS       : return ChronoUnit.HOURS;
            case DAYS        : return ChronoUnit.DAYS;
            default          : return ChronoUnit.MILLIS;
        }
    }

    private void checkTime() {
        Instant cutoffTime  = Instant.now().minus(timeout, chronoUnit);
        List<K> toBeRemoved = timeoutMap.entrySet()
                                        .stream()
                                        .filter(entry -> entry.getValue()
                                                              .isBefore(cutoffTime))
                                        .map(Map.Entry::getKey)
                                        .collect(Collectors.toList());
        removeEntries(toBeRemoved);
    }

    private void checkSize() {
        if (cache.size() <= limit) return;
        int     surplusEntries = cache.size() - limit;
        List<K> toBeRemoved    = timeoutMap.entrySet()
                                           .stream()
                                           .sorted(Map.Entry.<K, Instant>comparingByValue().reversed())
                                           .limit(surplusEntries)
                                           .map(Map.Entry::getKey)
                                           .collect(Collectors.toList());
        removeEntries(toBeRemoved);
    }

    private void removeEntries(final List<K> TO_BE_REMOVED) {
        TO_BE_REMOVED.forEach(key -> {
            timeoutMap.remove(key);
            cache.remove(key);
        });
    }

    private static int clamp(final int MIN, final int MAX, final int VALUE) {
        if (VALUE < MIN) return MIN;
        if (VALUE > MAX) return MAX;
        return VALUE;
    }
    private static long clamp(final long MIN, final long MAX, final long VALUE) {
        if (VALUE < MIN) return MIN;
        if (VALUE > MAX) return MAX;
        return VALUE;
    }


    // ******************** Inner Classes *************************************
    public static class CacheBuilder {
        private final Cache cache;


        private CacheBuilder(final Cache CACHE) {
            cache = CACHE;
        }


        public CacheBuilder withLimit(final int LIMIT) {
            if (LIMIT < 1) { throw new IllegalArgumentException("\"Limit cannot be smaller than 1\""); }
            cache.limit = LIMIT;
            cache.checkSize();
            return this;
        }

        public CacheBuilder withTimeout(final long TIMEOUT, final TimeUnit TIME_UNIT) {
            if (TIMEOUT < 0) { throw new IllegalArgumentException("Timeout cannot be negative"); }
            if (null == TIME_UNIT) { throw new IllegalArgumentException("TimeUnit cannot be null"); }
            cache.timeout    = clamp(0, Long.MAX_VALUE, TIMEOUT);
            cache.timeUnit   = TIME_UNIT;
            cache.chronoUnit = cache.convertToChronoUnit(TIME_UNIT);
            return this;
        }

        public Cache build() {
            if (cache.timeout > 0 && cache.timeUnit == null) { throw new IllegalArgumentException("TimeUnit cannot be null if timeout is > 0"); }
            if (cache.limit < 1) { throw new IllegalArgumentException("\"Limit cannot be smaller than 1\""); }

            if (cache.timeout != 0) {
                cache.executor.scheduleAtFixedRate(() -> cache.checkTime(), 0, cache.timeout, cache.timeUnit);
            }
            return cache;
        }
    }
}