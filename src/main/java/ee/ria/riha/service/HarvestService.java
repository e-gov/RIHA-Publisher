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
    List<Infosystem> infosystems = addApprovals(getInfosystems());
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
    String data = getDataAsJsonArray(url);

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

  private ArrayList<Infosystem> merge(List<Infosystem> infosystems) {
    ArrayList<Infosystem> result = new ArrayList<>();

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

  private List<Infosystem> addApprovals(List<Infosystem> infosystems) {
    merge(infosystems, getApprovals());
    return infosystems;
  }

  private Map<String, JSONObject> getApprovals() {
    JSONArray approvals = new JSONArray(getApprovalData());
    Map<String, JSONObject> approvalsById = new HashMap<>();
    for (int i = 0; i < approvals.length(); i++) {
      JSONObject jsonObject = approvals.getJSONObject(i);
      String id = jsonObject.getString("id");
      jsonObject.remove("id");
      approvalsById.put(id, jsonObject);
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

  String getDataAsJsonArray(String url) {
    try {
      return Get(url).execute().returnContent().asString();
    }
    catch (IOException e) {
      logger.warn("Could not fetch data from: " + url, e);
      return "[]";
    }
  }

  String getApprovalData() {
    return getDataAsJsonArray(approvalsUrl);
  }
}
