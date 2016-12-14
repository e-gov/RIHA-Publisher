package ee.ria.riha.service;

import ee.ria.riha.models.Infosystem;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.IOException;
import java.nio.file.Files;

import static java.util.Collections.singletonList;

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

    JSONAssert.assertEquals("[{\"savedJson\":\"true\"}]", service.load(), true);
  }

  @Test
  public void load_notHarvestedYet() throws IOException {
    service.filePath.toFile().delete();
    JSONAssert.assertEquals("[]", service.load(), true);
  }

  @Test
  public void save() throws IOException {
    service.save(singletonList(new Infosystem(new JSONObject("{\"savedJson\":\"false\"}"))));

    JSONAssert.assertEquals("[{\"savedJson\":\"false\"}]", new String(Files.readAllBytes(service.filePath)), true);
  }
}