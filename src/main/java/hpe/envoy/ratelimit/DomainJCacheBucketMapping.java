package hpe.envoy.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.cache.Cache;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.TouchedExpiryPolicy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.grid.GridBucketState;
import io.github.bucket4j.grid.ProxyManager;
import io.github.bucket4j.grid.jcache.JCache;

public class DomainJCacheBucketMapping implements DomainBucketMapping {
	private static final Logger LOGGER = LogManager.getLogger(DomainBucketMapping.class);
	private Map<String, Bucket> serviceBuckets = new ConcurrentHashMap<>();

	private final ProxyManager<String> buckets;
	private final Cache<String, GridBucketState> cache;

	public DomainJCacheBucketMapping(String domain) {
		super();
		// configure the cache
		MutableConfiguration<String, GridBucketState> config = new MutableConfiguration<String, GridBucketState>();
		config.setStoreByValue(true).setTypes(String.class, GridBucketState.class)
				.setExpiryPolicyFactory(
						TouchedExpiryPolicy.factoryOf(new javax.cache.expiry.Duration(TimeUnit.MINUTES, 20)))
				.setStatisticsEnabled(false);

		// create the Jcache instance
		cache = Caching.getCachingProvider().getCacheManager().createCache(domain, config);
		// create ProxyManager for Bucket
		buckets = Bucket4j.extension(JCache.class).proxyManagerForCache(cache);

	}

	public Bucket getServiceBucketFor(String svcKey, RatelimitDef rateLimitDef) {
		Bucket bucket = serviceBuckets.get(svcKey);
		if (bucket == null) {
			bucket = createNewBucket(svcKey, rateLimitDef);
			serviceBuckets.put(svcKey, bucket);
			LOGGER.debug("Created new bucket for destination {}", svcKey);
		}
		return bucket;
	}

	public void destroy() {
		LOGGER.info("close jcache mapping {} ", this);
		try {
			cache.clear();
			cache.close();
		} finally {
			serviceBuckets.clear();
		}
	}

	private Bucket createNewBucket(String svcKey, RatelimitDef rateLimitDef) {
		long overdraft = rateLimitDef.getOverdraft() > rateLimitDef.getRequests_per_unit() ? rateLimitDef.getOverdraft()
				: rateLimitDef.getRequests_per_unit();
		Refill refill = null;
		Duration duration = null;
		if (rateLimitDef.getUnit().equalsIgnoreCase("second")) {
			duration = Duration.ofSeconds(1);
		} else if (rateLimitDef.getUnit().equalsIgnoreCase("minute")) {
			duration = Duration.ofMinutes(1);
		} else if (rateLimitDef.getUnit().equalsIgnoreCase("hour")) {
			duration = Duration.ofHours(1);
		} else if (rateLimitDef.getUnit().equalsIgnoreCase("day")) {
			duration = Duration.ofDays(1);
		}
		refill = Refill.smooth(rateLimitDef.getRequests_per_unit(), duration);
		final BucketConfiguration configuration = Bucket4j.configurationBuilder()
				.addLimit(Bandwidth.classic(overdraft, refill)).buildConfiguration();
		// acquire cheap proxy to bucket
		return buckets.getProxy(svcKey, () -> {
			return configuration;
		});

	}

	@Override
	public String toString() {
		return "DomainJCacheBucketMapping [buckets=" + buckets + ", cache=" + cache + "]";
	}

}
