package net.czlee.debatekeeper.debateformat;

import androidx.core.os.LocaleListCompat;

import com.ibm.icu.util.LocaleMatcher;
import com.ibm.icu.util.LocalePriorityList;
import com.ibm.icu.util.ULocale;

/**
 * Helper class to choose the 'best' language from an unordered list.
 * Used for XML 'lang' attribute support.
 */
class LanguageChooser {
    private LocalePriorityList mLocaleList;

    public LanguageChooser() {
        mLocaleList = BuildLocalePriorityList(LocaleListCompat.getAdjustedDefault());
    }

    /**
     * Choose the 'best' language from an unordered list.
     * @param languages Non-empty array with languages to choose from.
     * @return A language from @a languages.
     */
    public String choose (String[] languages) {
        // TODO: Should probably use Locale.lookupTag once we require minimum API level 26

        // Parse languages into ULocales
        ULocale[] localeObjs = new ULocale[languages.length];
        for (int i = 0; i < languages.length; i++) {
            localeObjs[i] = new ULocale(languages[i]);
        }

        // Build locale list from ULocales
        LocalePriorityList.Builder priorityListBuilder = null;
        for (int i = 0; i < localeObjs.length; i++) {
            ULocale locale = localeObjs[i];
            if (priorityListBuilder == null)
                priorityListBuilder = LocalePriorityList.add(locale);
            else
                priorityListBuilder = priorityListBuilder.add(locale);
        }
        LocalePriorityList availableLangs = priorityListBuilder.build();
        LocaleMatcher availableLocaleMatcher = new LocaleMatcher(availableLangs);

        // Obtain best match
        ULocale bestLang = availableLocaleMatcher.getBestMatch(mLocaleList);

        // Map back to original string
        for (int i = 0; i < localeObjs.length; i++) {
            if (bestLang == localeObjs[i]) return languages[i];
        }

        // Fallback just in case
        return languages[0];
    }

    /// Build a LocalePriorityList from a LocaleListCompat
    private LocalePriorityList BuildLocalePriorityList (LocaleListCompat locales) {
        LocalePriorityList.Builder priorityListBuilder = null;
        for (int i = 0; i < locales.size(); i++) {
            ULocale locale = ULocale.forLocale(locales.get(i));
            if (priorityListBuilder == null)
                priorityListBuilder = LocalePriorityList.add(locale);
            else
                priorityListBuilder = priorityListBuilder.add(locale);
        }
        return priorityListBuilder.build();
    }
}
