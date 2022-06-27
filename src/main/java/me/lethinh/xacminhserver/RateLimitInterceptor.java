package me.lethinh.xacminhserver;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final short NORMAL_PACKET_SIZE = 6400;
    private static final short MAX_TOKENS_PER_IP = 3;
    private static final short SECONDS_TO_REFILL_CLIENT_TOKEN = 8;
    private static final Logger LOGGER = LogManager.getLogger("XacMinh");

    private final Bucket mainBucket;
    private final LoadingCache<String, Bucket> bucketPerIp;

    public RateLimitInterceptor() {
        this.mainBucket = Bucket.builder()
                .addLimit(Bandwidth.classic(80, Refill.intervally(80, Duration.ofMinutes(1))))
                .build();
        this.bucketPerIp = CacheBuilder.newBuilder()
                .maximumSize(80)
                .expireAfterAccess(Duration.ofSeconds(SECONDS_TO_REFILL_CLIENT_TOKEN))
                .build(new CacheLoader<String, Bucket>() {
                    @Override
                    public Bucket load(String key) throws Exception {
                        return Bucket.builder()
                                .addLimit(Bandwidth.classic(MAX_TOKENS_PER_IP, Refill.intervally(MAX_TOKENS_PER_IP, Duration.ofSeconds(SECONDS_TO_REFILL_CLIENT_TOKEN))))
                                .build();
                    }
                });
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        long tokens = Math.max(1, request.getContentLengthLong() / NORMAL_PACKET_SIZE);
        if (!mainBucket.tryConsume(tokens)) {
            LOGGER.warn("Ko the xu li yeu cau xac minh, da toi gioi han 80 req/min!");
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            // optimizing
            bucketPerIp.invalidateAll();
            return false;
        }

        Bucket requestBucket = bucketPerIp.get(request.getRemoteAddr());

        if (!requestBucket.tryConsume(tokens)) {
            LOGGER.warn("Ko the xu li yeu cau xac minh, da toi gioi han 3 req/client/10 secs!");
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            return false;
        }

        return true;
    }

}
