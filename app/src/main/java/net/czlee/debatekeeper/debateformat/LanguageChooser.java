/*
 * Copyright (C) 2018-2021 Frank Richter, Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the GNU
 * General Public Licence version 3 (GPLv3).  You can redistribute and/or modify
 * it under the terms of the GPLv3, and you must not use this file except in
 * compliance with the GPLv3.
 *
 * This app is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU General Public Licence for more details.
 *
 * You should have received a copy of the GNU General Public Licence along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.czlee.debatekeeper.debateformat;

import androidx.core.os.LocaleListCompat;

import com.ibm.icu.util.LocaleMatcher;
import com.ibm.icu.util.LocalePriorityList;
import com.ibm.icu.util.ULocale;

import java.util.List;

/**
 * Helper class to choose the 'best' language from an unordered list.
 * Used for XML 'lang' attribute support.
 */
public class LanguageChooser {
    private final LocalePriorityList mLocaleList;

    public LanguageChooser() {
        mLocaleList = buildLocalePriorityList(LocaleListCompat.getAdjustedDefault());
    }

    /**
     * Choose the 'best' language from an unordered list.
     * @param languages Non-empty array with languages to choose from.
     * @return A language from @a languages.
     */
    public String choose (List<String> languages) {
        // TODO: Should probably use Locale.lookupTag once we require minimum API level 26

        if (languages.isEmpty()) return null;
        if (mLocaleList == null) return languages.get(0);

        // Parse languages into ULocales
        ULocale[] localeObjs = new ULocale[languages.size()];
        for (int i = 0; i < languages.size(); i++) {
            localeObjs[i] = new ULocale(languages.get(i));
        }

        // Build locale list from ULocales
        LocalePriorityList.Builder priorityListBuilder = null;
        for (ULocale locale : localeObjs) {
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
            if (bestLang == localeObjs[i]) return languages.get(i);
        }

        // Fallback just in case
        return languages.get(0);
    }

    /// Build a LocalePriorityList from a LocaleListCompat
    private LocalePriorityList buildLocalePriorityList(LocaleListCompat locales) {
        if (locales.isEmpty()) return null;

        LocalePriorityList.Builder priorityListBuilder = null;
        for (int i = 0; i < locales.size(); i++) {
            ULocale locale = ULocale.forLocale(locales.get(i));
            if (priorityListBuilder == null)
                priorityListBuilder = LocalePriorityList.add(locale);
            else
                priorityListBuilder = priorityListBuilder.add(locale);
        }
        assert priorityListBuilder != null;
        return priorityListBuilder.build();
    }
}
