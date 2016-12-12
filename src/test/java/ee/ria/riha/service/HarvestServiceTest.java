package ee.ria.riha.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Properties;

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
    service.producers = new Properties();
    service.producers.setProperty("riha-legacy", "data-url");

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
    System.out.println(infosystemsData);
    doReturn(infosystemsData).when(service).getData("data-url");

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

  @Test
  public void loadDataFromMultipleProducers() {
    service.producers = new Properties();
    service.producers.setProperty("riha-legacy", "data-url");
    service.producers.setProperty("other-producer", "other-url");

    doReturn("[]").when(service).getApprovalData();
    doReturn("[{\"meta\": {\"URI\": \"/owner/shortname1\"}}]").when(service).getData("data-url");
    doReturn("[{\"meta\": {\"URI\": \"/owner/shortname2\"}}]").when(service).getData("other-url");

    service.harvestInfosystems();

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(storageService).save(captor.capture());

    assertEquals("[{\"meta\":{\"URI\":\"/owner/shortname1\"}}," +
                  "{\"meta\":{\"URI\":\"/owner/shortname2\"}}]",
      captor.getValue());

    verify(service).getData("data-url");
    verify(service).getData("other-url");
  }

  @Test
  public void loadDataFromMultipleProducers_takesMostRecentInfosystemData() {
    service.producers = new Properties();
    service.producers.setProperty("riha-legacy", "data-url");
    service.producers.setProperty("other-producer", "other-url");

    doReturn("[]").when(service).getApprovalData();
    doReturn("[{\"meta\":{\"URI\":\"/owner/shortname1\"},\"status\":{\"timestamp\":\"2013-11-08T00:00:00.000001\"}}," +
              "{\"meta\":{\"URI\":\"/owner/shortname1\"},\"status\":{\"timestamp\":\"2016-01-01T00:00:00\"}}]")
      .when(service).getData("data-url");
    doReturn("[{\"meta\":{\"URI\":\"/owner/shortname1\"},\"status\":{\"timestamp\":\"2013-11-08T00:01:00\"}}]")
      .when(service).getData("other-url");

    service.harvestInfosystems();

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(storageService).save(captor.capture());

    assertEquals("[{\"meta\":{\"URI\":\"/owner/shortname1\"},\"status\":{\"timestamp\":\"2016-01-01T00:00:00\"}}]", captor.getValue());

    verify(service).getData("data-url");
    verify(service).getData("other-url");
  }

  @Test
  public void loadDataFromMultipleProducers_takesOnlyOneInfosystemIfTwoAreEquallyRecent() {
    service.producers = new Properties();
    service.producers.setProperty("riha-legacy", "data-url");

    doReturn("[]").when(service).getApprovalData();
    doReturn("[{\"meta\":{\"URI\":\"/owner/shortname1\"},\"status\":{\"timestamp\":\"2016-01-01T00:00:00\"}}," +
      "{\"meta\":{\"URI\":\"/owner/shortname1\"},\"status\":{\"timestamp\":\"2016-01-01T00:00:00\"}}]")
      .when(service).getData("data-url");

    service.harvestInfosystems();

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(storageService).save(captor.capture());

    assertEquals("[{\"meta\":{\"URI\":\"/owner/shortname1\"},\"status\":{\"timestamp\":\"2016-01-01T00:00:00\"}}]", captor.getValue());
  }
}