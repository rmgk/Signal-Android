package org.thoughtcrime.securesms.database.backup;

import android.provider.Telephony.TextBasedSmsColumns;
import android.support.annotation.NonNull;
import org.xmlpull.v1.XmlPullParser;

import java.util.HashMap;
import java.util.Map;

public abstract class BackupItem {



  // common attributes
  protected String address;
  protected long date;
  protected long dateSent;
  protected String subject;
  protected String body;
  protected int read;
  protected Long signalType;
  protected String signalGroupAddress;

  public Map<String, String> attributes = new HashMap<>();



  public BackupItem(@NonNull XmlPullParser parser) {
    for (int i = 0, count = parser.getAttributeCount(); i < count; i++) {
      attributes.put(parser.getAttributeName(i), parser.getAttributeValue(i));
      readAttribute(parser, i);
    }
  }

  protected void readAttribute(@NonNull XmlPullParser parser, int i) {
    switch (parser.getAttributeName(i)) {
      case TextBasedSmsColumns.ADDRESS:
        address = parser.getAttributeValue(i);
        break;
      case TextBasedSmsColumns.DATE:
        date = Long.parseLong(parser.getAttributeValue(i));
        break;
      case TextBasedSmsColumns.DATE_SENT:
        dateSent = Long.parseLong(parser.getAttributeValue(i));
        break;
      case TextBasedSmsColumns.READ:
        read = Integer.parseInt(parser.getAttributeValue(i));
        break;
      case XmlBackupWriter.SIGNAL_TYPE:
        signalType = Long.parseLong(parser.getAttributeValue(i));
        break;
      case XmlBackupWriter.SIGNAL_GROUP_ADDRESS:
        signalGroupAddress = parser.getAttributeValue(i);
        break;
      default:
        // ignore unkown attributes
    }
  }

  public String getAddress() {
    return address;
  }


  public long getDate() {
    return date;
  }

  public long getDateSent() {
    return dateSent;
  }


  public String getSubject() {
    return subject;
  }

  public String getBody() { return body; }

  public int getRead() {
    return read;
  }

  public String getSignalGroupAddress() {
    return signalGroupAddress;
  }

  public Long getSignalType() {
    return signalType;
  }

  public abstract int getType();
}
