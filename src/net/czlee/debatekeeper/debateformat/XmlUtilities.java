package net.czlee.debatekeeper.debateformat;

import net.czlee.debatekeeper.R;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.content.res.Resources;

/**
 * Provides convenience functions for dealing with XML files.
 *
 * If constructed, it takes a {@link Resources} which it uses to retrieve resources.  Methods
 * that require these resources must be called from an instance of this class.  Other methods
 * are static (and obviously don't require an instance).
 *
 * @author Chuan-Zheng Lee
 * @since  2013-06-04
 */
public class XmlUtilities {

    Resources mResources;

    public XmlUtilities(Resources resources) {
        mResources = resources;
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Convenience function.  Finds the {@link Element} of the name given by a resource ID.
     * @param element an {@link Element}
     * @param tagNameResId a resource ID referring to a string
     * @return the {@link Element} if found, or <code>null</code> if no such element is found
     */
    public Element findElement(Element element, int tagNameResId) {
        String elemName = getString(tagNameResId);
        NodeList candidates = element.getElementsByTagName(elemName);
        return (Element) candidates.item(0);
    }

    /**
     * Convenience function.  Finds the text of the element of the name given by a resource ID.
     * @param element an {@link Element}
     * @param tagNameResId a resource ID referring to a string
     * @return the String if found, or <code>null</code> if no such element is found (if multiple
     * elements are found it just returns the text of the first)
     */
    public String findElementText(Element element, int tagNameResId) {
        element = findElement(element, tagNameResId);
        if (element == null) return null;
        else return element.getTextContent();
    }

    /**
     * Convenience function.  Finds all {@link Element}s of the name given by a resource ID.
     * @param element an {@link Element}
     * @param tagNameResId a resource ID referring to a string
     * @return a {@link NodeList}
     */
    public NodeList findAllElements(Element element, int tagNameResId) {
        String elemName = getString(tagNameResId);
        return element.getElementsByTagName(elemName);
    }

    /**
     * Convenience function.  Finds the text of the attribute of the name given by a resource ID.
     * @param element an {@link Element}
     * @param attrNameResId a resource ID referring to a string
     * @return a string, or the empty string if the attribute does not have a specified value
     */
    public String findAttributeText(Element element, int attrNameResId) {
        String attrName = getString(attrNameResId);
        return element.getAttribute(attrName);
    }

    /**
     * Convenience function.  Finds the text of the attribute of the name given by a resource ID
     * and converts the time string to a number of seconds.
     * @param element an {@link Element}
     * @param attrNameResId a resource ID referring to a string
     * @return a Long, or the <code>null</code> if the attribute does not have a specified value
     * @throws NumberFormatException
     */
    public Long findAttributeAsTime(Element element, int attrNameResId) throws NumberFormatException {
        String text = findAttributeText(element, attrNameResId);
        if (text.length() == 0) return null;
        long seconds;
        try {
            seconds = timeStr2Secs(text);
        } catch (NumberFormatException e) {
            throw new NumberFormatException(getString(R.string.xml2error_invalidTime, text));
        }
        return seconds;
    }

    /**
     * Convenience function.  Finds the text of the attribute of the name given by a resource ID
     * and converts the time string to a number of seconds.
     * @param element an {@link Element}
     * @param attrNameResId a resource ID referring to a string
     * @return a Long, or the <code>null</code> if the attribute does not have a specified value
     * @throws NumberFormatException
     */
    public Integer findAttributeAsInteger(Element element, int attrNameResId) throws NumberFormatException {
        String text = findAttributeText(element, attrNameResId);
        if (text.length() == 0) return null;
        return Integer.parseInt(text);
    }

    /**
     * Convenience function.  Examines the attribute of the name given by a resource ID and
     * determines if it is "true".
     * @param element an {@link Element}
     * @param attrNameResId a resource ID referring to a string
     * @return <code>true</code> if the attribute's value is "true" (case-insensitive),
     * <code>false</code> otherwise
     */
    public boolean isAttributeTrue(Element element, int attrNameResId) {
        String text = findAttributeText(element, attrNameResId);
        return text.equalsIgnoreCase(getString(R.string.xml2attrValue_common_true));
    }

    /**
     * Converts a String in the format 00:00 to a long, being the number of seconds
     * @param s the String
     * @return the total number of seconds (minutes + seconds * 60)
     * @throws NumberFormatException
     */
    public static long timeStr2Secs(String s) throws NumberFormatException {
        long seconds = 0;
        String parts[] = s.split(":", 2);
        switch (parts.length){
        case 2:
            long minutes = Long.parseLong(parts[0]);
            seconds += minutes * 60;
            seconds += Long.parseLong(parts[1]);
            break;
        case 1:
            seconds = Long.parseLong(parts[0]);
            break;
        default:
            throw new NumberFormatException();
        }
        return seconds;
    }

    /**
     * @param a
     * @param b
     * @return 1 if a > b, 0 if a == b, 1 if a < b
     */
    public static int compareSchemaVersions(String a, String b) throws IllegalArgumentException {
        int[] a_int = versionToIntArray(a);
        int[] b_int = versionToIntArray(b);
        int min_length = (a_int.length > b_int.length) ? b_int.length : a_int.length;
        for (int i = 0; i < min_length; i++) {
            if (a_int[i] > b_int[i]) return 1;
            if (a_int[i] < b_int[i]) return -1;
        }
        return 0;
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************
    private String getString(int resId, Object... formatArgs) {
        return mResources.getString(resId, formatArgs);
    }

    /**
     * @param version
     * @return an integer array
     */
    private static int[] versionToIntArray(String version) throws IllegalArgumentException {
        int[] result = new int[2];
        String[] parts = version.split("\\.", 2);
        if (parts.length != 2)
            throw new IllegalArgumentException("version must be in the form 'a.b' where a and b are numbers");
        for (int i = 0; i < 2; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("version must be in the form 'a.b' where a and b are numbers");
            }
        }
        return result;
    }

}
