package ee.ria.riha.service;

import ee.ria.riha.models.Infosystem;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.client.fluent.Request.Get;

@Service
public class HarvestService {

  private Logger logger = LoggerFactory.getLogger(HarvestService.class);

  @Value("${approvals.url}")
  String approvalsUrl;

  @Value("${legacyProducer.url}")
  String legacyProducerUrl;

  Properties producers;

  @Autowired InfosystemStorageService infosystemStorageService;

  @Scheduled(cron = "${harvester.cron}")
  public void harvestInfosystems() {
    logger.info("Started");
    Map<String, JSONObject> approvals;
    try {
      approvals = getApprovals();
    } catch (UnreachableResourceException e) {
      logger.info("Skipping harvesting - could not get approval information!", e);
      return;
    }
    List<Infosystem> infosystems = addApprovals(getInfosystems(), approvals);
    infosystemStorageService.save(infosystems);
    logger.info("Finished");
  }

  private List<Infosystem> getInfosystems() {
    List<Infosystem> allInfosystems = new ArrayList<>();
    if (isNotBlank(legacyProducerUrl)) {
      allInfosystems.addAll(getInfosystemsWithoutOwnerRestriction(legacyProducerUrl));
    }

    Properties producers = getProducers();

    for (String url : producers.stringPropertyNames()) {
      List<String> allowedOwners = asList(producers.getProperty(url).split(","));
      allInfosystems.addAll(getInfosystems(url, allowedOwners));
    }

    return merge(allInfosystems);
  }

  private List<Infosystem> getInfosystemsWithoutOwnerRestriction(String url) {
    return getInfosystems(url, null);
  }

  private List<Infosystem> getInfosystems(String url, List<String> allowedOwners) {
    String data;
    try {
      data = getDataAsJsonArray(url);
    }
    catch (UnreachableResourceException e) {
      logger.error("Skipping producer - failed to get data from: " + url);
      return Collections.emptyList();
    }

    JSONArray infosystems = new JSONArray(data);
    List<Infosystem> result = new ArrayList<>();
    for (int i = 0; i < infosystems.length(); i++) {
      Infosystem infosystem = new Infosystem(infosystems.getJSONObject(i));
      if (allowedOwners== null || allowedOwners.contains(infosystem.getOwner())) {
        result.add(infosystem);
      }
    }
    return result;
  }

  private List<Infosystem> merge(List<Infosystem> infosystems) {
    List<Infosystem> result = new ArrayList<>();

    for (Infosystem infosystem : infosystems) {
      Infosystem existing = result.stream().filter(i -> i.getId().equals(infosystem.getId())).findAny().orElse(null);

      if (existing == null) {
        result.add(infosystem);
      } else if (infosystem.getUpdated().isAfter(existing.getUpdated())) {
        result.remove(existing);
        result.add(infosystem);
      }
    }
    return result;
  }

  private Properties getProducers() {
    if (producers == null) {
      initProducers();
    }
    return producers;
  }

  private void initProducers() {
    Path path = Paths.get("producers.db");

    producers = new Properties();
    if (!path.toFile().exists()) return;

    try (InputStream inputStream = Files.newInputStream(path)) {
      producers.load(inputStream);
    }
    catch (IOException e) {
      logger.error("Could not read producers db " + path, e);
      throw new RuntimeException(e);
    }
  }

  private List<Infosystem> addApprovals(List<Infosystem> infosystems, Map<String, JSONObject> approvals) {
    merge(infosystems, approvals);
    return infosystems;
  }

  private Map<String, JSONObject> getApprovals() throws UnreachableResourceException {
    JSONArray approvals = new JSONArray(getApprovalData());
    Map<String, JSONObject> approvalsById = new HashMap<>();
    for (int i = 0; i < approvals.length(); i++) {
      JSONObject jsonObject = approvals.getJSONObject(i);
      String uri = jsonObject.getString("uri");
      jsonObject.remove("uri");
      approvalsById.put(uri, jsonObject);
    }
    return approvalsById;
  }

  private void merge(List<Infosystem> infosystems, Map<String, JSONObject> approvalsById) {
    for (Infosystem infosystem : infosystems) {
      String id = infosystem.getId();
      if (approvalsById.containsKey(id)) {
        infosystem.setApproval(approvalsById.get(id));
      }
    }
  }

  String getDataAsJsonArray(String url) throws UnreachableResourceException {
    try {
      return Get(url).execute().returnContent().asString();
    }
    catch (Exception e) {
      throw new UnreachableResourceException(e);
    }
  }

  String getApprovalData() throws UnreachableResourceException {
    return getDataAsJsonArray(approvalsUrl);
  }

  static class UnreachableResourceException extends Exception {
    UnreachableResourceException(Exception e) {
      super(e);
    }
  }
}
