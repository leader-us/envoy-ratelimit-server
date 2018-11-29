package hpe.envoy.ratelimit;

import java.util.List;

public class DescriptorDomain {
	public static final String LOCAL_BUCKET="local";
	public static final String JCACHE_BUCKET="jcache";
	private String domain;
	private String bucketType=LOCAL_BUCKET;
	private String yamlFile;
	private List<Descriptor> descriptors;

	public DescriptorDomain(String domainName, List<Descriptor> descriptors2) {
		this.domain = domainName;
		this.descriptors = descriptors2;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public List<Descriptor> getDescriptors() {
		return descriptors;
	}

	public void setDescriptors(List<Descriptor> descriptors) {
		this.descriptors = descriptors;
	}

	public String getYamlFile() {
		return yamlFile;
	}

	public void setYamlFile(String yamlFile) {
		this.yamlFile = yamlFile;
	}



	public String getBucketType() {
		return bucketType;
	}

	public void setBucketType(String bucketType) {
		this.bucketType = bucketType;
	}

	@Override
	public String toString() {
		return "DescriptorDomain [domain=" + domain + ", bucketType=" + bucketType + ", yamlFile=" + yamlFile
				+ ", descriptors=" + descriptors + "]";
	}


}
