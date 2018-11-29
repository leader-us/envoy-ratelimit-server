package hpe.envoy.ratelimit;

public class RatelimitDef {
private String unit;
private int requests_per_unit;
private int overdraft=-1;
public String getUnit() {
	return unit;
}
public void setUnit(String unit) {
	this.unit = unit;
}
public int getRequests_per_unit() {
	return requests_per_unit;
}
public void setRequests_per_unit(int requests_per_unit) {
	this.requests_per_unit = requests_per_unit;
}
public int getOverdraft() {
	return overdraft;
}
public void setOverdraft(int overdraft) {
	this.overdraft = overdraft;
}


}
