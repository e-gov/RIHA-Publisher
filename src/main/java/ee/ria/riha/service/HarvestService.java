package ee.ria.riha.service;

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

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
    String infosystemsWithApprovalData = addApprovals(getInfosystems());
    infosystemStorageService.save(infosystemsWithApprovalData);
  }

  private JSONArray getInfosystems() {
    List<JSONObject> allInfosystems = new ArrayList<>();
    Properties producers = getProducers();

    for (String owner : producers.stringPropertyNames()) {
      String url = producers.getProperty(owner);
      JSONArray infosystems = new JSONArray(getData(url));
      for (int i = 0; i < infosystems.length(); i++) {
        JSONObject infosystem = infosystems.getJSONObject(i);
        merge(allInfosystems, infosystem);
      }
    }
    return new JSONArray(allInfosystems);
  }

  private void merge(List<JSONObject> allInfosystems, JSONObject infosystem) {
    JSONObject existing = findInfosystem(allInfosystems, getId(infosystem));

    if (existing == null) {
      allInfosystems.add(infosystem);
    } else if (getUpdated(infosystem).isAfter(getUpdated(existing))) {
      allInfosystems.remove(existing);
      allInfosystems.add(infosystem);
    }
  }

  private LocalDateTime getUpdated(JSONObject infosystem) {
    return LocalDateTime.parse(infosystem.getJSONObject("status").getString("timestamp"), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  }

  private JSONObject findInfosystem(List<JSONObject> infosystems, String id) {
    for (JSONObject infosystem : infosystems) {
      if (getId(infosystem).equals(id)) return infosystem;
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

  private String addApprovals(JSONArray infosystems) {
    merge(infosystems, getApprovals());
    return infosystems.toString();
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

  private void merge(JSONArray infosystems, Map<String, JSONObject> approvalsById) {
    for (int i = 0; i < infosystems.length(); i++) {
      JSONObject jsonObject = infosystems.getJSONObject(i);
      String id = getId(jsonObject);
      if (approvalsById.containsKey(id)) jsonObject.put("approval", approvalsById.get(id));
    }
  }

  private String getId(JSONObject infosystem) {
    return infosystem.getJSONObject("meta").getString("URI");
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
