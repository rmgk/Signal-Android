package org.thoughtcrime.securesms.database;


import android.content.Context;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.DisplayRecord;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.util.StorageUtil;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.io.File;
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
    int count               = DatabaseFactory.getSmsDatabase(context).getMessageCount();
    XmlBackup.Writer writer = new XmlBackup.Writer(getPlaintextExportFile().getAbsolutePath(), count);
    ThreadDatabase threads  = DatabaseFactory.getThreadDatabase(context);

    SmsMessageRecord record;
    EncryptingSmsDatabase.Reader reader = null;
    int skip                            = 0;
    final int ROW_LIMIT                 = 500;

    do {
      if (reader != null)
        reader.close();

      reader = DatabaseFactory.getEncryptingSmsDatabase(context).getMessages(masterSecret, skip, ROW_LIMIT);

      while ((record = reader.getNext()) != null) {
        String threadAddress = null;
        Recipients threadRecipients = threads.getRecipientsForThreadId(record.getThreadId());
        if (threadRecipients != null && !threadRecipients.isEmpty()) {
          threadAddress = threadRecipients.getPrimaryRecipient().getNumber();
        }
        XmlBackup.XmlBackupItem item =
            new XmlBackup.XmlBackupItem(0, record.getIndividualRecipient().getNumber(),
                                        threadAddress, record.getDateReceived(),
                                        MmsSmsColumns.Types.translateToSystemBaseType(record.getType()),
                                        null, record.getDisplayBody().toString(), null,
                                        1, record.isDelivered() ? SmsDatabase.Status.STATUS_COMPLETE : record.getDeliveryStatus());

        writer.writeItem(item);
      }

      skip += ROW_LIMIT;
    } while (reader.getCount() > 0);

    skip = 0;
    MmsDatabase.Reader mmsReader = null;
    do {
      if (mmsReader != null)
        mmsReader.close();

      mmsReader = DatabaseFactory.getMmsDatabase(context).getMessages(masterSecret, skip, ROW_LIMIT);

      MessageRecord mmsRecord;
      while ((mmsRecord = mmsReader.getNext()) != null) {
        String threadAddress = null;
        Recipients threadRecipients = threads.getRecipientsForThreadId(mmsRecord.getThreadId());
        if (threadRecipients != null && !threadRecipients.isEmpty()) {
          threadAddress = threadRecipients.getPrimaryRecipient().getNumber();
        }
        XmlBackup.XmlBackupItem item =
                new XmlBackup.XmlBackupItem(1, mmsRecord.getIndividualRecipient().getNumber(),
                        threadAddress, mmsRecord.getDateReceived(),
                        MmsSmsColumns.Types.translateToSystemBaseType(mmsRecord.getType()),
                        null, mmsRecord.getDisplayBody().toString(), null,
                        1, mmsRecord.isDelivered() ? SmsDatabase.Status.STATUS_COMPLETE : mmsRecord.getDeliveryStatus());

        writer.writeItem(item);
      }

      skip += ROW_LIMIT;
    } while (mmsReader.getCount() > 0);

    writer.close();
  }
}
