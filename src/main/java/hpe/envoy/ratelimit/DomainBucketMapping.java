package hpe.envoy.ratelimit;

import io.github.bucket4j.Bucket;

public interface DomainBucketMapping {
	public Bucket getServiceBucketFor(String svcKey, RatelimitDef rateLimitDef);
	public void destroy() ;
}
