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
import org.skyscreamer.jsonassert.JSONAssert;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

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
  public void addApprovalData() throws Exception {
    service.producers.setProperty("data-url", "producer");

    doReturn("[{\"uri\":\"http://base.url/shortname1\",\"timestamp\":\"2016-01-01T10:00:00\",\"status\":\"MITTE KOOSKÕLASTATUD\"}," +
      "{\"uri\":\"http://base.url/shortname2\",\"timestamp\":\"2015-10-10T01:10:10\",\"status\":\"KOOSKÕLASTATUD\"}]")
      .when(service).getApprovalData();

    String infosystemsData = array(json("producer", "http://base.url/shortname1", ""), json("producer", "/70000740/\\u00d5ppurite register", ""));
    doReturn(infosystemsData).when(service).getDataAsJsonArray("data-url");

    service.harvestInfosystems();

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(storageService).save(captor.capture());

    List<Infosystem> infosystems = captor.getValue();
    assertEquals(2, infosystems.size());

    JSONAssert.assertEquals(
      json("producer", "http://base.url/shortname1", "", "MITTE KOOSKÕLASTATUD", "2016-01-01T10:00:00"),
      infosystems.get(0).getJson().toString(), true);

    JSONAssert.assertEquals(json("producer", "/70000740/\\u00d5ppurite register", ""),
      infosystems.get(1).getJson().toString(), true);
  }

  @Test
  public void loadDataFromMultipleProducers() throws Exception {
    service.producers.setProperty("data-url", "producer");
    service.producers.setProperty("other-url", "other-producer");

    doReturn("[]").when(service).getApprovalData();
    doReturn(array(json("producer","http://base.url/shortname1", ""))).when(service).getDataAsJsonArray("data-url");
    doReturn(array(json("other-producer","http://base.url/shortname2", ""))).when(service).getDataAsJsonArray("other-url");

    service.harvestInfosystems();

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(storageService).save(captor.capture());
    List<Infosystem> infosystems = captor.getValue();
    assertEquals(2, infosystems.size());
    JSONAssert.assertEquals(json("producer","http://base.url/shortname1", ""), infosystems.get(0).getJson().toString(), true);
    JSONAssert.assertEquals(json("other-producer","http://base.url/shortname2", ""), infosystems.get(1).getJson().toString(), true);
    verify(service).getDataAsJsonArray("data-url");
    verify(service).getDataAsJsonArray("other-url");
  }

  @Test
  public void loadDataFromMultipleProducers_takesMostRecentInfosystemData() throws Exception {
    service.producers.setProperty("data-url", "producer");
    service.producers.setProperty("other-url", "other-producer");

    doReturn("[]").when(service).getApprovalData();
    String expectedResult = json("producer", "http://base.url/shortname1", "2016-09-05T00:36:26.255215");
    doReturn(array(json("producer", "http://base.url/shortname1", "2015-09-05T00:36:26.255215"), expectedResult))
      .when(service).getDataAsJsonArray("data-url");
    doReturn(array(json("other-producer","http://base.url/shortname1","2011-09-05T00:36:26.255215")))
      .when(service).getDataAsJsonArray("other-url");

    service.harvestInfosystems();

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(storageService).save(captor.capture());
    List<Infosystem> infosystems = captor.getValue();
    assertEquals(1, infosystems.size());
    JSONAssert.assertEquals(expectedResult, infosystems.get(0).getJson().toString(), true);
    verify(service).getDataAsJsonArray("data-url");
    verify(service).getDataAsJsonArray("other-url");
  }

  private String array(String... objects) {
    return "[" + stream(objects).collect(Collectors.joining(",")) + "]";
  }

  private String json(final String ownerCode, final String uri, final String statusTimestamp, final String approvalStatus, final String approvalTimestamp) {
    String approvalJson = approvalStatus == null && approvalTimestamp == null ? "" :
      (",\"approval_status\": {\"status\":\""+approvalStatus+"\",\"timestamp\": \"" + approvalTimestamp + "\"}");
    return "{\"owner\":{\"code\":\"" + ownerCode + "\"},\"uri\":\"" + uri + "\"," +
      "\"meta\": {" +
        "\"system_status\": {\"timestamp\": \"" + statusTimestamp + "\"}" +
        approvalJson +
      "}}";
  }

  private String json(String ownerCode, String uri, String statusTimestamp) {
    return json(ownerCode, uri, statusTimestamp, null, null);
  }

  @Test
  public void loadDataFromMultipleProducers_takesOnlyOneInfosystemIfTwoAreEquallyRecent() throws Exception {
    service.producers.setProperty("data-url", "producer");

    doReturn("[]").when(service).getApprovalData();
    doReturn(array(json("producer", "http://base.url/shortname1", "2016-01-01T00:00:00"), json("producer", "http://base.url/shortname1", "2016-01-01T00:00:00")))
      .when(service).getDataAsJsonArray("data-url");

    service.harvestInfosystems();

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(storageService).save(captor.capture());
    List<Infosystem> infosystems = captor.getValue();
    assertEquals(1, infosystems.size());
    JSONAssert.assertEquals(
      json("producer", "http://base.url/shortname1", "2016-01-01T00:00:00"),
      infosystems.get(0).getJson().toString(), true);
  }

  @Test
  public void loadDataFromLegacyProducerAllowingAnyOwner() throws Exception {
    service.legacyProducerUrl = "legacy-data-url";
    service.producers.setProperty("data-url", "producer,producer3");

    doReturn("[]").when(service).getApprovalData();

    doReturn(array(json("producer2", "http://base.url/shortname2", "2016-01-01T00:00:00"), json("producer3", "http://base.url/shortname3", "2016-01-01T00:00:00")))
      .when(service).getDataAsJsonArray("data-url");

    doReturn(array(json("producer1", "http://base.url/shortname1", "2016-01-01T00:00:00"), json("producer2", "http://base.url/shortname2", "2016-01-01T00:00:00")))
      .when(service).getDataAsJsonArray("legacy-data-url");

    service.harvestInfosystems();

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(storageService).save(captor.capture());
    List<Infosystem> infosystems = captor.getValue();
    assertEquals(3, infosystems.size());
    JSONAssert.assertEquals(
      json("producer1", "http://base.url/shortname1", "2016-01-01T00:00:00"),
      infosystems.get(0).getJson().toString(), true);
    JSONAssert.assertEquals(
      json("producer2", "http://base.url/shortname2", "2016-01-01T00:00:00"),
      infosystems.get(1).getJson().toString(), true);
    JSONAssert.assertEquals(
      json("producer3", "http://base.url/shortname3", "2016-01-01T00:00:00"),
      infosystems.get(2).getJson().toString(), true);
  }

  @Test(expected = HarvestService.UnreachableResourceException.class)
  public void getDataAsJsonArray_returnsEmptyJsonArrayInCaseOfError() throws HarvestService.UnreachableResourceException {
    service.getDataAsJsonArray("invalid-url");
  }

  @Test
  public void doesNotHarvestInfosystemsIfApprovalsRequestThrowsException() throws Exception {
    doThrow(mock(HarvestService.UnreachableResourceException.class)).when(service).getApprovalData();

    service.harvestInfosystems();

    verify(storageService, never()).save(any());
  }

  @Test
  public void skipsProducerIfUrlIsUnreachable() throws Exception {
    service.producers.setProperty("data-url-ok1", "producer1");
    service.producers.setProperty("data-url-fail", "producer2");
    service.producers.setProperty("data-url-ok2", "producer3");

    doReturn("[]").when(service).getApprovalData();
    doReturn(array(json("producer1", "http://base.url/shortname1", "2016-01-01T00:00:00"))).when(service).getDataAsJsonArray("data-url-ok1");
    doThrow(mock(HarvestService.UnreachableResourceException.class)).when(service).getDataAsJsonArray("data-url-fail");
    doReturn(array(json("producer3", "http://base.url/shortname3", "2016-01-01T00:00:00"))).when(service).getDataAsJsonArray("data-url-ok2");

    service.harvestInfosystems();

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(storageService).save(captor.capture());
    List<Infosystem> infosystems = captor.getValue();
    assertEquals(2, infosystems.size());
    JSONAssert.assertEquals(
      json("producer1", "http://base.url/shortname1", "2016-01-01T00:00:00"),
      infosystems.get(0).getJson().toString(), true);
    JSONAssert.assertEquals(
      json("producer3", "http://base.url/shortname3", "2016-01-01T00:00:00"),
      infosystems.get(1).getJson().toString(), true);
  }
}