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
    return json.getJSONObject("meta").getString("URI");
  }

  public LocalDateTime getUpdated() {
    return LocalDateTime.parse(json.getJSONObject("status").getString("timestamp"), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  }

  public void setApproval(JSONObject approval){
    json.put("approval", approval);
  }

  public JSONObject getJson() {
    return json;
  }
}
