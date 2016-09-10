package org.thoughtcrime.securesms.database.backup;

import android.provider.Telephony;
import android.provider.Telephony.BaseMmsColumns;
import android.provider.Telephony.TextBasedSmsColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Charsets;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.NotificationMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.BufferedWriter;

public abstract class XmlBackupItem {
  // common signal attributes
//  public static final String READABLE_DATE   = "readable_date";  // SMS Backup & Restore, optional
//  public static final String CONTACT_NAME    = "contact_name";   // SMS Backup & Restore, optional
  public static final String THREAD_ADDRESS  = "thread_address"; // Signal




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

  public XmlBackupItem(@NonNull MessageRecord record, @Nullable String threadAddress) {
    this.address = record.getIndividualRecipient().getNumber();
    this.threadAddress = threadAddress;
    this.date = record.getDateReceived();
    this.dateSent = record.getDateSent();
    this.type = MmsSmsColumns.Types.translateToSystemBaseType(record.getType());
    this.subject = null;
    this.body = record.getDisplayBody().toString();
    this.read = 1;
    this.status = record.isDelivered() ? SmsDatabase.Status.STATUS_COMPLETE : record.getDeliveryStatus();
  }

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
      case THREAD_ADDRESS:
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

  public abstract void storeOn(@NonNull XmlBackupWriter writer) throws IOException;

  public static class Sms extends XmlBackupItem {
    // SMS attribute names
    public static final String TOA = "toa";       // SMS Backup & Restore, optional
    public static final String SC_TOA = "sc_toa"; // SMS Backup & Restore, optional


    // SMS attributes
    protected int protocol;
    protected String serviceCenter;

    public Sms(@NonNull SmsMessageRecord record, @Nullable String threadAddress) {
      super(record, threadAddress);
    }

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

    @Override
    public void storeOn(@NonNull XmlBackupWriter writer) throws IOException {
      writer.openSMS();

      writer.storeAttribute(TextBasedSmsColumns.PROTOCOL, protocol);
      writer.storeAttribute(TextBasedSmsColumns.ADDRESS, address);
      if (threadAddress != null && !threadAddress.equals(address)) {
        // aka group address
        writer.storeAttribute(THREAD_ADDRESS, threadAddress);
      }
      writer.storeAttribute(TextBasedSmsColumns.DATE, date);
      writer.storeAttribute(TextBasedSmsColumns.DATE_SENT, dateSent);
      writer.storeAttribute(TextBasedSmsColumns.TYPE, type);
      writer.storeAttribute(TextBasedSmsColumns.SUBJECT, subject);
      writer.storeAttribute(TextBasedSmsColumns.BODY, body);
      writer.storeAttribute(TextBasedSmsColumns.SERVICE_CENTER, serviceCenter);
      writer.storeAttribute(TextBasedSmsColumns.READ, read);
      writer.storeAttribute(TextBasedSmsColumns.STATUS, status);

      writer.closeSMS();
    }
  }



  public static class Mms extends XmlBackupItem {

    // Part attribute names
    public static final String DISPLAY_NAME = "_display_name"; // SMS Backup & Restore, optional
    public static final String SIZE         = "_size";         // SMS Backup & Restore, optional
    public static final String DATA         = "data";          // SMS Backup & Restore, optional



    // character sets
    public static final int UTF_8 = 106;

    // MMS attributes
    private byte[] contentLocation;
    private long expiry          = -1;  // TODO: check meaning of expiry long value
    private long messageSize     = 0;
    private int partCount        = 1;
    private int subscriptionId   = -1;
    private byte[] transactionId = null;


    public Mms(@NonNull MediaMmsMessageRecord record, @Nullable String threadAddress) {
      super(record, threadAddress);
      this.partCount = record.getPartCount();
//      this.slideDeck = record.getSlideDeck();
    }

    public Mms(@NonNull NotificationMmsMessageRecord record, @Nullable String threadAddress) {
      super(record, threadAddress);
      this.contentLocation = record.getContentLocation();
      this.messageSize     = record.getMessageSize();
      this.expiry          = record.getExpiration();
//      this.status          = record.getStatus(); TODO: check status type of MMS
      this.subscriptionId = record.getSubscriptionId();
      this.transactionId   = record.getTransactionId();
    }

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

    @Override
    public void storeOn(@NonNull XmlBackupWriter writer) throws IOException {
      writer.startMMS();

      writer.storeAttribute(BaseMmsColumns.TEXT_ONLY, 1);              // optional
      writer.storeAttribute(TextBasedSmsColumns.ADDRESS, address);
      if (threadAddress != null && !threadAddress.equals(address)) {
        // aka group address
        writer.storeAttribute(THREAD_ADDRESS, threadAddress);
      }
      writer.storeAttribute(TextBasedSmsColumns.DATE, date);
      writer.storeAttribute(TextBasedSmsColumns.DATE_SENT, dateSent);
      writer.storeAttribute(BaseMmsColumns.MESSAGE_BOX, type);
      writer.storeAttribute(BaseMmsColumns.SUBJECT, subject);
      writer.storeAttribute(TextBasedSmsColumns.READ, read);
      writer.storeAttribute(BaseMmsColumns.STATUS, status);

//      writer.storeAttribute(BaseMmsColumns.CONTENT_CLASS, null);
//      writer.storeAttribute(BaseMmsColumns.SUBJECT_CHARSET, 106);
//      writer.storeAttribute(BaseMmsColumns.RETRIEVE_STATUS, "128");
      writer.storeAttribute(BaseMmsColumns.CONTENT_LOCATION, getContentLocation());
      writer.storeAttribute(BaseMmsColumns.TRANSACTION_ID, getTransactionId());
//      writer.storeAttribute(BaseMmsColumns.MESSAGE_CLASS, "personal");
//      writer.storeAttribute(BaseMmsColumns.DELIVERY_TIME, null);
//      writer.storeAttribute(BaseMmsColumns.READ_STATUS, null);
//      writer.storeAttribute(BaseMmsColumns.CONTENT_TYPE, "application/vnd.wap.multipart.related");
//      writer.storeAttribute(BaseMmsColumns.SUBSCRIPTION_ID, 0);
//      writer.storeAttribute(BaseMmsColumns.RETRIEVE_TEXT_CHARSET, null);
//      writer.storeAttribute(BaseMmsColumns.DELIVERY_REPORT, 128);
//      writer.storeAttribute(BaseMmsColumns.MESSAGE_ID, "NOKASDF0000900002");
//      writer.storeAttribute(BaseMmsColumns.SEEN, 1);
//      writer.storeAttribute(BaseMmsColumns.MESSAGE_TYPE, 132);
//      writer.storeAttribute(BaseMmsColumns.MMS_VERSION, 17);
      writer.storeAttribute(BaseMmsColumns.EXPIRY, expiry);
//      writer.storeAttribute(BaseMmsColumns.PRIORITY, 129);
//      writer.storeAttribute(BaseMmsColumns.READ_REPORT, 129);
//      writer.storeAttribute(BaseMmsColumns.RESPONSE_TEXT, null);
//      writer.storeAttribute(BaseMmsColumns.REPORT_ALLOWED, null);
//      writer.storeAttribute(BaseMmsColumns.LOCKED, 0);
//      writer.storeAttribute(BaseMmsColumns.RETRIEVE_TEXT, null);
//      writer.storeAttribute(BaseMmsColumns.RESPONSE_STATUS, null);
      writer.storeAttribute(BaseMmsColumns.MESSAGE_SIZE, messageSize);
//      writer.storeAttribute(BaseMmsColumns.CREATOR, null);
//      writer.storeAttribute(READABLE_DATE, "08.03.3588 12:54:30");
//      writer.storeAttribute(CONTACT_NAME, "Hook");

      // store message text only
      writer.startParts();
      writer.startPart();

      writer.storeAttribute(Telephony.Mms.Part.SEQ, 0);
      writer.storeAttribute(Telephony.Mms.Part.CONTENT_TYPE, "text/plain");
      writer.storeAttribute(Telephony.Mms.Part.NAME, null);
      writer.storeAttribute(Telephony.Mms.Part.CHARSET, UTF_8);
      writer.storeAttribute(Telephony.Mms.Part.CONTENT_DISPOSITION, null);
      writer.storeAttribute(Telephony.Mms.Part.FILENAME, null);
      writer.storeAttribute(Telephony.Mms.Part.CONTENT_ID, "&lt;text_0&gt;"); // should be int?
      writer.storeAttribute(Telephony.Mms.Part.CONTENT_LOCATION, "text_0.txt");      // should be int?
      writer.storeAttribute(Telephony.Mms.Part.CT_START, null);
      writer.storeAttribute(Telephony.Mms.Part.CT_TYPE, null);
      writer.storeAttribute(Telephony.Mms.Part.TEXT, body);
//      writer.storeAttribute(DISPLAY_NAME, null);
//      writer.storeAttribute(SIZE, null);

      writer.closePart();
      writer.closeParts();
      writer.closeMMS();
    }
  }
}
