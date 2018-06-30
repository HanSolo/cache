## Cache
A simple map based cache with timeout and limit


## Demo
```
public class Demo {
    private Cache<String, Integer> cache;

    public Demo() {
        cache = new Cache<>();
        CacheBuilder builder = Cache.builder(cache);
        builder.withLimit(10).withTimeout(1, TimeUnit.SECONDS).build();

        String name1 = "Han Solo";

        cache.put(name1, 10);

        System.out.println(name1 + " is cached: " + isCached(name1));
        
        // Wait 1 second
        try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }

        System.out.println(name1 + " is cached: " + isCached(name1));

        // Wait another second
        try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }

        System.out.println(name1 + " is cached: " + isCached(name1));
    }

    private boolean isCached(final String KEY) { return cache.get(KEY).isPresent(); }

    public static void main(String[] args) {
        new Demo();
    }
}
```

Output:
```
Han Solo is cached: true
Han Solo is cached: true
Han Solo is cached: false
```