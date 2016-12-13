package ee.ria.riha.service;

import ee.ria.riha.models.Infosystem;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class HarvestServiceTest {

  @Mock InfosystemStorageService storageService;

  @Spy @InjectMocks
  private HarvestService service = new HarvestService();

  @Before
  public void setUp() throws Exception {
    service.producers = new Properties();
  }

  @Test
  public void addApprovalData() {
    service.producers.setProperty("data-url", "producer");

    doReturn("[{\"id\":\"/owner/shortname1\",\"timestamp\":\"2016-01-01T10:00:00\",\"status\":\"MITTE KOOSKÕLASTATUD\"}," +
      "{\"id\":\"/owner/shortname2\",\"timestamp\":\"2015-10-10T01:10:10\",\"status\":\"KOOSKÕLASTATUD\"}]")
      .when(service).getApprovalData();

    String infosystemsData = "[" +
      "{" +
      "\"shortname\": \"shortname1\"," +
      "\"owner\": \"producer\"," +
      "\"meta\": {" +
      "\"URI\": \"/owner/shortname1\"" +
      "}" +
      "}," +
      "{" +
      "\"shortname\": \"shortname3\"," +
      "\"owner\": \"producer\"," +
      "\"meta\": {" +
      "\"URI\": \"/70000740/\\u00d5ppurite register\"" +
      "}" +
      "}" +
      "]";
    doReturn(infosystemsData).when(service).getData("data-url");

    service.harvestInfosystems();

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(storageService).save(captor.capture());

    List<Infosystem> infosystems = captor.getValue();
    assertEquals(2, infosystems.size());
    assertEquals("{\"owner\":\"producer\"," +
      "\"meta\":{\"URI\":\"/owner/shortname1\"}," +
      "\"approval\":{\"timestamp\":\"2016-01-01T10:00:00\",\"status\":\"MITTE KOOSKÕLASTATUD\"}," +
      "\"shortname\":\"shortname1\"}", infosystems.get(0).getJson().toString());

    assertEquals("{\"owner\":\"producer\"," +
      "\"meta\":{\"URI\":\"/70000740/Õppurite register\"}," +
      "\"shortname\":\"shortname3\"}", infosystems.get(1).getJson().toString());
  }

  @Test
  public void loadDataFromMultipleProducers() {
    service.producers.setProperty("data-url", "producer");
    service.producers.setProperty("other-url", "other-producer");

    doReturn("[]").when(service).getApprovalData();
    doReturn("[{\"owner\":\"producer\",\"meta\": {\"URI\": \"/owner/shortname1\"}}]").when(service).getData("data-url");
    doReturn("[{\"owner\":\"other-producer\",\"meta\": {\"URI\": \"/owner/shortname2\"}}]").when(service).getData("other-url");

    service.harvestInfosystems();

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(storageService).save(captor.capture());
    List<Infosystem> infosystems = captor.getValue();
    assertEquals(2, infosystems.size());
    assertEquals("{\"owner\":\"producer\",\"meta\":{\"URI\":\"/owner/shortname1\"}}", infosystems.get(0).getJson().toString());
    assertEquals("{\"owner\":\"other-producer\",\"meta\":{\"URI\":\"/owner/shortname2\"}}", infosystems.get(1).getJson().toString());
    verify(service).getData("data-url");
    verify(service).getData("other-url");
  }

  @Test
  public void loadDataFromMultipleProducers_takesMostRecentInfosystemData() {
    service.producers.setProperty("data-url", "producer");
    service.producers.setProperty("other-url", "other-producer");

    doReturn("[]").when(service).getApprovalData();
    doReturn("[{\"owner\":\"producer\",\"meta\":{\"URI\":\"/owner/shortname1\"},\"status\":{\"timestamp\":\"2013-11-08T00:00:00.000001\"}}," +
              "{\"owner\":\"producer\",\"meta\":{\"URI\":\"/owner/shortname1\"},\"status\":{\"timestamp\":\"2016-01-01T00:00:00\"}}]")
      .when(service).getData("data-url");
    doReturn("[{\"owner\":\"other-producer\",\"meta\":{\"URI\":\"/owner/shortname1\"},\"status\":{\"timestamp\":\"2013-11-08T00:01:00\"}}]")
      .when(service).getData("other-url");

    service.harvestInfosystems();

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(storageService).save(captor.capture());
    List<Infosystem> infosystems = captor.getValue();
    assertEquals(1, infosystems.size());
    assertEquals(
      "{\"owner\":\"producer\",\"meta\":{\"URI\":\"/owner/shortname1\"},\"status\":{\"timestamp\":\"2016-01-01T00:00:00\"}}",
      infosystems.get(0).getJson().toString());
    verify(service).getData("data-url");
    verify(service).getData("other-url");
  }

  @Test
  public void loadDataFromMultipleProducers_takesOnlyOneInfosystemIfTwoAreEquallyRecent() {
    service.producers.setProperty("data-url", "producer");

    doReturn("[]").when(service).getApprovalData();
    doReturn("[{\"owner\":\"producer\",\"meta\":{\"URI\":\"/owner/shortname1\"},\"status\":{\"timestamp\":\"2016-01-01T00:00:00\"}}," +
      "{\"owner\":\"producer\",\"meta\":{\"URI\":\"/owner/shortname1\"},\"status\":{\"timestamp\":\"2016-01-01T00:00:00\"}}]")
      .when(service).getData("data-url");

    service.harvestInfosystems();

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(storageService).save(captor.capture());
    List<Infosystem> infosystems = captor.getValue();
    assertEquals(1, infosystems.size());
    assertEquals(
      "{\"owner\":\"producer\",\"meta\":{\"URI\":\"/owner/shortname1\"},\"status\":{\"timestamp\":\"2016-01-01T00:00:00\"}}",
      infosystems.get(0).getJson().toString());
  }

  @Test
  public void loadDataFromLegacyProducerAllowingAnyOwner() {
    service.legacyProducerUrl = "legacy-data-url";
    service.producers.setProperty("data-url", "producer,producer3");

    doReturn("[]").when(service).getApprovalData();
    doReturn("[{\"owner\":\"producer2\",\"meta\":{\"URI\":\"/owner/shortname2\"},\"status\":{\"timestamp\":\"2016-01-01T00:00:00\"}}," +
      "{\"owner\":\"producer3\",\"meta\":{\"URI\":\"/owner/shortname3\"},\"status\":{\"timestamp\":\"2016-01-01T00:00:00\"}}]")
      .when(service).getData("data-url");

    doReturn("[{\"owner\":\"producer1\",\"meta\":{\"URI\":\"/owner/shortname1\"},\"status\":{\"timestamp\":\"2016-01-01T00:00:00\"}}," +
      "{\"owner\":\"producer2\",\"meta\":{\"URI\":\"/owner/shortname2\"},\"status\":{\"timestamp\":\"2016-01-01T00:00:00\"}}]")
      .when(service).getData("legacy-data-url");

    service.harvestInfosystems();

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(storageService).save(captor.capture());
    List<Infosystem> infosystems = captor.getValue();
    assertEquals(3, infosystems.size());
    assertEquals(
      "{\"owner\":\"producer1\",\"meta\":{\"URI\":\"/owner/shortname1\"},\"status\":{\"timestamp\":\"2016-01-01T00:00:00\"}}",
      infosystems.get(0).getJson().toString());
    assertEquals(
      "{\"owner\":\"producer2\",\"meta\":{\"URI\":\"/owner/shortname2\"},\"status\":{\"timestamp\":\"2016-01-01T00:00:00\"}}",
      infosystems.get(1).getJson().toString());
    assertEquals(
      "{\"owner\":\"producer3\",\"meta\":{\"URI\":\"/owner/shortname3\"},\"status\":{\"timestamp\":\"2016-01-01T00:00:00\"}}",
      infosystems.get(2).getJson().toString());
  }
}