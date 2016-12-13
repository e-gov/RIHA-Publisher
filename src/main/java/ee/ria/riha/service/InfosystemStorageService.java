package ee.ria.riha.service;

import ee.ria.riha.models.Infosystem;
import org.json.JSONArray;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

@Service
public class InfosystemStorageService {

  Path filePath = Paths.get("infosystems.json");

  public String load() {
    try {
      if (!filePath.toFile().exists()) return "[]";
      return new String(Files.readAllBytes(filePath), UTF_8);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void save(List<Infosystem> infosystems) {
    try {
      String json = new JSONArray(infosystems.stream().map(Infosystem::getJson).collect(toList())).toString();
      Files.write(filePath, json.getBytes(UTF_8));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
