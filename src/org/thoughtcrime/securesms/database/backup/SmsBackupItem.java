package org.thoughtcrime.securesms.database.backup;

import android.provider.Telephony;
import android.support.annotation.NonNull;
import org.xmlpull.v1.XmlPullParser;

public class SmsBackupItem extends BackupItem {
  // SMS attribute names
  public static final String TOA = "toa";       // SMS Backup & Restore, optional
  public static final String SC_TOA = "sc_toa"; // SMS Backup & Restore, optional


  // SMS attributes
  protected int protocol;
  protected String serviceCenter;
  protected int type;
  protected int deliveryStatus;


  public SmsBackupItem(@NonNull XmlPullParser parser) {
    super(parser);
  }

  @Override
  protected void readAttribute(@NonNull XmlPullParser parser, int i) {
    switch (parser.getAttributeName(i)) {
      case Telephony.TextBasedSmsColumns.STATUS:
        deliveryStatus = Integer.parseInt(parser.getAttributeValue(i));
        break;
      case Telephony.TextBasedSmsColumns.PROTOCOL:
        protocol = Integer.parseInt(parser.getAttributeValue(i));
        break;
      case Telephony.TextBasedSmsColumns.TYPE:
        type = Integer.parseInt(parser.getAttributeValue(i));
        break;
      case Telephony.TextBasedSmsColumns.SUBJECT:
        subject = parser.getAttributeValue(i);
        break;
      case Telephony.TextBasedSmsColumns.BODY:
        body = parser.getAttributeValue(i);
        break;
      case Telephony.TextBasedSmsColumns.SERVICE_CENTER:
        serviceCenter = parser.getAttributeValue(i);
        break;
      default:
        super.readAttribute(parser, i);
    }
  }

  public int getProtocol() {
    return protocol;
  }

  public String getServiceCenter() {
    return serviceCenter;
  }

  public int getType() {
    return type;
  }

  public int getDeliveryStatus() {
    return deliveryStatus;
  }
}
