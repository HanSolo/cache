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
    public Cache(final long timeout, final TimeUnit timeUnit) {
        this(timeout, timeUnit, Integer.MAX_VALUE);
    }
    public Cache(final int limit) {
        this(0, TimeUnit.MILLISECONDS, Integer.MAX_VALUE);
    }
    public Cache(final long timeout, final TimeUnit timeUnit, final int limit) {
        this();

        if (timeout < 0) { throw new IllegalArgumentException("\"Timeout cannot be negative\""); }
        if (timeout > 0 && null == timeUnit) { throw new IllegalArgumentException("TimeUnit cannot be null if timeout is > 0"); }
        if (limit < 1) { throw new IllegalArgumentException("Limit cannot be smaller than 1"); }

        this.timeout  = timeout;
        this.timeUnit = timeUnit;
        chronoUnit    = convertToChronoUnit(this.timeUnit);
        this.limit    = limit;
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


    // ******************** Public Methods ************************************
    public static CacheBuilder builder() {
        return builder(new Cache<>());
    }
    public static CacheBuilder builder(final Cache cache) {
        return new CacheBuilder(cache);
    }


    public boolean isCached(final K key) {
        return get(key).isPresent();
    }

    public long getLimit() { return limit; }

    public long getTimeout() { return timeout; }

    public TimeUnit getTimeUnit() { return timeUnit; }

    public int getSize() { return cache.size(); }

    public V getIfPresent(final K key) { return cache.getOrDefault(key, null); }

    public Optional<V> get(final K key) { return Optional.ofNullable(getIfPresent(key)); }
    public void put(final K key, final V value) {
        cache.putIfAbsent(key, value);
        timeoutMap.putIfAbsent(key, Instant.now());
    }
    public void remove(final K key) {
        cache.remove(key);
        timeoutMap.remove(key);
    }


    // ******************** Private Methods ***********************************
    private ChronoUnit convertToChronoUnit(final TimeUnit timeUnit) {
        switch(timeUnit) {
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

    private void removeEntries(final List<K> toBeRemoved) {
        toBeRemoved.forEach(key -> {
            timeoutMap.remove(key);
            cache.remove(key);
        });
    }

    private static int clamp(final int min, final int max, final int value) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
    private static long clamp(final long min, final long max, final long value) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }


    // ******************** Inner Classes *************************************
    public static class CacheBuilder {
        private final Cache cache;


        // ******************** Constructors **********************************
        private CacheBuilder(final Cache cache) {
            this.cache = cache;
        }



        // ******************** Public Methods ********************************
        public CacheBuilder withLimit(final int limit) {
            if (limit < 1) { throw new IllegalArgumentException("\"Limit cannot be smaller than 1\""); }
            cache.limit = limit;
            cache.checkSize();
            return this;
        }

        public CacheBuilder withTimeout(final long timeout, final TimeUnit timeUnit) {
            if (timeout < 0) { throw new IllegalArgumentException("Timeout cannot be negative"); }
            if (null == timeUnit) { throw new IllegalArgumentException("TimeUnit cannot be null"); }
            cache.timeout    = clamp(0, Long.MAX_VALUE, timeout);
            cache.timeUnit   = timeUnit;
            cache.chronoUnit = cache.convertToChronoUnit(timeUnit);
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