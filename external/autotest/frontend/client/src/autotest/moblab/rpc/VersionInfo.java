package autotest.moblab.rpc;

/**
 * The version information RPC entity.
 */
import com.google.gwt.json.client.JSONObject;

public class VersionInfo extends JsonRpcEntity {

  private static final String NO_MILESTONE_FOUND = "NO MILESTONE FOUND";
  private static final String NO_VERSION_FOUND = "NO VERSION FOUND";
  private static final String NO_TRACK_FOUND = "NO TRACK FOUND";
  private static final String NO_DESCRIPTION_FOUND = "NO DESCRIPTION FOUND";
  private static final String NO_ID_FOUND = "NO ID FOUND";
  private static final String NO_MAC_ADDRESS_FOUND = "NO MAC ADDRESS FOUND";

  private String milestoneInfo;
  private String versionInfo;
  private String releaseTrack;
  private String releaseDescription;
  private String moblabIdentification;
  private String moblabMacAddress;

  public VersionInfo() { reset(); }

  public String getVersion() {
    return new StringBuilder("R").append(milestoneInfo).append("-").append(versionInfo).toString();
  }
  public String getReleaseTrack() { return releaseTrack; }
  public String getReleaseDescription() { return releaseDescription; }
  public String getMoblabIdentification() { return moblabIdentification; }
  public String getMoblabMacAddress() { return moblabMacAddress; }

  private void reset() {
    milestoneInfo = new String(NO_MILESTONE_FOUND);
    versionInfo = new String(NO_VERSION_FOUND);
    releaseTrack = new String(NO_TRACK_FOUND);
    releaseDescription = new String(NO_DESCRIPTION_FOUND);
    moblabIdentification = new String(NO_ID_FOUND);
    moblabMacAddress = new String(NO_MAC_ADDRESS_FOUND);
  }

  @Override
  public void fromJson(JSONObject object) {
    reset();
    milestoneInfo = getStringFieldOrDefault(object, "CHROMEOS_RELEASE_CHROME_MILESTONE",
        NO_MILESTONE_FOUND).trim();
    versionInfo = getStringFieldOrDefault(object, "CHROMEOS_RELEASE_VERSION",
        NO_VERSION_FOUND).trim();
    releaseTrack = getStringFieldOrDefault(object, "CHROMEOS_RELEASE_TRACK",
        NO_TRACK_FOUND).trim();
    releaseDescription = getStringFieldOrDefault(object, "CHROMEOS_RELEASE_DESCRIPTION",
        NO_DESCRIPTION_FOUND).trim();
    moblabIdentification = getStringFieldOrDefault(object, "MOBLAB_ID",
        NO_DESCRIPTION_FOUND).trim();
    moblabMacAddress = getStringFieldOrDefault(object, "MOBLAB_MAC_ADDRESS",
        NO_DESCRIPTION_FOUND).trim();
  }

  @Override
  public JSONObject toJson() {
    // Required override - but this is a read only UI so the write function does not need to be
    // implemented.
    return new JSONObject();
  }
}

