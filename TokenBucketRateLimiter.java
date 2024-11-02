import java.sql.Time;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;


//Amazon and Stripe uses this rate limiting algo to throttle their api request
//Similar is leaking bucket algo
class TokenBucketRateLimiter{
    private final int tokenBucketCapacity;
    private final long refillIntervalNanos;
    private final AtomicInteger currentTokens;
    private volatile long lastRefilledNanos;

    public RateLimiterUsingTokenBucket(int tokenBucketCapacity, Duration refillInterval){
        this.tokenBucketCapacity = tokenBucketCapacity;
        this.refillIntervalNanos = refillInterval.toNanos();
        currentTokens = new AtomicInteger(tokenBucketCapacity);
        lastRefilledNanos = System.nanoTime();               
    }

    public int getAvailableTokens(){
        return currentTokens.get();
    }

    public synchronized boolean consumeToken(){
        refillBucket();

        if (currentTokens.get() == 0) {
            //System.out.printf("Rejected %s\n", request);
            return false;
        } 

        currentTokens.decrementAndGet();
        return true;        
    }

    public void refillBucket(){         
        long now = System.nanoTime();
        long elapsedTime = now - lastRefilledNanos; 
        
        if (elapsedTime < refillIntervalNanos) {
            return;
        }

        synchronized(this) {
            //check again if any other thread refilled

            elapsedTime = now - lastRefilledNanos;
            if (elapsedTime < refillIntervalNanos) {
                return;
            }
            lastRefilledNanos = System.nanoTime();
            System.out.printf("last Refilled tokens at %s\n", (double)lastRefilledNanos/1_000_000_000);                        
            currentTokens.set(tokenBucketCapacity);             
        }
        
                                 
    }

    public static void main(String[] args) throws Exception{
        RateLimiterUsingTokenBucket rateLimiter = new RateLimiterUsingTokenBucket(5, Duration.ofSeconds(1));

        
        for(int i = 0; i < 1_000_000_000; i++) {   
            Thread.sleep(100);   

            boolean acquired = rateLimiter.consumeToken();
            System.out.printf("[%s] Request %d: %s (Available tokens: %d)%n",
            "thread", i, acquired ? "accepted" : "rejected", 
            rateLimiter.getAvailableTokens());      
        }
    }
}
