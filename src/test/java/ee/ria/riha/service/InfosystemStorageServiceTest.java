package ee.ria.riha.service;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;

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
    service.save("[{\"savedJson\":\"false\"}]");

    assertEquals("[{\"savedJson\":\"false\"}]", new String(Files.readAllBytes(service.filePath)));
  }
}