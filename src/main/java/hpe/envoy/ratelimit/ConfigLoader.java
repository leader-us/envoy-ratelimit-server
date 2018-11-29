package hpe.envoy.ratelimit;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.esotericsoftware.yamlbeans.YamlReader;

public class ConfigLoader {
	private static final Logger LOGGER = LogManager.getLogger(ConfigLoader.class);

	private Map<String, DescriptorDomain> allDomains = new HashMap<>();
	private Map<String, Long> domainFiletimeMap = new HashMap<>();
	private String configFilePath;

	public String getConfigFilePath() {
		return configFilePath;
	}

	public void setConfigFilePath(String configFilePath) {
		this.configFilePath = configFilePath;
	}

	public Map<String, DescriptorDomain> getAllDomains() {
		return allDomains;
	}

	public void checkAndReloadConfigs(Map<String, DomainBucketMapping> domainBuckets) {
		LOGGER.info("check domain config file changes ");
		Set<String> curYamlSet = new HashSet<>();
		try {
			File[] yamlfiles = new File(configFilePath)
					.listFiles((dir, name) -> (name.endsWith(".yaml") ? true : false));
			if (yamlfiles == null || yamlfiles.length == 0) {
				LOGGER.warn("no domain files  in " + new File(configFilePath).getAbsolutePath());
			} else {
				for (File yamfile : yamlfiles) {
					curYamlSet.add(yamfile.getName());
					Long updatTime = domainFiletimeMap.get(yamfile.getName());
					if (!Long.valueOf(yamfile.lastModified()).equals(updatTime)) {
						LOGGER.info("find domain file updated and reload{}", yamfile.getName());
						DescriptorDomain domain = parseDomainFile(yamfile);
						if (domain != null) {
							allDomains.put(domain.getDomain(), domain);
							domainFiletimeMap.put(yamfile.getName(), yamfile.lastModified());
							// 删除旧Bucket
							DomainBucketMapping mapping = domainBuckets.remove(domain.getDomain());
							if (mapping != null) {
								mapping.destroy();
							}
						}

					}
				}
			}
		} catch (Exception e) {
			LOGGER.warn("caught err " + e);
		}

		// 对比是否有Domain文件被删除
		Iterator<Entry<String, DescriptorDomain>> itor = allDomains.entrySet().iterator();
		while (itor.hasNext()) {
			Entry<String, DescriptorDomain> entry = itor.next();
			DescriptorDomain domain = entry.getValue();
			if (!curYamlSet.contains(domain.getYamlFile())) {
				// 已经删除了这个Domain配置文件
				itor.remove();
				// 删除旧Bucket
				DomainBucketMapping mapping = domainBuckets.remove(domain.getDomain());
				if (mapping != null) {
					mapping.destroy();
				}
			}

		}
	}

	public void loadDomainConfigs() {
		try {
			File[] yamlfiles = new File(configFilePath)
					.listFiles((dir, name) -> (name.endsWith(".yaml") ? true : false));
			if (yamlfiles == null || yamlfiles.length == 0) {
				LOGGER.warn("no domain files  in " + new File(configFilePath).getAbsolutePath());
				return;
			}
			for (File yamfile : yamlfiles) {
				LOGGER.info("load domain file  " + yamfile);

				DescriptorDomain domain = parseDomainFile(yamfile);
				if (domain != null) {
					if (allDomains.containsKey(domain.getDomain())) {
						LOGGER.warn("duplicated domain " + domain.getDomain());
					} else {
						allDomains.put(domain.getDomain(), domain);
						domainFiletimeMap.put(yamfile.getName(), yamfile.lastModified());

					}
				}

			}
		} catch (Exception e) {
			LOGGER.warn("load domain file err " + e);
		}

	}

	private DescriptorDomain parseDomainFile(File yamfile) throws FileNotFoundException, IOException {
		DescriptorDomain domain = null;
		YamlReader reader = new YamlReader(new FileReader(yamfile));
		try {
			Map<String, Object> map = (Map<String, Object>) reader.read();
			String domainName = (String) map.get("domain");
			String bucketTypeStr = (String) map.get("bucket");

			ArrayList descriptorLst = (ArrayList) map.get("descriptors");
			List<Descriptor> descriptors = parseDescriptors(descriptorLst);
			domain = new DescriptorDomain(domainName, descriptors);
			if (bucketTypeStr != null) {
				domain.setBucketType(bucketTypeStr);
			}
			domain.setYamlFile(yamfile.getName());
			LOGGER.info("loaded domain " + domain);
			return domain;
		} catch (Exception e) {
			LOGGER.warn("load domain file err " + yamfile, e);
		} finally {
			if (reader != null) {
				reader.close();
			}
		}
		return null;
	}

	private List<Descriptor> parseDescriptors(ArrayList<Map<String, Object>> descriptorLst) {
		List<Descriptor> descriptors = new LinkedList<Descriptor>();
		for (Map<String, Object> theMap : descriptorLst) {
			String key = (String) theMap.get("key");
			String value = (String) theMap.get("value");
			Map ratelimitMap = (Map) theMap.get("rate_limit");
			RatelimitDef rate_limit = null;
			List<Descriptor> childDes = null;
			if (ratelimitMap != null) {
				rate_limit = new RatelimitDef();
				// Map<String,String> ratelimitMap=(Map<String, String>)
				// ratelimitList.get(0);
				rate_limit.setUnit((String) ratelimitMap.get("unit"));
				rate_limit.setRequests_per_unit(Integer.valueOf((String) ratelimitMap.get("requests_per_unit")));
				String overdraftStr = (String) ratelimitMap.get("overdraft");
				if (overdraftStr != null) {
					rate_limit.setOverdraft(Integer.valueOf(overdraftStr));
				}
			} else {
				ArrayList childLst = (ArrayList) theMap.get("descriptors");
				if (childLst == null || childLst.isEmpty()) {
					LOGGER.warn("no rate_limit define for " + key);
					continue;
				}
				childDes = parseDescriptors(childLst);
			}
			Descriptor des = new Descriptor();
			des.setKey(key);
			des.setValue(value);
			des.setRate_limit(rate_limit);
			des.setDescriptors(childDes);
			descriptors.add(des);
		}
		return descriptors;
	}

	public static void main(String[] args) throws Exception {
		ConfigLoader loader = new ConfigLoader();
		loader.configFilePath = "config";
		loader.loadDomainConfigs();
	}
}
