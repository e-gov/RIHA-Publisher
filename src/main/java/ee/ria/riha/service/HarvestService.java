package ee.ria.riha.service;

import ee.ria.riha.models.Infosystem;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
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

  @Value("${approvals.url}")
  String approvalsUrl;

  @Value("${legacyProducer.url}")
  String legacyProducerUrl;

  Properties producers;

  @Autowired InfosystemStorageService infosystemStorageService;

  @PostConstruct
  public void onStartup() {
    harvestInfosystems();
  }

  @Scheduled(cron = "${harvester.cron}")
  public void harvestInfosystems() {
    List<Infosystem> infosystems = addApprovals(getInfosystems());
    infosystemStorageService.save(infosystems);
  }

  private List<Infosystem> getInfosystems() {
    List<Infosystem> allInfosystems = new ArrayList<>();
    if (isNotBlank(legacyProducerUrl)) {
      getInfosystemsWithoutOwnerRestriction(allInfosystems, legacyProducerUrl);
    }

    Properties producers = getProducers();

    for (String url : producers.stringPropertyNames()) {
      List<String> allowedOwners = asList(producers.getProperty(url).split(","));
      getInfosystems(allInfosystems, url, allowedOwners);
    }
    return allInfosystems;
  }

  private void getInfosystemsWithoutOwnerRestriction(List<Infosystem> allInfosystems, String url) {
    getInfosystems(allInfosystems, url, null);
  }

  private void getInfosystems(List<Infosystem> allInfosystems, String url, List<String> allowedOwners) {
    JSONArray infosystems = new JSONArray(getData(url));
    for (int i = 0; i < infosystems.length(); i++) {
      Infosystem infosystem = new Infosystem(infosystems.getJSONObject(i));
      if (allowedOwners == null || allowedOwners.contains(infosystem.getOwner())) {
        merge(allInfosystems, infosystem);
      }
    }
  }

  private void merge(List<Infosystem> infosystems, Infosystem infosystem) {
    Infosystem existing = findInfosystem(infosystems, infosystem.getId());

    if (existing == null) {
      infosystems.add(infosystem);
    } else if (infosystem.getUpdated().isAfter(existing.getUpdated())) {
      infosystems.remove(existing);
      infosystems.add(infosystem);
    }
  }

  private Infosystem findInfosystem(List<Infosystem> infosystems, String id) {
    for (Infosystem infosystem : infosystems) {
      if (infosystem.getId().equals(id)) return infosystem;
    }
    return null;
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

  String getData(String url) {
    try {
      return Get(url).execute().returnContent().asString();
    }
    catch (IOException e) {
      //todo do not throw exception
      throw new RuntimeException(e);
    }
  }

  String getApprovalData() {
    return getData(approvalsUrl);
  }
}
