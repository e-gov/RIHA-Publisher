package ee.ria.riha.service;

import ee.ria.riha.models.Infosystem;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

public class InfosystemStorageServiceTest {

  private InfosystemStorageService service;

  @Before
  public void setUp() throws Exception {
    service = new InfosystemStorageService();
    service.filePath = Files.createTempFile("", "");
  }

  @Test
  public void load() throws IOException {
    Files.write(service.filePath, "[{\"savedJson\":\"true\"}]".getBytes());

    assertEquals("[{\"savedJson\":\"true\"}]", service.load());
  }

  @Test
  public void save() throws IOException {
    service.save(singletonList(new Infosystem(new JSONObject("{\"savedJson\":\"false\"}"))));

    assertEquals("[{\"savedJson\":\"false\"}]", new String(Files.readAllBytes(service.filePath)));
  }
}