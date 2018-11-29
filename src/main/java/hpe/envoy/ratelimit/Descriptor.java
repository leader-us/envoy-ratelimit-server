package hpe.envoy.ratelimit;

import java.util.List;

public class Descriptor {
private String key;
private String value;
private List<Descriptor> descriptors;
private RatelimitDef rate_limit;
public String getKey() {
	return key;
}
public void setKey(String key) {
	this.key = key;
}
public String getValue() {
	return value;
}
public void setValue(String value) {
	this.value = value;
}
public RatelimitDef getRate_limit() {
	return rate_limit;
}
public void setRate_limit(RatelimitDef rate_limit) {
	this.rate_limit = rate_limit;
}

public List<Descriptor> getDescriptors() {
	return descriptors;
}
public void setDescriptors(List<Descriptor> descriptors) {
	this.descriptors = descriptors;
}
@Override
public String toString() {
	return "Descriptor [key=" + key + ", value=" + value + ", rate_limit=" + rate_limit + ", descriptors=" + descriptors
			+ "]";
}

}
