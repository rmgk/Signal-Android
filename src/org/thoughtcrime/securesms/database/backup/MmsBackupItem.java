package org.thoughtcrime.securesms.database.backup;

import android.provider.Telephony;
import android.support.annotation.NonNull;
import org.thoughtcrime.securesms.database.MmsAddresses;
import org.whispersystems.libsignal.logging.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import ws.com.google.android.mms.pdu.PduHeaders;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MmsBackupItem extends BackupItem {

  private static final String LOG = MmsBackupItem.class.getSimpleName();

  // MMS attributes
  protected String subject;
  private int msgBox;
  private String senderFROM;
  private List<String> receiversTO = new ArrayList<>(10);
  private List<String> receiversCC = new ArrayList<>(0);
  private List<String> receiversBCC = new ArrayList<>(0);
  public List<Map<String, String>> parts = new ArrayList<>(1);



  public MmsBackupItem(@NonNull XmlPullParser parser) throws IOException, XmlPullParserException {
    super(parser);
    while ((parser.next() != XmlPullParser.END_TAG) || !parser.getName().equals("mms")) {
      if (parser.getEventType() != XmlPullParser.START_TAG) {
        continue;
      }
      Log.d("MmsBackupItem", "processing line " + parser.getLineNumber());
      if (parser.getName().equalsIgnoreCase("part")) {
          readPart(parser);
      }
      if (parser.getName().equalsIgnoreCase("addr")) {
        readAddress(parser);
      }
    }
  }

  private void readAddress(@NonNull XmlPullParser parser) {
    String address = parser.getAttributeValue(null, Telephony.Mms.Addr.ADDRESS);
    switch (Integer.parseInt(parser.getAttributeValue(null, Telephony.Mms.Addr.TYPE))) {
      case PduHeaders.FROM:
        senderFROM = address;
        break;
      case PduHeaders.TO:
        receiversTO.add(address);
        break;
      case PduHeaders.CC:
        receiversCC.add(address);
        break;
      case PduHeaders.BCC:
        receiversBCC.add(address);
        break;
      default:
        Log.w(LOG, "unknown address header while parsing: " + parser.getLineNumber());
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

  protected void readPart(@NonNull XmlPullParser parser) {
    String mime = parser.getAttributeValue(null, Telephony.Mms.Part.CONTENT_TYPE);
    String text = parser.getAttributeValue(null, Telephony.Mms.Part.TEXT);
    String data = parser.getAttributeValue(null, "data");
    if ((mime != null) && (text != null) && mime.equals("text/plain") && body == null) {
      body = text;
    }
    else if (data != null && mime != null && !mime.equals("application/smil")) {
      Log.i(LOG, "importing part " + parser.getLineNumber() + " " + mime);
      Map<String, String> partAttribuse = new HashMap<>();
      for (int i = 0, count = parser.getAttributeCount(); i < count; i++) {
        partAttribuse.put(parser.getAttributeName(i), parser.getAttributeValue(i));
      }
      parts.add(partAttribuse);
    }
  }


  public int getPartCount() {
    return parts.size();
  }

  public int getMessageBox() {
    return msgBox;
  }

  @Override
  public int getType() {
    return getMessageBox();
  }

  public MmsAddresses getAddresses() {
    return new MmsAddresses(senderFROM, receiversTO, receiversCC, receiversBCC);
  }
}
