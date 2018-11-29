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
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.grid.GridBucketState;
import io.github.bucket4j.grid.ProxyManager;
import io.github.bucket4j.grid.jcache.JCache;
import io.grpc.Server;
import io.grpc.ServerBuilder;

public class RateLimitServer {

	private static final Logger LOGGER = LogManager.getLogger(RateLimitServer.class);

	private static final int port = 8081;

	private Server server;
	private ConfigLoader configLoader = new ConfigLoader();
	private Map<String, DomainBucketMapping> domainBuckets = new ConcurrentHashMap<>();
	private void start() throws Exception {
		LOGGER.info("Attempting to start server listening on {}", port);
		configLoader.setConfigFilePath("config");
		configLoader.loadDomainConfigs();
		server = ServerBuilder.forPort(port).addService(new RateLimiterImpl(configLoader,domainBuckets)).build().start();
		LOGGER.info("HPE Ratelimit Server started, listening on {}", port);

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			// Use stderr here since the logger may have been reset by its JVM
			// shutdown hook.
			System.err.println("Shutting down gRPC server since JVM is shutting down");
			RateLimitServer.this.stop();
			System.err.println("gRPC Server shut down");
		}));
		
	}

	
	private void stop() {
		if (server != null) {
			server.shutdown();
		}
	}

	 /**
     * we initialize a cache with name
     */
    private void initCache(String name) {
        // configure the cache
        MutableConfiguration<String, GridBucketState> config = new MutableConfiguration<String, GridBucketState>();
        config.setStoreByValue(true)
                .setTypes(String.class, GridBucketState.class)
                .setExpiryPolicyFactory(TouchedExpiryPolicy.factoryOf(new javax.cache.expiry.Duration(TimeUnit.MINUTES,20)))
                .setStatisticsEnabled(false);

        // create the cache
        Cache<String, GridBucketState> cache= Caching.getCachingProvider().getCacheManager().createCache(name, config);
        ProxyManager<String> buckets = Bucket4j.extension(JCache.class).proxyManagerForCache(cache);
        BucketConfiguration configuration = Bucket4j.configurationBuilder()
                .addLimit(Bandwidth.simple(30, Duration.ofMinutes(1))).buildConfiguration();
        // acquire cheap proxy to bucket  
       // Bucket bucket = buckets.getProxy(ip, configuration);
    }

	/**
	 * Main launches the server from the command line.
	 */
	public static void main(String[] args) throws Exception {
		final RateLimitServer server = new RateLimitServer();
		server.start();
		while(!Thread.interrupted())
		{
			Thread.sleep(300*1000L);
			server.checkAndReloadConfigFiles();
			
		}
	}

	private void checkAndReloadConfigFiles() {
		configLoader.checkAndReloadConfigs(domainBuckets);
		
	}

}
