package edu.usu.cosl.aggregatord;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.syndication.io.XmlReaderException;

// copied from com.sun.syndication.io.XmlReader because the encoding detection is not exposed

public class EncodingDetector {

    private static final int BUFFER_SIZE = 4096;
    private static final String UTF_8 = "UTF-8";
    private static final String US_ASCII = "US-ASCII";
    private static final String UTF_16BE = "UTF-16BE";
    private static final String UTF_16LE = "UTF-16LE";
    private static final String UTF_16 = "UTF-16";
    private static String _staticDefaultEncoding = null;
    private static String _defaultEncoding;
    
	public static String detectEncoding(HttpURLConnection conn) throws IOException
	{
		String sEncoding = UTF_8;
		boolean lenient = true;
        try {
            sEncoding = doHttpStream(conn.getInputStream(),conn.getContentType(),lenient);
        }
        catch (XmlReaderException ex) {
//        	sEncoding = doLenientDetection(conn.getContentType(),ex);
        }
        return sEncoding;
	}

	private static String doHttpStream(InputStream is,String httpContentType,boolean lenient) throws IOException {
        BufferedInputStream pis = new BufferedInputStream(is, BUFFER_SIZE);
        String cTMime = getContentTypeMime(httpContentType);
        String cTEnc  = getContentTypeEncoding(httpContentType);
        String bomEnc = getBOMEncoding(pis);
        String xmlGuessEnc =  getXMLGuessEncoding(pis);
        String xmlEnc = getXmlProlog(pis,xmlGuessEnc);
        return calculateHttpEncoding(cTMime, cTEnc, bomEnc, xmlGuessEnc, xmlEnc, pis,lenient);
    }

    private static String calculateHttpEncoding(String cTMime, String cTEnc, String bomEnc, String xmlGuessEnc, String xmlEnc, InputStream is,boolean lenient) throws IOException {
        String encoding;
        if (lenient & xmlEnc!=null) {
            encoding = xmlEnc;
        }
        else {
            boolean appXml = isAppXml(cTMime);
            boolean textXml = isTextXml(cTMime);
            if (appXml || textXml) {
                if (cTEnc==null) {
                    if (appXml) {
                        encoding = calculateRawEncoding(bomEnc, xmlGuessEnc, xmlEnc, is);
                    }
                    else {
                        encoding = (_defaultEncoding == null) ? US_ASCII : _defaultEncoding;
                    }
                }
                else
                if (bomEnc!=null && (cTEnc.equals(UTF_16BE) || cTEnc.equals(UTF_16LE))) {
                    throw new XmlReaderException(HTTP_EX_1.format(new Object[]{cTMime,cTEnc,bomEnc,xmlGuessEnc,xmlEnc}),
                                                 cTMime,cTEnc,bomEnc,xmlGuessEnc,xmlEnc,is);
                }
                else
                if (cTEnc.equals(UTF_16)) {
                    if (bomEnc!=null && bomEnc.startsWith(UTF_16)) {
                        encoding = bomEnc;
                    }
                    else {
                        throw new XmlReaderException(HTTP_EX_2.format(new Object[]{cTMime,cTEnc,bomEnc,xmlGuessEnc,xmlEnc}),
                                                     cTMime,cTEnc,bomEnc,xmlGuessEnc,xmlEnc,is);
                    }
                }
                else {
                    encoding = cTEnc;
                }
            }
            else {
                throw new XmlReaderException(HTTP_EX_3.format(new Object[]{cTMime,cTEnc,bomEnc,xmlGuessEnc,xmlEnc}),
                                             cTMime,cTEnc,bomEnc,xmlGuessEnc,xmlEnc,is);
            }
        }
        return encoding;
    }
    // returns MIME type or NULL if httpContentType is NULL
    private static String getContentTypeMime(String httpContentType) {
        String mime = null;
        if (httpContentType!=null) {
            int i = httpContentType.indexOf(";");
            mime = ((i==-1) ? httpContentType : httpContentType.substring(0,i)).trim();
        }
        return mime;
    }

    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([.[^; ]]*)");

    // returns charset parameter value, NULL if not present, NULL if httpContentType is NULL
    private static String getContentTypeEncoding(String httpContentType) {
        String encoding = null;
        if (httpContentType!=null) {
            int i = httpContentType.indexOf(";");
            if (i>-1) {
                String postMime = httpContentType.substring(i+1);
                Matcher m = CHARSET_PATTERN.matcher(postMime);
                encoding = (m.find()) ? m.group(1) : null;
                encoding = (encoding!=null) ? encoding.toUpperCase() : null;
            }
            if (encoding != null &&
                    ((encoding.startsWith("\"") && encoding.endsWith("\"")) ||
                     (encoding.startsWith("'") && encoding.endsWith("'"))
                    )) {
                encoding = encoding.substring(1, encoding.length() - 1);
            }
        }
        return encoding;
    }
    // returns the BOM in the stream, NULL if not present,
    // if there was BOM the in the stream it is consumed
    private static String getBOMEncoding(BufferedInputStream is) throws IOException {
        String encoding = null;
        int[] bytes = new int[3];
        is.mark(3);
        bytes[0] = is.read();
        bytes[1] = is.read();
        bytes[2] = is.read();

        if (bytes[0] == 0xFE && bytes[1] == 0xFF) {
            encoding = UTF_16BE;
            is.reset();
            is.read();
            is.read();
        }
        else
        if (bytes[0] == 0xFF && bytes[1] == 0xFE) {
            encoding = UTF_16LE;
            is.reset();
            is.read();
            is.read();
        }
        else
        if (bytes[0] == 0xEF && bytes[1] == 0xBB && bytes[2] == 0xBF) {
            encoding = UTF_8;
        }
        else {
            is.reset();
        }
        return encoding;
    }
    // returns the best guess for the encoding by looking the first bytes of the stream, '<?'
    private static String getXMLGuessEncoding(BufferedInputStream is) throws IOException {
        String encoding = null;
        int[] bytes = new int[4];
        is.mark(4);
        bytes[0] = is.read();
        bytes[1] = is.read();
        bytes[2] = is.read();
        bytes[3] = is.read();
        is.reset();

        if (bytes[0] == 0x00 && bytes[1] == 0x3C && bytes[2] == 0x00 && bytes[3] == 0x3F) {
                encoding = UTF_16BE;
        }
        else
        if (bytes[0] == 0x3C && bytes[1] == 0x00 && bytes[2] == 0x3F && bytes[3] == 0x00) {
                encoding = UTF_16LE;
        }
        else
        if (bytes[0] == 0x3C && bytes[1] == 0x3F && bytes[2] == 0x78 && bytes[3] == 0x6D) {
            encoding = UTF_8;
        }
        return encoding;
    }

    private static final Pattern ENCODING_PATTERN =
        Pattern.compile("<\\?xml.*encoding[\\s]*=[\\s]*((?:\".[^\"]*\")|(?:'.[^']*'))", Pattern.MULTILINE);

    // returns the encoding declared in the <?xml encoding=...?>,  NULL if none
    private static String getXmlProlog(BufferedInputStream is,String guessedEnc) throws IOException {
        String encoding = null;
        if (guessedEnc!=null) {
            byte[] bytes = new byte[BUFFER_SIZE];
            is.mark(BUFFER_SIZE);
            int offset = 0;
            int max = BUFFER_SIZE;
            int c = is.read(bytes,offset,max);
            int firstGT = -1;
            while (c!=-1 && firstGT==-1 && offset< BUFFER_SIZE) {
                offset += c;
                max -= c;
                c = is.read(bytes,offset,max);
                firstGT = new String(bytes, 0, offset).indexOf(">");
            }
            if (firstGT == -1) {
                if (c == -1) {
                    throw new IOException("Unexpected end of XML stream");
                }
                else {
                    throw new IOException("XML prolog or ROOT element not found on first " + offset + " bytes");
                }
            }
            int bytesRead = offset;
            if (bytesRead>0) {
                is.reset();
                Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes,0,firstGT + 1), guessedEnc);
                BufferedReader bReader = new BufferedReader(reader);
                StringBuffer prolog = new StringBuffer();
                String line = bReader.readLine();
                while (line != null) {
                    prolog.append(line);
                    line = bReader.readLine();
                }
                Matcher m = ENCODING_PATTERN.matcher(prolog);
                if (m.find()) {
                    encoding = m.group(1).toUpperCase();
                    encoding = encoding.substring(1,encoding.length()-1);
                }
            }
        }
        return encoding;
    }
    // indicates if the MIME type belongs to the APPLICATION XML family
    private static boolean isAppXml(String mime) {
        return mime!=null &&
               (mime.equals("application/xml") ||
                mime.equals("application/xml-dtd") ||
                mime.equals("application/xml-external-parsed-entity") ||
                (mime.startsWith("application/") && mime.endsWith("+xml")));
    }

    // indicates if the MIME type belongs to the TEXT XML family
    private static boolean isTextXml(String mime) {
        return mime!=null &&
               (mime.equals("text/xml") ||
                mime.equals("text/xml-external-parsed-entity") ||
                (mime.startsWith("text/") && mime.endsWith("+xml")));
    }

    private static final MessageFormat RAW_EX_1 = new MessageFormat(
            "Invalid encoding, BOM [{0}] XML guess [{1}] XML prolog [{2}] encoding mismatch");

    private static final MessageFormat RAW_EX_2 = new MessageFormat(
            "Invalid encoding, BOM [{0}] XML guess [{1}] XML prolog [{2}] unknown BOM");

    private static final MessageFormat HTTP_EX_1 = new MessageFormat(
            "Invalid encoding, CT-MIME [{0}] CT-Enc [{1}] BOM [{2}] XML guess [{3}] XML prolog [{4}], BOM must be NULL");

    private static final MessageFormat HTTP_EX_2 = new MessageFormat(
            "Invalid encoding, CT-MIME [{0}] CT-Enc [{1}] BOM [{2}] XML guess [{3}] XML prolog [{4}], encoding mismatch");

    private static final MessageFormat HTTP_EX_3 = new MessageFormat(
            "Invalid encoding, CT-MIME [{0}] CT-Enc [{1}] BOM [{2}] XML guess [{3}] XML prolog [{4}], Invalid MIME");

    // InputStream is passed for XmlReaderException creation only
    private static String calculateRawEncoding(String bomEnc, String xmlGuessEnc, String xmlEnc, InputStream is) throws IOException {
        String encoding;
        if (bomEnc==null) {
            if (xmlGuessEnc==null || xmlEnc==null) {
                encoding = (_defaultEncoding == null) ? UTF_8 : _defaultEncoding;
            }
            else
            if (xmlEnc.equals(UTF_16) && (xmlGuessEnc.equals(UTF_16BE) || xmlGuessEnc.equals(UTF_16LE))) {
                encoding = xmlGuessEnc;
            }
            else {
                encoding = xmlEnc;
            }
        }
        else
        if (bomEnc.equals(UTF_8)) {
            if (xmlGuessEnc!=null && !xmlGuessEnc.equals(UTF_8)) {
                throw new XmlReaderException(RAW_EX_1.format(new Object[]{bomEnc,xmlGuessEnc,xmlEnc}),
                                             bomEnc,xmlGuessEnc,xmlEnc,is);
            }
            if (xmlEnc!=null && !xmlEnc.equals(UTF_8)) {
                throw new XmlReaderException(RAW_EX_1.format(new Object[]{bomEnc,xmlGuessEnc,xmlEnc}),
                                             bomEnc,xmlGuessEnc,xmlEnc,is);
            }
            encoding = UTF_8;
        }
        else
        if (bomEnc.equals(UTF_16BE) || bomEnc.equals(UTF_16LE)) {
            if (xmlGuessEnc!=null && !xmlGuessEnc.equals(bomEnc)) {
                throw new IOException(RAW_EX_1.format(new Object[]{bomEnc,xmlGuessEnc,xmlEnc}));
            }
            if (xmlEnc!=null && !xmlEnc.equals(UTF_16) && !xmlEnc.equals(bomEnc)) {
                throw new XmlReaderException(RAW_EX_1.format(new Object[]{bomEnc,xmlGuessEnc,xmlEnc}),
                                             bomEnc,xmlGuessEnc,xmlEnc,is);
            }
            encoding =bomEnc;
        }
        else {
            throw new XmlReaderException(RAW_EX_2.format(new Object[]{bomEnc,xmlGuessEnc,xmlEnc}),
                                         bomEnc,xmlGuessEnc,xmlEnc,is);
        }
        return encoding;
    }

}
