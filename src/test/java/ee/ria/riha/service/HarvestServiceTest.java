package ee.ria.riha.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class HarvestServiceTest {

  @Mock InfosystemStorageService storageService;

  @Spy @InjectMocks
  private HarvestService service = new HarvestService();

  @Test
  public void addApprovalData() {
    doReturn("[{\"id\":\"/owner/shortname1\",\"timestamp\":\"2016-01-01T10:00:00\",\"status\":\"MITTE KOOSKÕLASTATUD\"}," +
      "{\"id\":\"/owner/shortname2\",\"timestamp\":\"2015-10-10T01:10:10\",\"status\":\"KOOSKÕLASTATUD\"}]")
      .when(service).getApprovalData();

    String infosystemsData = "[" +
      "{" +
      "\"shortname\": \"shortname1\"," +
      "\"owner\": \"owner\"," +
      "\"meta\": {" +
      "\"URI\": \"/owner/shortname1\"" +
      "}" +
      "}," +
      "{" +
      "\"shortname\": \"shortname3\"," +
      "\"owner\": \"owner\"," +
      "\"meta\": {" +
      "\"URI\": \"/70000740/\\u00d5ppurite register\"" +
      "}" +
      "}" +
      "]";
    doReturn(infosystemsData).when(service).getInfosystemsData();

    service.harvestInfosystems();

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(storageService).save(captor.capture());

    assertEquals("[" +
      "{" +
      "\"owner\":\"owner\"," +
      "\"meta\":{" +
      "\"URI\":\"/owner/shortname1\"" +
      "}," +
      "\"approval\":{" +
      "\"timestamp\":\"2016-01-01T10:00:00\"," +
      "\"status\":\"MITTE KOOSKÕLASTATUD\"" +
      "}," +
      "\"shortname\":\"shortname1\"" +
      "}," +
      "{" +
      "\"owner\":\"owner\"," +
      "\"meta\":{" +
      "\"URI\":\"/70000740/Õppurite register\"" +
      "}," +
      "\"shortname\":\"shortname3\"" +
      "}" +
      "]", captor.getValue());
  }
}