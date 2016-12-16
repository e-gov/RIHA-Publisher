package ee.ria.riha.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
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

    initProducers();

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
    JSONArray infosystems;
    try {
      infosystems = getData(url);
    }
    catch (UnreachableResourceException e) {
      logger.error("Skipping producer - failed to get data from: " + url);
      return Collections.emptyList();
    }

    List<Infosystem> result = new ArrayList<>();
    int added = 0;
    for (int i = 0; i < infosystems.length(); i++) {
      JSONObject infosystemJson = infosystems.getJSONObject(i);
      if (!validateInfosystem(infosystemJson.toString())) {
        logger.warn("Skipping infosystem, invalid json: " + infosystemJson.toString());
        continue;
      }

      Infosystem infosystem = new Infosystem(infosystemJson);
      if (allowedOwners != null && !allowedOwners.contains(infosystem.getOwner())) {
        logger.warn("Skipping infosystem, owner code '{}' not whitelisted for url: {}", infosystem.getOwner(), url);
        continue;
      }

      result.add(infosystem);
      added++;
    }
    logger.info("{} processing finished, added {}/{} infosystems", url, added, infosystems.length());
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

  void initProducers() {
    Path path = Paths.get("producers.db");
    if (!path.toFile().exists()) return;

    try (InputStream inputStream = Files.newInputStream(path)) {
      producers = new Properties();
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
    JSONArray approvals = getApprovalData();

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

  JSONArray getData(String url) throws UnreachableResourceException {
    try {
      return new JSONArray(Get(url).execute().returnContent().asString());
    }
    catch (Exception e) {
      throw new UnreachableResourceException(e);
    }
  }

  JSONArray getApprovalData() throws UnreachableResourceException {
    return getData(approvalsUrl);
  }

  static class UnreachableResourceException extends Exception {
    UnreachableResourceException(Exception e) {
      super(e);
    }
  }

  boolean validateInfosystem(String infosystemJson) {
    try {
      String schemaJson = new String(Files.readAllBytes(Paths.get("infosystem-schema.json")));
      JsonNode schemaNode = new ObjectMapper().readValue(schemaJson, JsonNode.class);
      JsonNode infosystemNode = new ObjectMapper().readValue(infosystemJson, JsonNode.class);
      ProcessingReport report = JsonSchemaFactory.byDefault().getJsonSchema(schemaNode).validate(infosystemNode);
      return report.isSuccess();
    }
    catch (Exception e) {
      logger.error("Error validating infosystem", e);
      return false;
    }
  }
}