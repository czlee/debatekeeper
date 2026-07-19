/*
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

package net.czlee.debatekeeper.debateformat

import androidx.core.os.LocaleListCompat
import com.ibm.icu.util.LocaleMatcher
import com.ibm.icu.util.LocalePriorityList
import com.ibm.icu.util.ULocale

/**
 * Helper class to choose the 'best' language from an unordered list.
 * Used for XML 'lang' attribute support.
 */
class LanguageChooser {

    private val mLocaleList: LocalePriorityList? =
            buildLocalePriorityList(LocaleListCompat.getAdjustedDefault())

    /**
     * Choose the 'best' language from an unordered list.
     * @param languages Non-empty list with languages to choose from.
     * @return A language from `languages`.
     */
    fun choose(languages: List<String>): String? {
        // TODO: Should probably use Locale.lookupTag once we require minimum API level 26

        if (languages.isEmpty()) return null
        if (mLocaleList == null) return languages[0]

        // Parse languages into ULocales
        val localeObjs = Array(languages.size) { ULocale(languages[it]) }

        // Build locale list from ULocales
        var priorityListBuilder: LocalePriorityList.Builder? = null
        for (locale in localeObjs) {
            priorityListBuilder = priorityListBuilder?.add(locale)
                    ?: LocalePriorityList.add(locale)
        }
        val availableLangs = priorityListBuilder!!.build()
        val availableLocaleMatcher = LocaleMatcher(availableLangs)

        // Obtain best match
        val bestLang = availableLocaleMatcher.getBestMatch(mLocaleList)

        // Map back to original string
        for (i in localeObjs.indices) {
            if (bestLang === localeObjs[i]) return languages[i]
        }

        // Fallback just in case
        return languages[0]
    }

    /** Build a LocalePriorityList from a LocaleListCompat */
    private fun buildLocalePriorityList(locales: LocaleListCompat): LocalePriorityList? {
        if (locales.isEmpty) return null

        var priorityListBuilder: LocalePriorityList.Builder? = null
        for (i in 0 until locales.size()) {
            val locale = ULocale.forLocale(locales[i])
            priorityListBuilder = priorityListBuilder?.add(locale)
                    ?: LocalePriorityList.add(locale)
        }
        return priorityListBuilder!!.build()
    }
}
