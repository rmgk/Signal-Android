package org.thoughtcrime.securesms.database.backup;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public class XmlBackupWriter {

	private static final String XML_HEADER      = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>";
	private static final String CREATED_BY      = "<!-- File Created By Signal -->";
	private static final String OPEN_TAG_SMSES  = "<smses count=\"%d\">";
	private static final String CLOSE_TAG_SMSES = "</smses>";

	private final BufferedWriter bufferedWriter;

	public XmlBackupWriter(BufferedWriter writer, int count) throws IOException {
		bufferedWriter = writer;

		bufferedWriter.write(XML_HEADER);
		bufferedWriter.newLine();
		bufferedWriter.write(CREATED_BY);
		bufferedWriter.newLine();
		bufferedWriter.write(String.format(OPEN_TAG_SMSES, count));
	}

	public void writeItem(XmlBackupItem item) throws IOException {
		bufferedWriter.newLine();
		item.storeOn(bufferedWriter);
	}

	public void close() throws IOException {
		bufferedWriter.newLine();
		bufferedWriter.write(CLOSE_TAG_SMSES);
		bufferedWriter.close();
	}
}