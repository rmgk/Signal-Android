package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Environment;
import android.provider.Telephony;
import android.util.Log;

import android.util.Pair;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUnion;
import org.thoughtcrime.securesms.database.backup.MmsBackupItem;
import org.thoughtcrime.securesms.database.backup.PlaintextBackupExporter;
import org.thoughtcrime.securesms.database.backup.SmsBackupItem;
import org.thoughtcrime.securesms.database.backup.XmlBackupReader;
import org.thoughtcrime.securesms.database.backup.BackupItem;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlaintextBackupImporter {

  public static final String TAG = PlaintextBackupImporter.class.getSimpleName();

  public static void importPlaintextFromSd(Context context, MasterSecret masterSecret)
      throws NoExternalStorageException, IOException
  {
    Log.w(TAG, "importPlaintext()");
    SmsDatabase    smsDatabase    = DatabaseFactory.getSmsDatabase(context);
    SQLiteDatabase transaction = smsDatabase.beginTransaction();
    MmsDatabase    mmsDatabase    = DatabaseFactory.getMmsDatabase(context);
    AttachmentDatabase attachmentDatabase   = DatabaseFactory.getAttachmentDatabase(context);
    MmsAddressDatabase addressDatabase = DatabaseFactory.getMmsAddressDatabase(context);
    GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);

    try {
      ThreadDatabase threads         = DatabaseFactory.getThreadDatabase(context);
      XmlBackupReader backup          = new XmlBackupReader(getPlaintextExportFile().getAbsolutePath());
      MasterCipher   masterCipher    = new MasterCipher(masterSecret);
      Set<Long>      modifiedThreads = new HashSet<>();

      SQLiteStatement mmsStatement = mmsDatabase.createInsertStatement(transaction);
      SQLiteStatement smsStatement = smsDatabase.createInsertStatement(transaction);


      BackupItem msg;

      while ((msg = backup.getNext()) != null) {
        if (msg.getAddress() == null || msg.getAddress().equals("null")) {
          Log.i(TAG, "did not import message with null address");
          continue;
        }
        if (!isAppropriateTypeForImport(msg.getType())) {
          Log.i(TAG, "did not import message with unhandled type: " + msg.getType());
          continue;
        }
        final long threadId = getThreadId(context, threads, msg);

        if (msg instanceof SmsBackupItem) {
          addSmstoStatement(smsStatement, (SmsBackupItem) msg, threadId, masterCipher);
          smsStatement.execute();
        } else {
          MmsBackupItem mms = (MmsBackupItem) msg;
          if (mms.getSignalGroupAddress() != null) {
            byte[] groupID = GroupUtil.getDecodedId(mms.getSignalGroupAddress());
            if (groupDatabase.getGroup(groupID) == null) {
              Log.w(TAG, "creating group " + mms.getSignalGroupAddress());
              List<String> members = new ArrayList<>(10);
              MmsAddresses addr = mms.getAddresses();
              members.add(addr.getFrom());
              members.addAll(addr.getTo());
              members.addAll(addr.getCc());
              members.addAll(addr.getBcc());
              groupDatabase.create(groupID, "imported group", members, null, null);
            }
          }

          addMmsToStatement(mmsStatement, mms, threadId, masterCipher);
          long messageId = mmsStatement.executeInsert();

          addressDatabase.insertAddressesForId(messageId, mms.getAddresses());
          for (Map<String, String> part : mms.parts) {
            insertAttachment(masterSecret, messageId, part, attachmentDatabase);
          }

        }

        modifiedThreads.add(threadId);
      }

      for (long threadId : modifiedThreads) {
        threads.update(threadId, true);
        mmsDatabase.notifyConversationListeners(threadId);
        smsDatabase.notifyConversationListeners(threadId);
      }

      transaction.setTransactionSuccessful();

      Log.w(TAG, "Exited loop");
    } catch (XmlPullParserException e) {
      Log.w(TAG, e);
      throw new IOException("XML Parsing error!");
    } finally {
      transaction.endTransaction();
    }
  }


  private static AttachmentId insertAttachment(MasterSecret masterSecret, long mmsId, Map<String, String> mmspart, AttachmentDatabase attachmentDatabase) {
    Log.w(TAG, "Inserting attachment for mms id: " + mmsId);

    long uniqueId = System.currentTimeMillis();

    Pair<File, Long> partData;

    try {
      for (String key: mmspart.keySet()) {
      }
      InputStream in = new Base64.InputStream(new ByteArrayInputStream(mmspart.get("data").getBytes(Charset.forName("UTF-8"))), Base64.DECODE | Base64.DONT_GUNZIP);
      partData = attachmentDatabase.setAttachmentData(masterSecret, in);
    }
    catch (MmsException e) {
      throw new RuntimeException(e);
    }


    ContentValues contentValues = new ContentValues();
    contentValues.put(AttachmentDatabase.MMS_ID, mmsId);
    contentValues.put(AttachmentDatabase.CONTENT_TYPE, mmspart.get(Telephony.Mms.Part.CONTENT_TYPE));
    contentValues.put(AttachmentDatabase.TRANSFER_STATE, AttachmentDatabase.TRANSFER_PROGRESS_DONE);
    contentValues.put(AttachmentDatabase.UNIQUE_ID, uniqueId);
    contentValues.put(AttachmentDatabase.CONTENT_LOCATION, mmspart.get(Telephony.Mms.Part.CONTENT_LOCATION));
    contentValues.put(AttachmentDatabase.CONTENT_DISPOSITION, mmspart.get(Telephony.Mms.Part.CONTENT_DISPOSITION));
    contentValues.put(AttachmentDatabase.NAME, mmspart.get(Telephony.Mms.Part.NAME));

    contentValues.put(AttachmentDatabase.DATA, partData.first.getAbsolutePath());
    contentValues.put(AttachmentDatabase.SIZE, partData.second);

    long rowId = attachmentDatabase.insertContentValues(contentValues);
    AttachmentId attachmentId = new AttachmentId(rowId, uniqueId);

    Log.i(TAG, "added attachment " + attachmentId);


    attachmentDatabase.scheduleThumbnailFetch(masterSecret, attachmentId);
    return attachmentId;
  }


  private static File getPlaintextExportFile() throws NoExternalStorageException {
    File backup    = PlaintextBackupExporter.getPlaintextExportFile();
    File oldBackup = new File(Environment.getExternalStorageDirectory(), "TextSecurePlaintextBackup.xml");

    return !backup.exists() && oldBackup.exists() ? oldBackup : backup;
  }


  private static long getThreadId(Context context, ThreadDatabase threads, BackupItem msg) {
    final String recipientAddress = (msg.getSignalGroupAddress() == null) ?
            msg.getAddress().replace('~', ',') :  // '~' is the address separator used by SMSB&R
            msg.getSignalGroupAddress();
    final Recipients recipients = RecipientFactory.getRecipientsFromString(context, recipientAddress, false);
    long id = threads.getThreadIdFor(recipients);
    return id;
  }

  private static void addSmstoStatement(SQLiteStatement statement, SmsBackupItem sms, long threadId, MasterCipher masterCipher) {
    addStringToStatement(statement, 1, sms.getAddress());
    addNullToStatement(statement, 2); //PERSON
    addLongToStatement(statement, 3, sms.getDateSent());
    addLongToStatement(statement, 4, sms.getDate());
    addLongToStatement(statement, 5, sms.getProtocol());
    addLongToStatement(statement, 6, sms.getRead());
    addLongToStatement(statement, 7, sms.getDeliveryStatus());
    if (sms.getSignalType() == null) addTranslatedTypeToStatement(statement, 8, sms.getType());
    else addLongToStatement(statement, 8, sms.getSignalType());
    addNullToStatement(statement, 9); //REPLY_PATH_PRESENT
    addStringToStatement(statement, 10, sms.getSubject());
    addEncryptedStringToStatement(masterCipher, statement, 11, sms.getBody());
    addStringToStatement(statement, 12, sms.getServiceCenter());
    addLongToStatement(statement, 13, threadId);
  }

  private static void addMmsToStatement(SQLiteStatement statement, MmsBackupItem mms, long threadId, MasterCipher masterCipher) {
    String[] args = {
            String.valueOf(threadId),
            mms.attributes.get(Telephony.BaseMmsColumns.DATE_SENT),
            mms.attributes.get(Telephony.BaseMmsColumns.DATE),
            (mms.getSignalType() == null) ?
                    String.valueOf(SmsDatabase.Types.translateFromSystemBaseType(mms.getMessageBox()) | SmsDatabase.Types.ENCRYPTION_SYMMETRIC_BIT) :
                    String.valueOf(mms.getSignalType()),
            mms.attributes.get(Telephony.BaseMmsColumns.READ),
            mms.attributes.get(Telephony.BaseMmsColumns.MESSAGE_ID),
            mms.attributes.get(Telephony.BaseMmsColumns.SUBJECT),
            mms.attributes.get(Telephony.BaseMmsColumns.SUBJECT_CHARSET),
            (mms.getBody() == null) ? null : masterCipher.encryptBody(mms.getBody()),
            String.valueOf(mms.getPartCount()),
            mms.attributes.get(Telephony.BaseMmsColumns.CONTENT_TYPE),
            mms.attributes.get(Telephony.BaseMmsColumns.CONTENT_LOCATION),
            mms.getAddresses().getFrom(),
            mms.attributes.get(MmsDatabase.ADDRESS_DEVICE_ID),
            mms.attributes.get(Telephony.BaseMmsColumns.EXPIRY),
            mms.attributes.get(Telephony.BaseMmsColumns.MESSAGE_CLASS),
            mms.attributes.get(Telephony.BaseMmsColumns.MESSAGE_TYPE),
            mms.attributes.get(Telephony.BaseMmsColumns.MMS_VERSION),
            mms.attributes.get(Telephony.BaseMmsColumns.MESSAGE_SIZE),
            mms.attributes.get(Telephony.BaseMmsColumns.PRIORITY),
            mms.attributes.get(Telephony.BaseMmsColumns.READ_REPORT),
            mms.attributes.get(Telephony.BaseMmsColumns.REPORT_ALLOWED),
            mms.attributes.get(Telephony.BaseMmsColumns.RESPONSE_STATUS),
            mms.attributes.get(Telephony.BaseMmsColumns.STATUS),
            mms.attributes.get(Telephony.BaseMmsColumns.TRANSACTION_ID),
            mms.attributes.get(Telephony.BaseMmsColumns.RETRIEVE_STATUS),
            mms.attributes.get(Telephony.BaseMmsColumns.RETRIEVE_TEXT),
            mms.attributes.get(Telephony.BaseMmsColumns.RETRIEVE_TEXT_CHARSET),
            mms.attributes.get(Telephony.BaseMmsColumns.READ_STATUS),
            mms.attributes.get(Telephony.BaseMmsColumns.CONTENT_CLASS),
            mms.attributes.get(Telephony.BaseMmsColumns.RESPONSE_TEXT),
            mms.attributes.get(Telephony.BaseMmsColumns.DELIVERY_TIME),
            mms.attributes.get(MmsDatabase.RECEIPT_COUNT),
            mms.attributes.get(MmsDatabase.MISMATCHED_IDENTITIES),
            mms.attributes.get(MmsDatabase.NETWORK_FAILURE),
            mms.attributes.get(Telephony.BaseMmsColumns.DELIVERY_REPORT),
            mms.attributes.get(Telephony.BaseMmsColumns.SUBSCRIPTION_ID)
    };
    for (int i = args.length; i != 0; i--) {
      String arg = args[i - 1];
      if ((arg == null) || "null".equals(arg)) statement.bindNull(i);
      else statement.bindString(i, arg);
    }
//    addLongToStatement(statement, 1, threadId);                              // THREAD_ID
//    addLongToStatement(statement, 2, mms.getDateSent());                     // DATE_SENT
//    addLongToStatement(statement, 3, mms.getDate());                         // DATE_RECEIVED
//    addTranslatedTypeToStatement(statement, 4, mms.getMessageBox());         // MESSAGE_BOX
//    addLongToStatement(statement, 5, mms.getRead());                         // READ                  DEFAULT 0
//    addNullToStatement(statement, 6);                                        // MESSAGE_ID
//    addStringToStatement(statement, 7, mms.getSubject());                    // SUBJECT
//    addNullToStatement(statement, 8);                                        // SUBJECT_CHARSET
//    addEncryptedStingToStatement(masterCipher, statement, 9, mms.getBody()); // BODY
//    addLongToStatement(statement, 10, mms.getPartCount());                   // PART_COUNT
//    addNullToStatement(statement, 11);                                       // CONTENT_TYPE
//    addStringToStatement(statement, 12, mms.getContentLocation());           // CONTENT_LOCATION
//    addStringToStatement(statement, 13, mms.getAddress());                   // ADDRESS
//    addNullToStatement(statement, 14);                                       // ADDRESS_DEVICE_ID
//    addLongToStatement(statement, 15, mms.getExpiry());                      // EXPIRY
//    addNullToStatement(statement, 16);                                       // MESSAGE_CLASS
//    addNullToStatement(statement, 17);                                       // MESSAGE_TYPE
//    addNullToStatement(statement, 18);                                       // MMS_VERSION
//    addLongToStatement(statement, 19, mms.getMessageSize());                 // MESSAGE_SIZE
//    addNullToStatement(statement, 20);                                       // PRIORITY
//    addNullToStatement(statement, 21);                                       // READ_REPORT
//    addNullToStatement(statement, 22);                                       // REPORT_ALLOWED
//    addNullToStatement(statement, 23);                                       // RESPONSE_STATUS
//    addLongToStatement(statement, 24, mms.getDeliveryStatus());              // STATUS  TODO: check mms/sms deliveryStatus
//    addStringToStatement(statement, 25, mms.getTransactionId());             // TRANSACTION_ID
//    addNullToStatement(statement, 26);                                       // RETRIEVE_STATUS
//    addNullToStatement(statement, 27);                                       // RETRIEVE_TEXT
//    addNullToStatement(statement, 28);                                       // RETRIEVE_TEXT_CHARSET
//    addLongToStatement(statement, 29, mms.getRead());                        // READ_STATUS
//    addNullToStatement(statement, 30);                                       // CONTENT_CLASS
//    addNullToStatement(statement, 31);                                       // RESPONSE_TEXT
//    addNullToStatement(statement, 32);                                       // DELIVERY_TIME
//    addNullToStatement(statement, 33);                                       // RECEIPT_COUNT,        DEFAULT 0
//    addNullToStatement(statement, 34);                                       // MISMATCHED_IDENTITIES DEFAULT NULL
//    addNullToStatement(statement, 35);                                       // NETWORK_FAILURE       DEFAULT NULL
//    addNullToStatement(statement, 36);                                       // DELIVERY_REPORT
//    addLongToStatement(statement, 37, mms.getSubscriptionId());              // SUBSCRIPTION_ID       DEFAULT -1
  }

  private static void addEncryptedStringToStatement(MasterCipher masterCipher, SQLiteStatement statement, int index, String value) {
    if (value == null || value.equals("null")) {
      statement.bindNull(index);
    } else {
      statement.bindString(index, masterCipher.encryptBody(value));
    }
  }

  private static void addTranslatedTypeToStatement(SQLiteStatement statement, int index, int type) {
    statement.bindLong(index, SmsDatabase.Types.translateFromSystemBaseType(type) | SmsDatabase.Types.ENCRYPTION_SYMMETRIC_BIT);
  }

  private static void addStringToStatement(SQLiteStatement statement, int index, String value) {
    if (value == null || value.equals("null")) statement.bindNull(index);
    else                                       statement.bindString(index, value);
  }

  private static void addNullToStatement(SQLiteStatement statement, int index) {
    statement.bindNull(index);
  }

  private static void addLongToStatement(SQLiteStatement statement, int index, long value) {
    statement.bindLong(index, value);
  }

  private static boolean isAppropriateTypeForImport(long theirType) {
    long ourType = SmsDatabase.Types.translateFromSystemBaseType(theirType);

    return ourType == MmsSmsColumns.Types.BASE_INBOX_TYPE ||
           ourType == MmsSmsColumns.Types.BASE_SENT_TYPE ||
           ourType == MmsSmsColumns.Types.BASE_SENT_FAILED_TYPE;
  }
}
