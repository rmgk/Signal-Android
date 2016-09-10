package org.thoughtcrime.securesms.database.backup;


import android.content.Context;
import android.os.Environment;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.NotificationMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.util.StorageUtil;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class PlaintextBackupExporter {

  private static final String FILENAME = "SignalPlaintextBackup.xml";

  public static void exportPlaintextToSd(Context context, MasterSecret masterSecret)
      throws NoExternalStorageException, IOException
  {
    exportPlaintext(context, masterSecret);
  }

  public static File getPlaintextExportFile() throws NoExternalStorageException {
    return new File(StorageUtil.getBackupDir(), FILENAME);
  }

  private static void exportPlaintext(Context context, MasterSecret masterSecret)
      throws NoExternalStorageException, IOException
  {
    int smsCount = DatabaseFactory.getSmsDatabase(context).getMessageCount();
    int mmsCount = DatabaseFactory.getMmsDatabase(context).getMessageCount();
    BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(getPlaintextExportFile().getAbsolutePath(), false));
    XmlBackupWriter writer = new XmlBackupWriter(bufferedWriter, smsCount + mmsCount);
    ThreadDatabase threads  = DatabaseFactory.getThreadDatabase(context);

    exportSms(context, masterSecret, writer, threads);
    exportMms(context, masterSecret, writer, threads);

    writer.close();
  }

  private static void exportMms(Context context, MasterSecret masterSecret, XmlBackupWriter writer, ThreadDatabase threads) throws IOException {
    final int ROW_LIMIT = 500;
    int skip = 0;

    MmsDatabase.Reader mmsReader = null;
    do {
      if (mmsReader != null)
        mmsReader.close();

      mmsReader = DatabaseFactory.getMmsDatabase(context).getMessages(masterSecret, skip, ROW_LIMIT);

      MessageRecord mmsRecord;
      while ((mmsRecord = mmsReader.getNext()) != null) {
        String groupAddress = getGroupAddress(mmsRecord, threads);
        if (mmsRecord instanceof MediaMmsMessageRecord) {
          writer.writeItem(new XmlBackupItem.Mms((MediaMmsMessageRecord) mmsRecord, groupAddress));
        } else if (mmsRecord instanceof NotificationMmsMessageRecord) {
          writer.writeItem(new XmlBackupItem.Mms((NotificationMmsMessageRecord) mmsRecord, groupAddress));
        }
      }

      skip += ROW_LIMIT;
    } while (mmsReader.getCount() > 0);
  }

  private static void exportSms(Context context, MasterSecret masterSecret, XmlBackupWriter writer, ThreadDatabase threads) throws IOException {
    final int ROW_LIMIT = 500;
    int skip = 0;

    SmsDatabase.Reader reader = null;
    do {
      if (reader != null)
        reader.close();

      reader = DatabaseFactory.getEncryptingSmsDatabase(context).getMessages(masterSecret, skip, ROW_LIMIT);

      SmsMessageRecord record;
      while ((record = reader.getNext()) != null) {
        String groupAddress = getGroupAddress(record, threads);
        writer.writeItem(new XmlBackupItem.Sms(record, groupAddress));
      }

      skip += ROW_LIMIT;
    } while (reader.getCount() > 0);
  }

  @Nullable
  private static String getGroupAddress(MessageRecord record, ThreadDatabase threads) {
    Recipients threadRecipients = threads.getRecipientsForThreadId(record.getThreadId());
    if (threadRecipients == null || threadRecipients.isEmpty()) {
      return null;
    }
    Recipient rec = threadRecipients.getPrimaryRecipient();
    return rec.isGroupRecipient() ? rec.getNumber() : null;
  }
}
