package hpe.envoy.ratelimit;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import envoy.service.ratelimit.v2.RateLimitServiceGrpc;
import envoy.service.ratelimit.v2.Ratelimit;
import envoy.service.ratelimit.v2.Ratelimit.RateLimitDescriptor;
import envoy.service.ratelimit.v2.Ratelimit.RateLimitDescriptor.Entry;
import envoy.service.ratelimit.v2.Ratelimit.RateLimitResponse.Code;
import io.github.bucket4j.Bucket;
import io.grpc.stub.StreamObserver;

public class RateLimiterImpl extends RateLimitServiceGrpc.RateLimitServiceImplBase {
	private static final Logger LOGGER = LogManager.getLogger(RateLimiterImpl.class);

	private ConfigLoader configLoader;
	private Map<String, DomainBucketMapping> domainBuckets;

	public RateLimiterImpl(ConfigLoader configLoader, Map<String, DomainBucketMapping> domainBuckets) {
		this.configLoader = configLoader;
		this.domainBuckets = domainBuckets;
	}

	@Override
	public void shouldRateLimit(Ratelimit.RateLimitRequest rateLimitRequest,
			StreamObserver<Ratelimit.RateLimitResponse> responseStreamObserver) {
		logDebug(rateLimitRequest);
		Ratelimit.RateLimitResponse.Code code;
		String domainName = rateLimitRequest.getDomain();
		DescriptorDomain domain = configLoader.getAllDomains().get(domainName);
		if (domain == null) {
			LOGGER.warn("not find domain {} ", domainName);
			code = Ratelimit.RateLimitResponse.Code.OK;
		} else {
			code = judgeLimit(domain, rateLimitRequest.getDescriptorsList());
		}
		Ratelimit.RateLimitResponse rateLimitResponse = generateRateLimitResponse(code);
		responseStreamObserver.onNext(rateLimitResponse);
		responseStreamObserver.onCompleted();
	}

	private boolean matched(Descriptor domainDescrpt, Entry theEntry) {
		return ((theEntry.getKey().equals(domainDescrpt.getKey())))
				&& ((domainDescrpt.getValue() == null) || domainDescrpt.getValue().equals(theEntry.getValue()));

	}

	private Ratelimit.RateLimitResponse.Code judgeLimit(DescriptorDomain domain, List<RateLimitDescriptor> request) {
		// domain: messaging
		// descriptors:
		// # Only allow 5 marketing messages a day
		// - key: message_type
		// value: marketing
		// descriptors:
		// - key: to_number
		// rate_limit:
		// unit: day
		// requests_per_unit: 5
		RateLimitDescriptor fDes = request.get(0);
		Entry firstLevelEntry = fDes.getEntries(0);
		for (Descriptor domainDescrpt : domain.getDescriptors()) {
			// 尝试第一级匹配
			if (matched(domainDescrpt, firstLevelEntry)) {
				RatelimitDef limitDef = null;
				if (domainDescrpt.getDescriptors() != null) {// 尝试第二级匹配
					Entry secondLevelEntry = fDes.getEntries(1);
					for (Descriptor secondeDomainDescrpt : domainDescrpt.getDescriptors()) {
						if (matched(secondeDomainDescrpt, secondLevelEntry)) {// 第二级匹配成功
							limitDef = secondeDomainDescrpt.getRate_limit();
							String rateKey = firstLevelEntry.getKey() + '_' + firstLevelEntry.getValue() + '_'
									+ secondLevelEntry.getKey() + '_' + secondLevelEntry.getValue();
							LOGGER.debug("full matched with second level. key {} ", rateKey);
							Bucket bucket = getServiceBucketFor(domain.getDomain(), rateKey, limitDef);
							// CompletableFuture<Boolean> limitCheckingFuture =
							// bucket.asAsync().tryConsume(1);
							if (bucket.tryConsume(1)) {
								return Code.OK;
							} else {
								return Code.OVER_LIMIT;
							}

						}
					}

				} else {// 没有第二级
					limitDef = domainDescrpt.getRate_limit();
					String rateKey = firstLevelEntry.getKey() + '_' + firstLevelEntry.getValue();
					LOGGER.debug("full  matched . key {} ", rateKey);
					Bucket bucket = getServiceBucketFor(domain.getDomain(), rateKey, limitDef);
					if (bucket.tryConsume(1)) {
						return Code.OK;
					} else {
						return Code.OVER_LIMIT;
					}
				}

			}
		}
		LOGGER.debug("not  matched .");
		return Code.UNKNOWN;
	}

	private void logDebug(Ratelimit.RateLimitRequest rateLimitRequest) {

		LOGGER.debug("Domain: {}", rateLimitRequest.getDomain());
		LOGGER.debug("DescriptorsCount: {}", rateLimitRequest.getDescriptorsCount());

		if (LOGGER.isDebugEnabled()) {
			rateLimitRequest.getDescriptorsList().forEach(d -> {
				LOGGER.debug("-- New descriptor -- ");
				d.getEntriesList().forEach(e -> {
					LOGGER.debug("Descriptor Entry: [{}, {}]", e.getKey(), e.getValue());

				});
			});
		}
	}

	private Bucket getServiceBucketFor(String domain, String svcKey, RatelimitDef rateLimitDef) {
		DomainBucketMapping mapping = domainBuckets.get(domain);
		if (mapping == null) {
			DescriptorDomain theDomainObj = configLoader.getAllDomains().get(domain);
			if (theDomainObj.getBucketType().equalsIgnoreCase(DescriptorDomain.LOCAL_BUCKET)) {
				mapping = new DomainLocalBucketMapping();
			} else if (theDomainObj.getBucketType().equalsIgnoreCase(DescriptorDomain.JCACHE_BUCKET)) {
				mapping = new DomainJCacheBucketMapping(domain);
			} else {
				throw new RuntimeException("can't find bucket type " + theDomainObj.getBucketType());
			}
			domainBuckets.put(domain, mapping);

		}
		return mapping.getServiceBucketFor(svcKey, rateLimitDef);
	}

	private Ratelimit.RateLimitResponse generateRateLimitResponse(Ratelimit.RateLimitResponse.Code code) {
		LOGGER.debug("Generate rate limit response with code: {} ", code);
		return Ratelimit.RateLimitResponse.newBuilder().setOverallCode(code).build();
	}
}