package org.thoughtcrime.securesms.database.backup;


import android.content.Context;
import android.os.Environment;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
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
    XmlBackupWriter writer = new XmlBackupWriter(bufferedWriter, context, masterSecret);
    writer.writeHeader(smsCount + mmsCount);

    exportSms(context, masterSecret, writer);
    exportMms(context, masterSecret, writer);

    writer.close();
  }

  private static void exportMms(Context context, MasterSecret masterSecret, XmlBackupWriter writer) throws IOException {
    final int ROW_LIMIT = 500;
    int skip = 0;

    MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(context);
    MmsDatabase.Reader mmsReader = null;
    do {
      if (mmsReader != null)
        mmsReader.close();

      mmsReader = mmsDatabase.getMessages(masterSecret, skip, ROW_LIMIT);

      MessageRecord mmsRecord;
      while ((mmsRecord = mmsReader.getNext()) != null) {
        writer.writeRecord(mmsRecord);
      }

      skip += ROW_LIMIT;
    } while (mmsReader.getCount() > 0);
  }

  private static void exportSms(Context context, MasterSecret masterSecret, XmlBackupWriter writer) throws IOException {
    final int ROW_LIMIT = 500;
    int skip = 0;

    EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);
    SmsDatabase.Reader reader = null;

    do {
      if (reader != null) reader.close();

      reader = smsDatabase.getMessages(masterSecret, skip, ROW_LIMIT);

      SmsMessageRecord record;
      while ((record = reader.getNext()) != null) {
        writer.writeRecord(record);
      }

      skip += ROW_LIMIT;
    } while (reader.getCount() > 0);
  }

}
