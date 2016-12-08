package ee.ria.riha.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.apache.http.client.fluent.Request.Get;

@Service
public class HarvestService {

  @Value("${approvals.url}")
  String approvalsUrl;

  @Value("${infosystems.url}")
  String infosystemsUrl;

  @Autowired InfosystemStorageService infosystemStorageService;

  @Scheduled(cron = "${harvester.cron}")
  public void harvestInfosystems() {
    String infosystemsWithApprovalData = addApprovals(getInfosystemsData());
    infosystemStorageService.save(infosystemsWithApprovalData);
  }

  private String addApprovals(String infosystemsJson) {
    JSONArray infosystems = new JSONArray(infosystemsJson);
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
      String id = jsonObject.getJSONObject("meta").getString("URI");
      if (approvalsById.containsKey(id)) jsonObject.put("approval", approvalsById.get(id));
    }
  }

  String getInfosystemsData() {
    try {
      return Get(infosystemsUrl).execute().returnContent().asString();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  String getApprovalData() {
    try {
      return Get(approvalsUrl).execute().returnContent().asString();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
