package org.thoughtcrime.securesms.database.backup;

import android.util.Log;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.dom.smil.SmilDocumentImpl;
import org.thoughtcrime.securesms.dom.smil.parser.SmilXmlSerializer;
import org.thoughtcrime.securesms.util.SmilUtil;
import org.w3c.dom.smil.SMILDocument;
import org.w3c.dom.smil.SMILElement;
import org.w3c.dom.smil.SMILLayoutElement;
import org.w3c.dom.smil.SMILMediaElement;
import org.w3c.dom.smil.SMILParElement;
import org.w3c.dom.smil.SMILRegionElement;
import org.w3c.dom.smil.SMILRegionMediaElement;
import org.w3c.dom.smil.SMILRootLayoutElement;
import ws.com.google.android.mms.ContentType;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.List;

/* this class contains duplicated code from  org.thoughtcrime.securesms.util.SmilUtil */
public class SmilFromRecord {

  static class AttachmentLocation {
    public final String location;
    public final String contentType;

    public String getLocation() {
      return location;
    }

    public String getContentType() {
      return contentType;
    }

    AttachmentLocation(String location, String contentType) {
      this.location = location;
      this.contentType = contentType;
    }
  }

  private static final String TAG = SmilFromRecord.class.getSimpleName();

  public static String getSmilBody(MessageRecord record, List<AttachmentLocation> attachmentList) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SmilXmlSerializer.serialize(createSmilDocument(record, attachmentList), out);
    return new String(out.toByteArray(), Charset.forName("UTF-8"));
  }

  private static SMILDocument createSmilDocument(MessageRecord record, List<AttachmentLocation> attachmentList) {
    Log.w(TAG, "Creating SMIL document from MessageRecord.");

    SMILDocument document = new SmilDocumentImpl();

    SMILElement smilElement = (SMILElement) document.createElement("smil");
    document.appendChild(smilElement);

    SMILElement headElement = (SMILElement) document.createElement("head");
    smilElement.appendChild(headElement);

    SMILLayoutElement layoutElement = (SMILLayoutElement) document.createElement("layout");
    headElement.appendChild(layoutElement);

    SMILRootLayoutElement rootLayoutElement = (SMILRootLayoutElement) document.createElement("root-layout");
    rootLayoutElement.setWidth(SmilUtil.ROOT_WIDTH);
    rootLayoutElement.setHeight(SmilUtil.ROOT_HEIGHT);
    layoutElement.appendChild(rootLayoutElement);

    SMILElement bodyElement = (SMILElement) document.createElement("body");
    smilElement.appendChild(bodyElement);

    SMILParElement par = (SMILParElement) document.createElement("par");
    bodyElement.appendChild(par);

    for (AttachmentLocation attachment: attachmentList) {
      SMILRegionElement regionElement = getRegion(document, attachment);
      SMILMediaElement mediaElement  = getMediaElement(document, attachment);

      if (regionElement != null) {
        ((SMILRegionMediaElement)mediaElement).setRegion(regionElement);
        layoutElement.appendChild(regionElement);
      }
      par.appendChild(mediaElement);
    }

    return document;
  }

  private static SMILRegionElement getRegion(SMILDocument document, AttachmentLocation part) {
    if (ContentType.isAudioType(part.getContentType())) return null;

    SMILRegionElement region = (SMILRegionElement) document.createElement("region");
    if (ContentType.isTextType(part.getContentType())) {
      region.setId("Text");
      region.setTop(SmilUtil.ROOT_HEIGHT);
      region.setHeight(50);
    } else {
      region.setId("Image");
      region.setTop(0);
      region.setHeight(SmilUtil.ROOT_HEIGHT);
    }
    region.setLeft(0);
    region.setWidth(SmilUtil.ROOT_WIDTH);
    region.setFit("meet");
    return region;
  }

  private static SMILMediaElement getMediaElement(SMILDocument document, AttachmentLocation part) {
    final String tag;
    final String contentType = part.getContentType();
    if (ContentType.isImageType(contentType)) {
      tag = "img";
    } else if (ContentType.isAudioType(contentType)) {
      tag = "audio";
    } else if (ContentType.isVideoType(contentType)) {
      tag = "video";
    } else if (ContentType.isTextType(contentType)) {
      tag = "text";
    } else {
      tag = "ref";
    }
    return createMediaElement(tag, document, (part.getLocation() == null) ? "" : part.getLocation());
  }

  private static SMILMediaElement createMediaElement(String tag, SMILDocument document, String src) {
    SMILMediaElement mediaElement = (SMILMediaElement) document.createElement(tag);
    mediaElement.setSrc(escapeXML(src));
    return mediaElement;
  }

  private static String escapeXML(String str) {
    return str.replaceAll("&","&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&apos;");
  }
}
