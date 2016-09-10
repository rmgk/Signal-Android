package org.thoughtcrime.securesms.database.backup;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmlBackupWriter {

	private static final String XML_HEADER      = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>";
	private static final String CREATED_BY      = "<!-- File Created By Signal -->";
	private static final String OPEN_TAG_SMSES  = "<smses count=\"%d\">";
	private static final String CLOSE_TAG_SMSES = "</smses>";

	// XML tags
	public static final String CLOSE_TAG       = ">";
	public static final String CLOSE_EMPTY_TAG = "/>";
	public static final String OPEN_ATTRIBUTE  = "=\"";
	public static final String CLOSE_ATTRIBUTE = "\" ";

	// XML tags
	public static final String OPEN_TAG_SMS    = " <sms ";

	// XML tags
	public static final String OPEN_TAG_MMS    = " <mms ";
	public static final String OPEN_TAG_PARTS  = "   <parts>";
	public static final String OPEN_TAG_PART   = "   <part ";
	public static final String CLOSE_TAG_MMS   = " </mms> ";
	public static final String CLOSE_TAG_PARTS = "   </parts>";



	// XML escaping
	public static final Pattern PATTERN        = Pattern.compile("[^\u0020-\uD7FF]");

	private final BufferedWriter writer;

	public XmlBackupWriter(BufferedWriter writer, int count) throws IOException {
		this.writer = writer;

		this.writer.write(XML_HEADER);
		this.writer.newLine();
		this.writer.write(CREATED_BY);
		this.writer.newLine();
		this.writer.write(String.format(OPEN_TAG_SMSES, count));
		this.writer.newLine();
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

	public void storeAttribute(@NonNull String name, int value) throws IOException {
		storeAttribute(name, String.valueOf(value));
	}
	public void storeAttribute(@NonNull String name, long value) throws IOException {
		storeAttribute(name, String.valueOf(value));
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
}
