package ee.ria.riha.service;

import ee.ria.riha.models.Infosystem;
import ee.ria.riha.models.InfosystemJson;
import ee.ria.riha.models.InfosystemJson.ApprovalStatus;
import ee.ria.riha.models.InfosystemJson.Meta;
import ee.ria.riha.models.InfosystemJson.Owner;
import ee.ria.riha.models.InfosystemJson.SystemStatus;
import org.json.JSONArray;
import org.json.JSONObject;
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
import static org.junit.Assert.*;
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

    doNothing().when(service).initProducers();
    doReturn(true).when(service).validateInfosystem(anyString());
    doReturn(new JSONArray("[{\"uri\":\"http://base.url/shortname1\",\"timestamp\":\"2016-01-01T10:00:00\",\"status\":\"MITTE KOOSKÕLASTATUD\"}," +
      "{\"uri\":\"http://base.url/shortname2\",\"timestamp\":\"2015-10-10T01:10:10\",\"status\":\"KOOSKÕLASTATUD\"}]"))
      .when(service).getApprovalData();

    JSONArray infosystemsData = array(json("producer", "http://base.url/shortname1", ""), json("producer", "/70000740/\\u00d5ppurite register", ""));
    doReturn(infosystemsData).when(service).getData("data-url");

    service.harvestInfosystems();

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(storageService).save(captor.capture());

    List<Infosystem> infosystems = captor.getValue();
    assertEquals(2, infosystems.size());

    JSONAssert.assertEquals(
      json("producer", "http://base.url/shortname1", "", "MITTE KOOSKÕLASTATUD", "2016-01-01T10:00:00", null),
      infosystems.get(0).getJson().toString(), true);

    JSONAssert.assertEquals(json("producer", "/70000740/\\u00d5ppurite register", ""),
      infosystems.get(1).getJson().toString(), true);
  }

  @Test
  public void validateInfosystem_requiredFields() {
    assertTrue(service.validateInfosystem(json("ownerCode", "http://base.url/short-name", null, null, null, "short-name")));
    assertFalse(service.validateInfosystem(json(null, "http://base.url/short-name", null, null, null, "short-name")));
    assertFalse(service.validateInfosystem(json("ownerCode", null, null, null, null, "short-name")));
    assertFalse(service.validateInfosystem(json("ownerCode", "http://base.url/short-name", null, null, null, null)));
  }

  @Test
  public void validateInfosystem_usesUtcForDate() {
    assertTrue(service.validateInfosystem(new JSONObject(
      new InfosystemJson()
        .setOwner(new Owner().setCode("ownerCode"))
        .setUri("http://base.url/short-name")
        .setShortname("short-name")
      .setMeta(new Meta().setSystem_status(new SystemStatus().setTimestamp("2016-01-01T10:00:00")))
      ).toString())
    );
  }

  @Test
  public void loadDataFromMultipleProducers() throws Exception {
    service.producers.setProperty("data-url", "producer");
    service.producers.setProperty("other-url", "other-producer");

    doNothing().when(service).initProducers();
    doReturn(true).when(service).validateInfosystem(anyString());
    doReturn(new JSONArray("[]")).when(service).getApprovalData();
    doReturn(array(json("producer","http://base.url/shortname1", ""))).when(service).getData("data-url");
    doReturn(array(json("other-producer","http://base.url/shortname2", ""))).when(service).getData("other-url");

    service.harvestInfosystems();

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(storageService).save(captor.capture());
    List<Infosystem> infosystems = captor.getValue();
    assertEquals(2, infosystems.size());
    JSONAssert.assertEquals(json("producer","http://base.url/shortname1", ""), infosystems.get(0).getJson().toString(), true);
    JSONAssert.assertEquals(json("other-producer","http://base.url/shortname2", ""), infosystems.get(1).getJson().toString(), true);
    verify(service).getData("data-url");
    verify(service).getData("other-url");
  }

  @Test
  public void loadDataFromMultipleProducers_takesMostRecentInfosystemData() throws Exception {
    service.producers.setProperty("data-url", "producer");
    service.producers.setProperty("other-url", "other-producer");

    doNothing().when(service).initProducers();
    doReturn(true).when(service).validateInfosystem(anyString());
    doReturn(new JSONArray("[]")).when(service).getApprovalData();
    String expectedResult = json("producer", "http://base.url/shortname1", "2016-09-05T00:36:26.255215");
    doReturn(array(json("producer", "http://base.url/shortname1", "2015-09-05T00:36:26.255215"), expectedResult))
      .when(service).getData("data-url");
    doReturn(array(json("other-producer","http://base.url/shortname1","2011-09-05T00:36:26.255215")))
      .when(service).getData("other-url");

    service.harvestInfosystems();

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(storageService).save(captor.capture());
    List<Infosystem> infosystems = captor.getValue();
    assertEquals(1, infosystems.size());
    JSONAssert.assertEquals(expectedResult, infosystems.get(0).getJson().toString(), true);
    verify(service).getData("data-url");
    verify(service).getData("other-url");
  }

  @Test
  public void loadDataFromMultipleProducers_takesOnlyOneInfosystemIfTwoAreEquallyRecent() throws Exception {
    service.producers.setProperty("data-url", "producer");

    doNothing().when(service).initProducers();
    doReturn(true).when(service).validateInfosystem(anyString());
    doReturn(new JSONArray("[]")).when(service).getApprovalData();
    doReturn(array(json("producer", "http://base.url/shortname1", "2016-01-01T00:00:00"), json("producer", "http://base.url/shortname1", "2016-01-01T00:00:00")))
      .when(service).getData("data-url");

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

    doNothing().when(service).initProducers();
    doReturn(true).when(service).validateInfosystem(anyString());
    doReturn(new JSONArray("[]")).when(service).getApprovalData();
    doReturn(array(json("producer2", "http://base.url/shortname2", "2016-01-01T00:00:00"), json("producer3", "http://base.url/shortname3", "2016-01-01T00:00:00")))
      .when(service).getData("data-url");
    doReturn(array(json("producer1", "http://base.url/shortname1", "2016-01-01T00:00:00"), json("producer2", "http://base.url/shortname2", "2016-01-01T00:00:00")))
      .when(service).getData("legacy-data-url");

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
  public void getDataAsJsonArray_resourcetIsUnreachable() throws HarvestService.UnreachableResourceException {
    service.getData("invalid-url");
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

    doNothing().when(service).initProducers();
    doReturn(true).when(service).validateInfosystem(anyString());
    doReturn(new JSONArray("[]")).when(service).getApprovalData();
    doReturn(array(json("producer1", "http://base.url/shortname1", "2016-01-01T00:00:00"))).when(service).getData("data-url-ok1");
    doThrow(mock(HarvestService.UnreachableResourceException.class)).when(service).getData("data-url-fail");
    doReturn(array(json("producer3", "http://base.url/shortname3", "2016-01-01T00:00:00"))).when(service).getData("data-url-ok2");

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

  private JSONArray array(String... objects) {
    return new JSONArray("[" + stream(objects).collect(Collectors.joining(",")) + "]");
  }

  private String json(String ownerCode, String uri, String statusTimestamp) {
    return json(ownerCode, uri, statusTimestamp, null, null, null);
  }

  private String json(String ownerCode, String uri, String statusTimestamp, String approvalStatus, String approvalTimestamp, String shortName) {
    InfosystemJson json = new InfosystemJson();

    if (approvalStatus != null || approvalTimestamp != null || statusTimestamp != null) {
      json.setMeta(new Meta());

      if (approvalTimestamp != null || approvalStatus != null)
        json.getMeta().setApproval_status(new ApprovalStatus().setTimestamp(approvalTimestamp).setStatus(approvalStatus));
      if (statusTimestamp != null)
        json.getMeta().setSystem_status(new SystemStatus().setTimestamp(statusTimestamp));
    }

    if (ownerCode != null) {
      json.setOwner(new Owner().setCode(ownerCode));
    }

    json.setUri(uri);
    json.setShortname(shortName);

    return new JSONObject(json).toString();
  }
}