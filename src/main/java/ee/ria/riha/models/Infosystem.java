package ee.ria.riha.models;

import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Infosystem {

  private JSONObject json;

  public Infosystem(JSONObject json) {
    this.json = json;
  }

  public String getId() {
    return json.getString("uri");
  }

  public LocalDateTime getUpdated() {
    return LocalDateTime.parse(json.getJSONObject("meta").getJSONObject("system_status").getString("timestamp"), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  }

  public void setApproval(JSONObject approval){
    json.getJSONObject("meta").put("approval_status", approval);
  }

  public JSONObject getJson() {
    return json;
  }

  public String getOwner() {
    return json.getJSONObject("owner").getString("code");
  }
}
