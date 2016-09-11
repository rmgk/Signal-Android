package org.thoughtcrime.securesms.database.backup;

import android.provider.Telephony;
import android.provider.Telephony.BaseMmsColumns;
import android.provider.Telephony.TextBasedSmsColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Charsets;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.NotificationMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

public abstract class XmlBackupItem {



  // common attributes
  protected String address;
  protected String threadAddress;
  protected long date;
  protected long dateSent;
  protected int type;
  protected String subject;
  protected String body;
  protected int read;
  protected int status;

  public XmlBackupItem(@NonNull XmlPullParser parser) {
    for (int i = 0, count = parser.getAttributeCount(); i < count; i++) {
      readAttribute(parser, i);
    }
  }

  protected void readAttribute(@NonNull XmlPullParser parser, int i) {
    switch (parser.getAttributeName(i)) {
      case TextBasedSmsColumns.ADDRESS:
        address = parser.getAttributeValue(i);
        break;
      case XmlBackupWriter.SIGNAL_GROUP_ADDRESS:
        threadAddress = parser.getAttributeValue(i);
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
      default:
        // ignore unkown attributes
    }
  }

  public String getAddress() {
    return address;
  }

  public String getThreadAddress() {
    return threadAddress;
  }

  public long getDate() {
    return date;
  }

  public long getDateSent() {
    return dateSent;
  }

  public int getType() {
    return type;
  }

  public String getSubject() {
    return subject;
  }

  public String getBody() { return body; }

  public int getRead() {
    return read;
  }

  public int getStatus() {
    return status;
  }

  public static class Sms extends XmlBackupItem {
    // SMS attribute names
    public static final String TOA = "toa";       // SMS Backup & Restore, optional
    public static final String SC_TOA = "sc_toa"; // SMS Backup & Restore, optional


    // SMS attributes
    protected int protocol;
    protected String serviceCenter;

    public Sms(@NonNull XmlPullParser parser) {
      super(parser);
    }

    @Override
    protected void readAttribute(@NonNull XmlPullParser parser, int i) {
      switch (parser.getAttributeName(i)) {
        case TextBasedSmsColumns.STATUS:
          status = Integer.parseInt(parser.getAttributeValue(i));
          break;
        case TextBasedSmsColumns.PROTOCOL:
          protocol = Integer.parseInt(parser.getAttributeValue(i));
          break;
        case TextBasedSmsColumns.TYPE:
          type = Integer.parseInt(parser.getAttributeValue(i));
          break;
        case TextBasedSmsColumns.SUBJECT:
          subject = parser.getAttributeValue(i);
          break;
        case TextBasedSmsColumns.BODY:
          body = parser.getAttributeValue(i);
          break;
        case TextBasedSmsColumns.SERVICE_CENTER:
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

  }



  public static class Mms extends XmlBackupItem {






    // MMS attributes
    private byte[] contentLocation;
    private long expiry          = -1;  // TODO: check meaning of expiry long value
    private long messageSize     = 0;
    private int partCount        = 1;
    private int subscriptionId   = -1;
    private byte[] transactionId = null;


    public Mms(@NonNull XmlPullParser parser) throws IOException, XmlPullParserException {
      super(parser);
      while (parser.next() != XmlPullParser.END_TAG) {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
          continue;
        }
        if (!parser.getName().equalsIgnoreCase("part")) {
          continue;
        }
        if (body == null) {
          // read first text part only
          readTextOnlyPart(parser);
        }
      }
    }

    @Override
    protected void readAttribute(@NonNull XmlPullParser parser, int i) {
      switch (parser.getAttributeName(i)) {
        case BaseMmsColumns.SUBJECT:
          subject = parser.getAttributeValue(i);
          break;
        case BaseMmsColumns.STATUS:
          status = Integer.parseInt(parser.getAttributeValue(i));
          break;
        case BaseMmsColumns.CONTENT_LOCATION:
          contentLocation = null; // TODO: convert to byte array
          break;
        case BaseMmsColumns.EXPIRY:
          expiry = Long.parseLong(parser.getAttributeValue(i));
          break;
        case BaseMmsColumns.MESSAGE_SIZE:
          messageSize = Long.parseLong(parser.getAttributeValue(i));
          break;
        case BaseMmsColumns.SUBSCRIPTION_ID:
          subscriptionId = Integer.parseInt(parser.getAttributeValue(i));
          break;
        case BaseMmsColumns.TRANSACTION_ID:
          transactionId = null;  // TODO: convert to byte array
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

    @Nullable
    public String getContentLocation() {
      return (contentLocation == null) ? null : new String(contentLocation, Charsets.UTF_8);
    }

    public long getMessageSize() {
      return messageSize;
    }
    public long getExpiry() {
      return expiry;
    }

    public int getPartCount() {
      return partCount;
    }

    public int getSubscriptionId() {
      return subscriptionId;
    }

    @Nullable
    public String getTransactionId() {
      return (transactionId == null) ? null : new String(transactionId, Charsets.UTF_8);
    }
  }
}
