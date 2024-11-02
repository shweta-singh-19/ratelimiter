import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class Config {
    final int timeWindowSizeInSeconds;
    final int limit;

    public Config(int timeWindowSizeInSeconds, int limit) {
        this.timeWindowSizeInSeconds = timeWindowSizeInSeconds;
        this.limit = limit;
    }
}

class ConfigStore {
    private final Map<String, Config> configs = new ConcurrentHashMap<>();

    public Config get(String key) {
        return configs.get(key);
    }

    public void put(String key, Config val) {
        configs.put(key, val);
    }
}

class RequestStore {
    private final Map<String, Map<Long, Integer>> keyToRequestCountMapping = new ConcurrentHashMap<>();

    public Map<Long, Integer> get(String key) {
        return keyToRequestCountMapping.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
    }

    public void incrementCount(String key, long timestamp) {
        Map<Long, Integer> requestCounts = get(key);
        requestCounts.merge(timestamp, 1, Integer::sum);
    }

    public void cleanup(String key, long startTime) {
        Map<Long, Integer> requestCounts = get(key);
        requestCounts.entrySet().removeIf(entry -> entry.getKey() < startTime);
    }

    public Map<String, Integer> getCurrentUsage(){
        Map<String, Integer> summary = new HashMap<>();
        keyToRequestCountMapping.forEach((key, counts) -> summary.put(key, counts.values().stream().mapToInt(Integer::intValue).sum()));
        return summary;
    }

}

public class SlidingWindowRateLimiter {
    private final ConfigStore configStore;
    private final RequestStore requestStore;

    public SlidingWindowRateLimiter() {
        this.configStore = new ConfigStore();
        this.requestStore = new RequestStore();
    }

    public boolean allowRequest(String key, long requestTime) {
        Config config = configStore.get(key);
        if (config == null) {
            return false;
        }

        long startTime = requestTime - config.timeWindowSizeInSeconds;
        int numberOfRequestsInWindow = getCurrentWindowCount(key, startTime);

        if (numberOfRequestsInWindow >= config.limit) {
            return false;
        }

        requestStore.incrementCount(key, requestTime);        
        return true;
    }   

    private int getCurrentWindowCount(String key, long startTime) {
        Map<Long, Integer> requestCounts = requestStore.get(key);
        requestStore.cleanup(key, startTime);

        return requestCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    
    public static void main(String[] args) throws InterruptedException {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        String userKey = "userid:shweta/getBlogs";

        
        // Configure rate limit: 5 requests per minute
        limiter.configStore.put(userKey, new Config(60, 5));

        // Simulate requests
        for (int i = 0; i < 10; i++) {
            long currentTime = Instant.now().getEpochSecond();
            boolean allowed = limiter.allowRequest(userKey, currentTime);
            System.out.printf("Request %d: %s%n", i + 1, allowed ? "Allowed" : "Rejected");
            Thread.sleep(1000); // Add small delay between requests            
        }

    }
}
