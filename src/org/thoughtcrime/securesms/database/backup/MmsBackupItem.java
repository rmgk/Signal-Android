package org.thoughtcrime.securesms.database.backup;

import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.google.common.base.Charsets;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import ws.com.google.android.mms.pdu.PduHeaders;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MmsBackupItem extends BackupItem {

  // MMS attributes
  private int partCount        = 1;
  protected String subject;
  private int msgBox;
  public String sender;
  public List<String> receivers = new ArrayList<>(10);



  public MmsBackupItem(@NonNull XmlPullParser parser) throws IOException, XmlPullParserException {
    super(parser);
    while (parser.next() != XmlPullParser.END_TAG) {
      if (parser.getEventType() != XmlPullParser.START_TAG) {
        continue;
      }
      if (parser.getName().equalsIgnoreCase("part")) {
        if (body == null) {
          // read first text part only
          readTextOnlyPart(parser);
        }
      }
      if (parser.getName().equalsIgnoreCase("addr")) {
        readAddress(parser);
      }
    }
  }

  private void readAddress(@NonNull XmlPullParser parser) {
    String address = parser.getAttributeValue(null, Telephony.Mms.Addr.ADDRESS);
    if (parser.getAttributeValue(null, Telephony.Mms.Addr.TYPE).equals(PduHeaders.FROM)) {
      sender = address;
    }
    else {
      receivers.add(address);
    }
  }

  @Override
  protected void readAttribute(@NonNull XmlPullParser parser, int i) {
    switch (parser.getAttributeName(i)) {
      case Telephony.BaseMmsColumns.SUBJECT:
        subject = parser.getAttributeValue(i);
        break;

      case Telephony.BaseMmsColumns.MESSAGE_BOX:
        msgBox = Integer.parseInt(parser.getAttributeValue(i));
        break;
      default:
        super.readAttribute(parser, i);
    }
  }

  protected void readTextOnlyPart(@NonNull XmlPullParser parser) {
    String mime = null;
    String text = null;
    for (int i=0, count=parser.getAttributeCount(); i<count; i++) {
      switch (parser.getAttributeName(i)) {
        case Telephony.Mms.Part.CONTENT_TYPE:
          mime = parser.getAttributeValue(i);
          break;
        case Telephony.Mms.Part.TEXT:
          text = parser.getAttributeValue(i);
          break;
        default:
          // ignore other attributes
      }
      if (mime != null && text != null) {
        if (mime.equals("text/plain")) {
          // TODO deal with encoding: CHSET
          body = text;
        }
        break;
      }
    }
  }


  public int getPartCount() {
    return partCount;
  }

  public int getMessageBox() {
    return msgBox;
  }

  @Override
  public int getType() {
    return getMessageBox();
  }
}
