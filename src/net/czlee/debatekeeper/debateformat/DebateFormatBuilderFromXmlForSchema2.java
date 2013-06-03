package net.czlee.debatekeeper.debateformat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.czlee.debatekeeper.R;
import net.czlee.debatekeeper.debateformat.PeriodInfoManager.PeriodInfoException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.content.Context;
import android.util.Log;

/**
 * DebateFormatBuilderForSchema2 provides mechanisms for building DebateFormats.
 *
 * <p>While "schema 2" refers to the XML schema, this class does not actually handle the XML.
 * It merely provides building methods for other classes to use to construct a {@link DebateFormat}.
 * The salient features of schema 2, that are not true for schema 1, are:</p>
 * <ul>
 * <li>"Resources" do not exist in schema 2 (they did in schema 1).</li>
 * <li>Most formats in schema 2 will just use the global period types; for schema 1 all
 * period types had to be defined locally.</li>
 * </ul>
 *
 * <p>It is expected that other classes will be used to interface between a means of storing
 * information about debate formats, and this class.  For example, the {@link DebateFormatBuilderFromXmlForSchema2}
 * class interfaces between XML files and this class.</p>
 *
 * <p>Because there is no longer any concept of "resources", there is nothing that a builder needs
 * to implement that isn't already taken care of by {@link DebateFormat}.  Therefore, unlike for
 * schema 1, this builder creates formats directly from XML files, without an intermediate class
 * to provide mechanisms for handling resources.  It does, however, have to implement global
 * period types.</p>
 *
 * <p>Unlike {@link DebateFormatBuilderForSchema1}, this class does not do error checking that is
 * handled by the schema.  It does do error checking that is not handled by the schema.</p>
 *
 * @author Chuan-Zheng Lee
 *
 */
public class DebateFormatBuilderFromXmlForSchema2 {

    private final Context                       mContext;
    protected final DocumentBuilderFactory      mDocumentBuilderFactory;
    protected final PeriodInfoManager           mPeriodInfoManager;
    private final ArrayList<String>             mErrorLog = new ArrayList<String>();

    private final XmlUtilities xu;

    /**
     * Constructor.
     */
    public DebateFormatBuilderFromXmlForSchema2(Context context) {
        super();
        mContext = context;
        mDocumentBuilderFactory = DocumentBuilderFactory.newInstance();
        mPeriodInfoManager = new PeriodInfoManager(context);
        xu = new XmlUtilities(context.getResources());

    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************


    //******************************************************************************************
    // Public methods
    //******************************************************************************************
    /**
     * @param is an {@link InputStream} being the XML file to parse into a {@link DebateFormat}
     * @return the completed {@link DebateFormat}
     * @throws IOException if there was an IO error with the <code>InputStream</code>
     * @throws SAXException if thrown by the XML parser
     */
    public DebateFormat buildFromXml(InputStream is)
            throws SAXException, IOException {

        DebateFormat df = new DebateFormat();
        Document doc = getDocumentFromInputStream(is);
        Element root = doc.getDocumentElement();

        // 1. Name
        String name = xu.findElementText(root, R.string.xml2elemName_name);
        df.setName(name);

        // 2. If there are period types in this format, deal with them first.  We'll need to
        // store them somewhere useful in the meantime.
        Element periodTypes = xu.findElement(root, R.string.xml2elemName_periodTypes);
        if (periodTypes != null) {
            NodeList periodTypeElements = xu.findAllElements(periodTypes, R.string.xml2elemName_periodType);
            for (int i = 0; i < periodTypeElements.getLength(); i++) {
                Element periodType = (Element) periodTypeElements.item(i);
                try {
                    mPeriodInfoManager.addPeriodInfoFromElement(periodType);
                } catch (PeriodInfoException e) {
                    logXmlError(e);
                }
            }
        }

        // 3. Prep time
        // TODO

        // 4. Speech formats
        Element speechFormats = xu.findElement(root, R.string.xml2elemName_speechFormats);
        NodeList speechFormatElements = xu.findAllElements(speechFormats, R.string.xml2elemName_speechFormat);
        for (int i = 0; i < speechFormatElements.getLength(); i++) {
            Element speechFormatElement = (Element) speechFormatElements.item(i);
            SpeechFormat sf = createSpeechFormatFromElement(speechFormatElement);
            if (sf == null) continue;
            String reference = sf.getReference();
            if (reference == null) continue;
            df.addSpeechFormat(reference, sf);
        }

        return null;
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    private Document getDocumentFromInputStream(InputStream is)
            throws SAXException, IOException {

        DocumentBuilder builder;
        try {
            // There should never be a problem creating this DocumentBuilder, ever.
            builder = mDocumentBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            Log.wtf(this.getClass().getSimpleName(), "Error creating document builder");
            // After this, the app is pretty much guaranteed to crash.
            return null;
        }

        // Parse the file
        return builder.parse(is);

    }

    /**
     * Creates a {@link SpeechFormat} derived from an {@link Element}
     * @param element an {@link Element} object
     * @return a {@link SpeechFormat}, may return <code>null</code> if there was an error preventing
     * the object from being created
     */
    private SpeechFormat createSpeechFormatFromElement(Element element) {

        String reference = xu.findAttributeText(element, R.string.xml2attrName_common_ref);

        Long length;
        try {
            length = xu.findAttributeAsTime(element, R.string.xml2attrName_controlledTimeLength);
        } catch (NumberFormatException e) {
            logXmlError(e);
            return null;
        }
        if (length == null) return null;

        SpeechFormat sf = new SpeechFormat(reference, length);

        // If there is a first period specified, and it is not "#stay", set it accordingly
        String firstPeriod = xu.findAttributeText(element, R.string.xml2attrName_controlledTimeFirstPeriod);
        if (firstPeriod != null) {
            if (!firstPeriod.equalsIgnoreCase(getString(R.string.xml1attrValue_common_stay))) {
                PeriodInfo npi = mPeriodInfoManager.getPeriodInfo(firstPeriod);
                if (npi == null)
                    logXmlError(R.string.dfb2error_periodInfoNotFound, firstPeriod);
                else
                    sf.setFirstPeriodInfo(npi);
            }
        }

        // Add all the bells
        NodeList bellElements = xu.findAllElements(element, R.string.xml2elemName_bell);
        for (int i = 0; i < bellElements.getLength(); i++) {
            Element bellElement = (Element) bellElements.item(i);
            BellInfo bi = createBellInfoFromElement(bellElement, length);
            if (bi == null) continue;
            sf.addBellInfo(bi);
        }

        return sf;
    }

    /**
     * Creates a {@link BellInfo} derived from an {@link Element}
     * @param element an {@link Element} object
     * @param speechFinishTime the length of the speech, used if the bell is to be at the finish
     * time of the speech
     * @return a {@link BellInfo}, may return <code>null</code> if there was an error preventing
     * the object from being created
     */
    private BellInfo createBellInfoFromElement(Element element, long speechFinishTime) {

        long time;
        String timeStr = xu.findAttributeText(element, R.string.xml2attrName_bell_time);
        if (timeStr.equalsIgnoreCase(getString(R.string.xml2attrValue_bell_time_finish)))
            time = speechFinishTime;
        else {
            try {
                time = XmlUtilities.timeStr2Secs(timeStr);
            } catch (NumberFormatException e) {
                logXmlError(R.string.xml2error_invalidTime, timeStr);
                return null;
            }
        }

        Integer timesToPlay = xu.findAttributeAsInteger(element, R.string.xml2attrName_bell_number);
        if (timesToPlay == null) timesToPlay = 1;

        BellInfo bi = new BellInfo(time, timesToPlay);

        // If there is a next period specified, and it is not "#stay", set it accordingly
        String nextPeriod = xu.findAttributeText(element, R.string.xml2attrName_bell_nextPeriod);
        if (nextPeriod != null) {
            if (!nextPeriod.equalsIgnoreCase(getString(R.string.xml1attrValue_common_stay))) {
                PeriodInfo npi = mPeriodInfoManager.getPeriodInfo(nextPeriod);
                if (npi == null)
                    logXmlError(R.string.dfb2error_periodInfoNotFound, nextPeriod);
                else
                    bi.setNextPeriodInfo(npi);
            }
        }

        boolean pauseOnBell = xu.isAttributeTrue(element, R.string.xml2attrName_bell_pauseOnBell);
        bi.setPauseOnBell(pauseOnBell);

        return bi;

    }

    private String getString(int resId, Object... formatArgs) {
        return mContext.getString(resId, formatArgs);
    }

    // Error log methods

    private void addToErrorLog(String message) {
        String bullet = "• ";
        String line   = bullet.concat(message);
        mErrorLog.add(line);
    }

    /**
     * Logs an XML-related error from an exception.
     * @param e the Exception
     */
    private void logXmlError(Exception e) {
        addToErrorLog(e.getMessage());
        Log.e("logXmlError", e.getMessage());
    }

    /**
     * Logs an XML-related error from a string resource.
     * @param resId the resource ID of the string resource
     */
    private void logXmlError(int resId) {
        addToErrorLog(mContext.getString(resId));
        Log.e("logXmlError", mContext.getString(resId));
    }

    /**
     * Logs an XML-related error from a string resource and formats according to
     * <code>String.format</code>
     * @param resId the resource ID of the string resource
     * @param formatArgs arguments to pass to <code>String.format</code>
     */
    private void logXmlError(int resId, Object... formatArgs) {
        addToErrorLog(mContext.getString(resId, formatArgs));
        Log.e("logXmlError", mContext.getString(resId, formatArgs));
    }



}
