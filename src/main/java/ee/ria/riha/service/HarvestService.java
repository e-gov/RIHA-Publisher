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
import java.nio.file.Paths;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static org.apache.http.client.fluent.Request.Get;

@Service
public class HarvestService {

  @Value("${approvals.url}")
  String approvalsUrl;

  Properties producers;

  @Autowired InfosystemStorageService infosystemStorageService;

  @PostConstruct
  public void onStartup() {
    harvestInfosystems();
  }

  @Scheduled(cron = "${harvester.cron}")
  public void harvestInfosystems() {
    List<Infosystem> infosystems = addApprovals(getInfosystems());
    infosystemStorageService.save(new JSONArray(infosystems.stream().map(Infosystem::getJson).collect(toList())).toString());
  }

  private List<Infosystem> getInfosystems() {
    List<Infosystem> allInfosystems = new ArrayList<>();
    Properties producers = getProducers();

    for (String owner : producers.stringPropertyNames()) {
      String url = producers.getProperty(owner);
      JSONArray infosystems = new JSONArray(getData(url));
      for (int i = 0; i < infosystems.length(); i++) {
        Infosystem infosystem = new Infosystem(infosystems.getJSONObject(i));
        merge(allInfosystems, infosystem);
      }
    }
    return allInfosystems;
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
    try(InputStream inputStream = Files.newInputStream(Paths.get("producers.db"))) {
      producers = new Properties();
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
      throw new RuntimeException(e);
    }
  }

  String getApprovalData() {
    return getData(approvalsUrl);
  }
}
