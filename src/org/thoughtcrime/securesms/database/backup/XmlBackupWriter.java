package org.thoughtcrime.securesms.database.backup;

import android.content.Context;
import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.logging.Log;
import ws.com.google.android.mms.pdu.PduHeaders;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmlBackupWriter {

  private static final String LOG = XmlBackupWriter.class.getSimpleName();

  private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>";
  private static final String CREATED_BY = "<!-- File Created By Signal -->";
  private static final String OPEN_TAG_SMSES = "<smses count=\"%d\">";
  private static final String CLOSE_TAG_SMSES = "</smses>";

  // XML tags
  public static final String CLOSE_TAG = ">";
  public static final String CLOSE_EMPTY_TAG = "/>";
  public static final String OPEN_ATTRIBUTE = "=\"";
  public static final String CLOSE_ATTRIBUTE = "\" ";

  // XML tags
  public static final String OPEN_TAG_SMS = " <sms ";

  // XML tags
  public static final String OPEN_TAG_MMS = " <mms ";
  public static final String CLOSE_TAG_MMS = " </mms>";
  public static final String OPEN_TAG_PARTS = "   <parts>";
  public static final String CLOSE_TAG_PARTS = "   </parts>";
  public static final String OPEN_TAG_PART = "    <part ";
  public static final String OPEN_TAG_ADDRESS = "    <addr ";
  public static final String OPEN_TAG_ADDRESSES = "   <addrs>";
  public static final String CLOSE_TAG_ADDRESSES = "   </addrs>";

  // common signal attributes
//  public static final String READABLE_DATE   = "readable_date";  // SMS Backup & Restore, optional
//  public static final String CONTACT_NAME    = "contact_name";   // SMS Backup & Restore, optional
  public static final String SIGNAL_GROUP_ADDRESS = "signal_group_address"; // Signal
  public static final String SIGNAL_TYPE = "signal_type"; // Signal

  // Part attribute names
  public static final String DISPLAY_NAME = "_display_name"; // SMS Backup & Restore, optional
  public static final String SIZE = "_size";         // SMS Backup & Restore, optional
  public static final String DATA = "data";          // SMS Backup & Restore, optional


  // character sets
  public static final int UTF_8 = 106;


  // XML escaping
  public static final Pattern PATTERN = Pattern.compile("[^\u0020-\uD7FF]");

  private final BufferedWriter writer;
  private final ThreadDatabase threads;
  private final Context context;
  private final MasterSecret masterSecret;

  public XmlBackupWriter(BufferedWriter writer, @NonNull Context context, @NonNull MasterSecret masterSecret) {
    this.writer = writer;
    this.context = context;
    this.masterSecret = masterSecret;
    this.threads = DatabaseFactory.getThreadDatabase(context);
  }

  public void writeHeader(int count) throws IOException {
    writer.write(XML_HEADER);
    writer.newLine();
    writer.write(CREATED_BY);
    writer.newLine();
    writer.write(String.format(OPEN_TAG_SMSES, count));
    writer.newLine();
  }

  public void close() throws IOException {
    writer.write(CLOSE_TAG_SMSES);
    writer.close();
  }

  public void storeAttribute(@NonNull String name, String value) throws IOException {
    writer.write(name);
    writer.write(OPEN_ATTRIBUTE);
    writer.write(String.valueOf(escapeXML(value)));
    writer.write(CLOSE_ATTRIBUTE);
  }

  public void storeAttribute(@NonNull String name, boolean value) throws IOException {
    storeAttribute(name, value ? "1" : "0");
  }


  public void storeAttribute(@NonNull String name, int value) throws IOException {
    storeAttribute(name, String.valueOf(value));
  }

  public void storeAttribute(@NonNull String name, long value) throws IOException {
    storeAttribute(name, String.valueOf(value));
  }

  private void storeAttributeStream(String name, InputStream value) throws IOException {
    // value -> buffer (because crypto needs buffered reads) -> base64 encode  -> reader -> pipe to writer
    InputStreamReader encoded = new InputStreamReader(new Base64.InputStream(new BufferedInputStream(value), Base64.ENCODE), "US-ASCII");
    try {
      writer.write(name);
      writer.write(OPEN_ATTRIBUTE);
      char[] buffer = new char[1024];
      int len;
      while ((len = encoded.read(buffer)) != -1) {
        writer.write(buffer, 0, len);
      }
      writer.write(CLOSE_ATTRIBUTE);
    } finally {
      encoded.close();
    }
  }


  @Nullable
  private static String escapeXML(String s) {
    if (TextUtils.isEmpty(s)) return s;

    Matcher matcher = PATTERN.matcher(s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;"));
    StringBuffer st = new StringBuffer();

    while (matcher.find()) {
      String escaped = "";
      for (char ch : matcher.group(0).toCharArray()) {
        escaped += ("&#" + ((int) ch) + ";");
      }
      matcher.appendReplacement(st, escaped);
    }
    matcher.appendTail(st);
    return st.toString();
  }

  public void startMMS() throws IOException {
    writer.write(OPEN_TAG_MMS);
  }

  public void closeMMS() throws IOException {
    writer.write(CLOSE_TAG_MMS);
    writer.newLine();
  }

  public void startParts() throws IOException {
    // close dangling open MMS tag
    writer.write(CLOSE_TAG);
    writer.newLine();
    writer.write(OPEN_TAG_PARTS);
    writer.newLine();
  }

  public void startPart() throws IOException {
    writer.write(OPEN_TAG_PART);
  }

  public void closePart() throws IOException {
    writer.write(CLOSE_EMPTY_TAG);
    writer.newLine();
  }

  public void closeParts() throws IOException {
    writer.write(CLOSE_TAG_PARTS);
    writer.newLine();
  }

  public void openSMS() throws IOException {
    writer.write(OPEN_TAG_SMS);
  }

  public void closeSMS() throws IOException {
    writer.write(CLOSE_EMPTY_TAG);
    writer.newLine();
  }

  private void startAddresses() throws IOException {
    writer.write(OPEN_TAG_ADDRESSES);
    writer.newLine();
  }

  private void closeAddresses() throws IOException {
    writer.write(CLOSE_TAG_ADDRESSES);
    writer.newLine();
  }

  private void startAddress() throws IOException {
    writer.write(OPEN_TAG_ADDRESS);
  }

  public void closeAddress() throws IOException {
    writer.write(CLOSE_EMPTY_TAG);
    writer.newLine();
  }

  void writeComment(@NonNull String comment) throws IOException {
    writer.write("<!-- ");
    writer.write(comment);
    writer.write(" -->");
    writer.newLine();
  }


  void addCommonAttributes(MessageRecord record) throws IOException {
    //TODO: protocol currently not stored in record (but in database)
    //storeAttribute(Telephony.TextBasedSmsColumns.PROTOCOL, protocol);
    storeAttribute(Telephony.TextBasedSmsColumns.DATE, record.getDateReceived());
    storeAttribute(Telephony.TextBasedSmsColumns.DATE_SENT, record.getDateSent());
    storeAttribute(SIGNAL_TYPE, record.getType());
    //TODO: subject currently not stored in record (but in database)
    //storeAttribute(Telephony.TextBasedSmsColumns.SUBJECT, null);
    storeAttribute(Telephony.TextBasedSmsColumns.READ, 1);
  }


  public void writeRecord(MessageRecord record) throws IOException {
    if (record.isMmsNotification()) {
      Log.w(LOG, "ignored mms notification during backup");
      return;
    }

    String groupAddress = getGroupAddress(record);

    if (record.isMms() || (groupAddress != null)) {
      // media messages and messages to groups are stored as MMS

      List<Attachment> attachmentList = new ArrayList<>(0);
      if (record.isMms()) {
        MediaMmsMessageRecord mmsRecord = (MediaMmsMessageRecord) record;
        attachmentList = mmsRecord.getSlideDeck().asAttachments();
      }

      startMMS();

      storeAttribute(Telephony.BaseMmsColumns.TEXT_ONLY, attachmentList.isEmpty());              // optional

      String localNumber = TextSecurePreferences.getLocalNumber(context);

      // involved addresses differ for groups and direct messages
      // for non groups the single other party is found in the thread
      // for groups the involved numbers are stored in the group database
      if (groupAddress == null) {
        // this is a message to a single recipient, but with an attachment

        Recipient otherParty = threads.getRecipientsForThreadId(record.getThreadId()).getPrimaryRecipient();
        storeAttribute(Telephony.TextBasedSmsColumns.ADDRESS, otherParty.getNumber());

        addCommonAttributes(record);
        addMMSAttributes(record, attachmentList);

        startAddresses();
        storeRecipient(otherParty.getNumber(), !record.isOutgoing());
        storeRecipient(localNumber, record.isOutgoing());

      }
      else {
        // this is a message to multiple recipients, it may or may not have attachments

        // note that the way addresses are generated means that all messages in a conversation will have the same
        // set of participants, even if the group changed during its lifetime
        GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
        byte[] decodedGroupAddress = GroupUtil.getDecodedId(groupAddress);

        storeAttribute(SIGNAL_GROUP_ADDRESS, groupAddress);

        Recipients otherParties = groupDatabase.getGroupMembers(decodedGroupAddress, /*includeSelf = */ false);
        storeAttribute(Telephony.TextBasedSmsColumns.ADDRESS, TextUtils.join("~", otherParties.toNumberStringArray(false)));

        addCommonAttributes(record);
        addMMSAttributes(record, attachmentList);

        startAddresses();
        String sender = (record.isOutgoing()) ? localNumber : record.getIndividualRecipient().getNumber();
        Recipients allGroupMembers = groupDatabase.getGroupMembers(decodedGroupAddress, /*includeSelf = */ true);
        for (Recipient recipient : allGroupMembers) {
          String number = recipient.getNumber();
          storeRecipient(number, sender.equals(number));
        }
      }

      closeAddresses();
      closeMMS();
    }
    else {
      // text only messages to single recipient are stored as SMS
      openSMS();
      // individual recipient of simple sms is always the other party
      storeAttribute(Telephony.TextBasedSmsColumns.ADDRESS, record.getIndividualRecipient().getNumber());
      addCommonAttributes(record);
      storeAttribute(Telephony.TextBasedSmsColumns.STATUS, record.isDelivered() ? SmsDatabase.Status.STATUS_COMPLETE : record.getDeliveryStatus());
      // same as MMS.MESSAGE_BOX
      storeAttribute(Telephony.TextBasedSmsColumns.TYPE, MmsSmsColumns.Types.translateToSystemBaseType(record.getType()));
      storeAttribute(Telephony.TextBasedSmsColumns.BODY, getBody(record));
      //TODO: subject currently not stored in record (but in database)
      //storeAttribute(Telephony.TextBasedSmsColumns.SERVICE_CENTER, null);
      closeSMS();
    }
  }

  private void addMMSAttributes(MessageRecord record, List<Attachment> attachmentList) throws IOException {
    // same as SMS.TYPE
    storeAttribute(Telephony.BaseMmsColumns.MESSAGE_BOX, MmsSmsColumns.Types.translateToSystemBaseType(record.getType()));

    startParts();
    storeBodyAsPart(record);

    int count = 0;
    for (Attachment attachment : attachmentList) {
      storeAttachmentAsPart(attachment, "attachment" + count);
      count++;
    }

    closeParts();
  }

  private void storeRecipient(String number, boolean sender) throws IOException {
    startAddress();
    storeAttribute(Telephony.Mms.Addr.ADDRESS, number);
    storeAttribute(Telephony.Mms.Addr.CHARSET, UTF_8);
    storeAttribute(Telephony.Mms.Addr.TYPE, sender ? PduHeaders.FROM : PduHeaders.TO);
    closeAddress();
  }

  private void storeAttachmentAsPart(Attachment attachment, String name) throws IOException {
    if (attachment.isInProgress()) {
      Log.w(LOG, "tried to store in progress attachment");
      return;
    }

    startPart();
    // for my example mms, the sequence for all real content elements was 0
    // and the sequence for the main "smil" file was -1
    storeAttribute(Telephony.Mms.Part.SEQ, 0);
    storeAttribute(Telephony.Mms.Part.CONTENT_TYPE, attachment.getContentType());
    storeAttribute(Telephony.Mms.Part.NAME, name);
    storeAttribute(Telephony.Mms.Part.CHARSET, null);
    storeAttribute(Telephony.Mms.Part.CONTENT_DISPOSITION, null);
    storeAttribute(Telephony.Mms.Part.FILENAME, null);
    storeAttribute(Telephony.Mms.Part.CONTENT_ID, "<" + name + ">"); // should be int?
    storeAttribute(Telephony.Mms.Part.CONTENT_LOCATION, name);      // should be int?
    storeAttribute(Telephony.Mms.Part.CT_START, null);
    storeAttribute(Telephony.Mms.Part.CT_TYPE, null);
    storeAttribute(Telephony.Mms.Part.TEXT, null);
    storeAttributeStream(DATA, PartAuthority.getAttachmentStream(context, masterSecret, attachment.getDataUri()));
    closePart();
  }


  private void storeBodyAsPart(MessageRecord record) throws IOException {
    // store message text only
    startPart();

    storeAttribute(Telephony.Mms.Part.SEQ, 0);
    storeAttribute(Telephony.Mms.Part.CONTENT_TYPE, "text/plain");
    storeAttribute(Telephony.Mms.Part.NAME, null);
    storeAttribute(Telephony.Mms.Part.CHARSET, UTF_8);
    storeAttribute(Telephony.Mms.Part.CONTENT_DISPOSITION, null);
    storeAttribute(Telephony.Mms.Part.FILENAME, null);
    storeAttribute(Telephony.Mms.Part.CONTENT_ID, "<text_0>"); // should be int?
    storeAttribute(Telephony.Mms.Part.CONTENT_LOCATION, "text_0.txt");      // should be int?
    storeAttribute(Telephony.Mms.Part.CT_START, null);
    storeAttribute(Telephony.Mms.Part.CT_TYPE, null);
    storeAttribute(Telephony.Mms.Part.TEXT, getBody(record));

    closePart();
  }

  private String getBody(MessageRecord record) {
    String displayedMessage = record.getDisplayBody().toString();
    // display body has a maximum display length. want to store the full message in body.
    // no other obvious method found to test whether a message is "not special" (e.g. can just use the body)
    return (displayedMessage.length() == MessageRecord.MAX_DISPLAY_LENGTH) ? record.getBody().getBody() : displayedMessage;
  }


  @Nullable
  private String getGroupAddress(MessageRecord record) {
    Recipients threadRecipients = threads.getRecipientsForThreadId(record.getThreadId());
    if (threadRecipients == null || threadRecipients.isEmpty()) {
      return null;
    }
    Recipient rec = threadRecipients.getPrimaryRecipient();
    return rec.isGroupRecipient() ? rec.getNumber() : null;
  }


// more mms attributes that may want to be saved?
//      storeAttribute(BaseMmsColumns.CONTENT_CLASS, null);
//      storeAttribute(BaseMmsColumns.SUBJECT_CHARSET, 106);
//      storeAttribute(BaseMmsColumns.RETRIEVE_STATUS, "128");
//      storeAttribute(BaseMmsColumns.MESSAGE_CLASS, "personal");
//      storeAttribute(BaseMmsColumns.DELIVERY_TIME, null);
//      storeAttribute(BaseMmsColumns.READ_STATUS, null);
//      storeAttribute(BaseMmsColumns.CONTENT_TYPE, "application/vnd.wap.multipart.related");
//      storeAttribute(BaseMmsColumns.SUBSCRIPTION_ID, 0);
//      storeAttribute(BaseMmsColumns.RETRIEVE_TEXT_CHARSET, null);
//      storeAttribute(BaseMmsColumns.DELIVERY_REPORT, 128);
//      storeAttribute(BaseMmsColumns.MESSAGE_ID, "NOKASDF0000900002");
//      storeAttribute(BaseMmsColumns.SEEN, 1);
//      storeAttribute(BaseMmsColumns.MESSAGE_TYPE, 132);
//      storeAttribute(BaseMmsColumns.MMS_VERSION, 17);
//      storeAttribute(BaseMmsColumns.PRIORITY, 129);
//      storeAttribute(BaseMmsColumns.READ_REPORT, 129);
//      storeAttribute(BaseMmsColumns.RESPONSE_TEXT, null);
//      storeAttribute(BaseMmsColumns.REPORT_ALLOWED, null);
//      storeAttribute(BaseMmsColumns.LOCKED, 0);
//      storeAttribute(BaseMmsColumns.RETRIEVE_TEXT, null);
//      storeAttribute(BaseMmsColumns.RESPONSE_STATUS, null);
//      storeAttribute(BaseMmsColumns.CREATOR, null);
//      storeAttribute(READABLE_DATE, "08.03.3588 12:54:30");
//      storeAttribute(CONTACT_NAME, "Hook");

// only used in notifications
//			storeAttribute(Telephony.BaseMmsColumns.EXPIRY, expiry);
//			storeAttribute(Telephony.BaseMmsColumns.CONTENT_LOCATION, getContentLocation());
//			storeAttribute(Telephony.BaseMmsColumns.TRANSACTION_ID, getTransactionId());
//			storeAttribute(Telephony.BaseMmsColumns.MESSAGE_SIZE, messageSize);

}
