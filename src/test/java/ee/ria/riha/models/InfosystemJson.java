package ee.ria.riha.models;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain=true)
@Getter
@Setter
public class InfosystemJson {
  String name;
  String shortname;
  String documentation;
  String objective;
  Owner owner;
  Meta meta;
  String uri;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SystemStatus {
    String timestamp;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ApprovalStatus {
    String timestamp;
    String status;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class Owner {
    String code;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class Meta {
    SystemStatus system_status;
    ApprovalStatus approval_status;
  }
}