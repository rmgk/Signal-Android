package org.thoughtcrime.securesms.database.backup;

import android.support.annotation.Nullable;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class XmlBackupReader {
  private final XmlPullParser parser;

  public XmlBackupReader(String path) throws XmlPullParserException, FileNotFoundException {
    parser = XmlPullParserFactory.newInstance().newPullParser();
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
    parser.setInput(new FileInputStream(path), null);
  }

  public @Nullable
  BackupItem getNext() throws IOException, XmlPullParserException {
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      if (parser.getEventType() != XmlPullParser.START_TAG) {
        continue;
      }
      if (parser.getAttributeCount() <= 0) {
        continue;
      }
      if (parser.getName().equalsIgnoreCase("sms")) {
        return new SmsBackupItem(parser);
      }
      else if (parser.getName().equalsIgnoreCase("mms")) {
        return new MmsBackupItem(parser);
      }
    }
    return null;
  }


}
