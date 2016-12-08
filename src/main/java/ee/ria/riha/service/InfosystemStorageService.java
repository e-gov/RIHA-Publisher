package ee.ria.riha.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.charset.StandardCharsets.UTF_8;

@Service
public class InfosystemStorageService {

  Path path = Paths.get("infosystems.json");

  public String load() {
    try {
      return new String(Files.readAllBytes(path), UTF_8);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void save(String infosystemsJson) {
    try {
      Files.write(path, infosystemsJson.getBytes(UTF_8));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
