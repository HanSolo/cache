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

import eu.hansolo.cache.Cache.CacheBuilder;

import java.time.Instant;
import java.util.concurrent.TimeUnit;


public class Demo {

    public Demo() {
        final long             millis  = 1000;
        Cache<String, Integer> cache   = new Cache<>(millis, TimeUnit.MILLISECONDS);
        CacheBuilder           builder = Cache.builder(cache);
        builder.build();

        String name1 = "Han Solo";

        cache.put(name1, 10);

        Long startEpochMillis = Instant.now().toEpochMilli();
        System.out.println(name1 + " is cached: " + cache.isCached(name1) + " (" + (Instant.now().toEpochMilli() - startEpochMillis) + " ms)\n");

        try { Thread.sleep(millis); } catch (InterruptedException e) { e.printStackTrace(); }

        System.out.println(name1 + " is cached: " + cache.isCached(name1) + " (" + (Instant.now().toEpochMilli() - startEpochMillis) + " ms)\n");

        try { Thread.sleep(millis); } catch (InterruptedException e) { e.printStackTrace(); }

        System.out.println(name1 + " is cached: " + cache.isCached(name1) + " (" + (Instant.now().toEpochMilli() - startEpochMillis) + " ms)\n");

        try { Thread.sleep(millis); } catch (InterruptedException e) { e.printStackTrace(); }

        System.out.println(name1 + " is cached: " + cache.isCached(name1) + " (" + (Instant.now().toEpochMilli() - startEpochMillis) + " ms)\n");

        try { Thread.sleep(millis); } catch (InterruptedException e) { e.printStackTrace(); }

        System.out.println(name1 + " is cached: " + cache.isCached(name1) + " (" + (Instant.now().toEpochMilli() - startEpochMillis) + " ms)\n");
    }


    public static void main(String[] args) {
        new Demo();
    }
}
