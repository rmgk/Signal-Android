package org.thoughtcrime.securesms.database.backup;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

public class XmlBackupReader {
  private final XmlPullParser parser;

  public XmlBackupReader(String path) throws XmlPullParserException, FileNotFoundException {
    parser = XmlPullParserFactory.newInstance().newPullParser();
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
    parser.setInput(new FileInputStream(path), null);
  }

  public XmlBackupItem getNext() throws IOException, XmlPullParserException {
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      if (parser.getEventType() != XmlPullParser.START_TAG) {
        continue;
      }
      if (!parser.getName().equalsIgnoreCase("sms")) {
        continue;
      }
      if (parser.getAttributeCount() <= 0) {
        continue;
      }
      return new XmlBackupItem.Sms(parser);
    }
    return null;
  }


}
