/*
 * Copyright (C) 2013 Chuan-Zheng Lee
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

package net.czlee.debatekeeper

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TimePicker
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import net.czlee.debatekeeper.databinding.FragmentPrepTimeBellsEditBinding
import java.util.Locale

/**
 * This fragment allows the user to edit the preparation time bells.
 *
 * The fragment does all its editing through an instance of [PrepTimeBellsManager].
 * The implementation for loading and saving the edits to the SharedPreferences is
 * left to the [PrepTimeBellsManager].
 *
 * @author Chuan-Zheng Lee
 * @since  2013-02-02
 */
class PrepTimeBellsEditFragment : Fragment() {

    private lateinit var mViewBinding: FragmentPrepTimeBellsEditBinding

    private lateinit var mPtbm: PrepTimeBellsManager

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    class DialogAddOrEditBellFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return getAddOrEditBellDialog()
        }

        @Suppress("deprecation")
        private fun getAddOrEditBellDialog(): Dialog {
            val activity = requireActivity()
            val parent = parentFragment as PrepTimeBellsEditFragment

            val builder = AlertDialog.Builder(activity)

            @SuppressLint("InflateParams")
            val content = activity.layoutInflater.inflate(R.layout.add_prep_time_bell, null)

            // Take note of the form elements
            val typeSpinner =
                    content.findViewById<Spinner>(R.id.addPrepTimeBellDialog_typeSpinner)
            val timePicker =
                    content.findViewById<TimePicker>(R.id.addPrepTimeBellDialog_timePicker)
            val editText = content.findViewById<EditText>(R.id.addPrepTimeBellDialog_editText)

            // Format the form elements
            timePicker.setIs24HourView(true)
            val typesAdapter = ArrayAdapter.createFromResource(activity,
                    R.array.prepTimeBellsEditor_editBellDialog_types,
                    android.R.layout.simple_spinner_item)
            typesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            typeSpinner.adapter = typesAdapter

            typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int,
                                            id: Long) {
                    // Show either the EditText or TimePicker, whichever is appropriate
                    if (pos == ADD_PREP_TIME_BELL_TYPE_PERCENTAGE) {
                        // This is "percentage through prep time"
                        editText.visibility = View.VISIBLE
                        timePicker.visibility = View.GONE
                    } else {
                        editText.visibility = View.GONE
                        timePicker.visibility = View.VISIBLE
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // Do nothing.
                }
            }

            val title: String
            val confirmButtonText: String
            val confirmOnClickListener: DialogInterface.OnClickListener

            // Prepare the dialog fields
            when (tag) {
                DIALOG_TAG_ADD_BELL -> {
                    prepareAddBellDialogView(content)

                    title = getString(R.string.prepTimeBellsEditor_addBellDialog_title)
                    confirmButtonText =
                            getString(R.string.prepTimeBellsEditor_addBellDialog_confirmButton)
                    confirmOnClickListener = parent.getAddBellDialogOnClickListener()
                }
                DIALOG_TAG_EDIT_BELL -> {
                    prepareEditBellDialogView(content)
                    val index = requireArguments().getInt(KEY_INDEX)

                    title = getString(R.string.prepTimeBellsEditor_editBellDialog_title,
                            parent.mPtbm.getBellDescription(index))
                    confirmButtonText =
                            getString(R.string.prepTimeBellsEditor_editBellDialog_confirmButton)
                    confirmOnClickListener = parent.getEditBellDialogOnClickListener(index)
                }
                else -> {
                    Log.e("DialogAddOrEditBellFrag", "Unrecognised tag: $tag")

                    title = getString(R.string.prepTimeBellsEditor_addBellDialog_title)
                    confirmButtonText =
                            getString(R.string.prepTimeBellsEditor_addBellDialog_confirmButton)
                    confirmOnClickListener =
                            DialogInterface.OnClickListener { dialog, _ -> dialog.cancel() }
                }
            }

            // When the text field gains focus, select all
            builder.setTitle(title)
                    .setView(content)
                    .setCancelable(true)
                    .setPositiveButton(confirmButtonText, confirmOnClickListener)
                    .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }

            return builder.create()
        }

        @Suppress("deprecation")
        private fun prepareAddBellDialogView(view: View) {
            val timePicker = view.findViewById<TimePicker>(R.id.addPrepTimeBellDialog_timePicker)
            val editText = view.findViewById<EditText>(R.id.addPrepTimeBellDialog_editText)

            // Defaults
            timePicker.currentHour = DEFAULT_MINUTES
            timePicker.currentMinute = DEFAULT_SECONDS
            editText.setText(DEFAULT_PERCENTAGE_TEXT)
        }

        /**
         * @param view the [View] to be prepared
         */
        @Suppress("deprecation")
        private fun prepareEditBellDialogView(view: View) {
            val args = requireArguments()

            val typeSpinner = view.findViewById<Spinner>(R.id.addPrepTimeBellDialog_typeSpinner)
            val timePicker = view.findViewById<TimePicker>(R.id.addPrepTimeBellDialog_timePicker)
            val editText = view.findViewById<EditText>(R.id.addPrepTimeBellDialog_editText)

            // Defaults
            timePicker.currentHour = DEFAULT_MINUTES
            timePicker.currentMinute = DEFAULT_SECONDS
            editText.setText(DEFAULT_PERCENTAGE_TEXT)

            // Populate the fields with the current values
            when (args.getString(PrepTimeBellsManager.KEY_TYPE)) {
                PrepTimeBellsManager.VALUE_TYPE_START -> {
                    typeSpinner.setSelection(ADD_PREP_TIME_BELL_TYPE_START)
                    val time = args.getLong(PrepTimeBellsManager.KEY_TIME)
                    timePicker.currentHour = (time / 60).toInt()
                    timePicker.currentMinute = (time % 60).toInt()
                }
                PrepTimeBellsManager.VALUE_TYPE_FINISH -> {
                    typeSpinner.setSelection(ADD_PREP_TIME_BELL_TYPE_FINISH)
                    val time = args.getLong(PrepTimeBellsManager.KEY_TIME)
                    timePicker.currentHour = (time / 60).toInt()
                    timePicker.currentMinute = (time % 60).toInt()
                }
                PrepTimeBellsManager.VALUE_TYPE_PROPORTIONAL -> {
                    typeSpinner.setSelection(ADD_PREP_TIME_BELL_TYPE_PERCENTAGE)
                    val proportion = args.getDouble(PrepTimeBellsManager.KEY_PROPORTION)
                    editText.setText((proportion * 100).toString())
                }
            }
        }
    }

    class DialogClearBellsFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

            val activity = requireActivity()
            val parent = parentFragment as PrepTimeBellsEditFragment

            val builder = AlertDialog.Builder(activity)

            builder.setTitle(R.string.prepTimeBellsEditor_clearAllDialog_title)
                    .setMessage("")
                    .setCancelable(true)
                    .setPositiveButton(
                            R.string.prepTimeBellsEditor_clearAllDialog_confirmButton) { _, _ ->
                        val spareFinish = parent.mPtbm.hasBellsOtherThanFinish()
                        parent.mPtbm.deleteAllBells(spareFinish)
                        parent.refreshBellsList()
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }

            val dialog = builder.create()

            val messageResId =
                    if (parent.mPtbm.hasFinishBell() && parent.mPtbm.hasBellsOtherThanFinish())
                        R.string.prepTimeBellsEditor_clearAllDialog_message_withFinishBell
                    else
                        R.string.prepTimeBellsEditor_clearAllDialog_message_noFinishBell
            dialog.setMessage(getString(messageResId))

            return dialog
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val info = menuInfo as AdapterContextMenuInfo
        val title = getString(R.string.prepTimeBellsEditor_contextMenu_header,
                mPtbm.getBellDescription(info.position))
        menu.setHeaderTitle(title)
        val inflater = requireActivity().menuInflater
        inflater.inflate(R.menu.prep_time_bells_list_context, menu)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        mViewBinding = FragmentPrepTimeBellsEditBinding.inflate(inflater, container, false)
        return mViewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        // Open the preferences file and instantiate the PrepTimeBellsManager
        val prefs = context.getSharedPreferences(
                PrepTimeBellsManager.PREP_TIME_BELLS_PREFERENCES_NAME, Context.MODE_PRIVATE)
        mPtbm = PrepTimeBellsManager(context)
        mPtbm.loadFromPreferences(prefs)

        // Populate the list
        refreshBellsList()

        // Set the OnClickListeners
        mViewBinding.prepTimeBellsEditorAddBellButton.setOnClickListener {
            // Generate the "add bell" dialog
            val fragment = DialogAddOrEditBellFragment()
            fragment.show(childFragmentManager, DIALOG_TAG_ADD_BELL)
        }
        mViewBinding.prepTimeBellsEditorClearAllButton.setOnClickListener {
            // Generate the "are you sure?" dialog
            val fragment = DialogClearBellsFragment()
            fragment.show(childFragmentManager, DIALOG_TAG_CLEAR_ALL_BELLS)
        }

        // Register a context menu for the bells list items
        registerForContextMenu(mViewBinding.prepTimeBellsEditorBellsList)

        // Register for the list to show a toast on non-long click
        mViewBinding.prepTimeBellsEditorBellsList.setOnItemClickListener { _, _, _, _ ->
            Toast.makeText(context,
                    R.string.prepTimeBellsEditor_contextMenu_tip, Toast.LENGTH_SHORT).show()
        }

        // Set the action bar
        mViewBinding.prepTimeBellsEditorToolbar.setNavigationOnClickListener {
            NavHostFragment.findNavController(this).navigateUp()
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        @Suppress("deprecation")
        val info = item.menuInfo as AdapterContextMenuInfo
        when (item.itemId) {
            R.id.prepTimeBellsEditor_contextMenu_edit -> {
                val args = mPtbm.getBellBundle(info.position)
                args.putInt(KEY_INDEX, info.position)
                val fragment = DialogAddOrEditBellFragment()
                fragment.arguments = args
                fragment.show(childFragmentManager, DIALOG_TAG_EDIT_BELL)
                return true
            }
            R.id.prepTimeBellsEditor_contextMenu_delete -> {
                mPtbm.deleteBell(info.position)
                refreshBellsList()
                return true
            }
        }
        return super.onContextItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        val prefs = requireContext().getSharedPreferences(
                PrepTimeBellsManager.PREP_TIME_BELLS_PREFERENCES_NAME, Context.MODE_PRIVATE)
        mPtbm.saveToPreferences(prefs)
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    @SuppressLint("DefaultLocale")
    private fun refreshBellsList() {
        val descriptions = mPtbm.getBellDescriptions()

        // Convert descriptions to sentence case
        val descriptionsIterator = descriptions.listIterator()
        while (descriptionsIterator.hasNext()) {
            val description = descriptionsIterator.next()
            descriptionsIterator.set(
                    description.substring(0, 1).uppercase(Locale.getDefault())
                            + description.substring(1))
        }

        val view = mViewBinding.prepTimeBellsEditorBellsList
        val adapter = ArrayAdapter(requireContext(),
                android.R.layout.simple_list_item_1, descriptions)
        view.adapter = adapter

        // Disable the "clear" button if there is nothing to clear
        mViewBinding.prepTimeBellsEditorClearAllButton.isEnabled = mPtbm.hasBells()
    }

    private fun getAddBellDialogOnClickListener(): DialogInterface.OnClickListener {
        return DialogInterface.OnClickListener { dialogInterface, _ ->
            val dialog = dialogInterface as Dialog
            val newBundle = createBellBundleFromAddOrEditDialog(dialog)
            mPtbm.addFromBundle(newBundle)
            refreshBellsList()
        }
    }

    private fun getEditBellDialogOnClickListener(index: Int): DialogInterface.OnClickListener {
        return DialogInterface.OnClickListener { dialogInterface, _ ->
            val dialog = dialogInterface as Dialog
            val newBundle = createBellBundleFromAddOrEditDialog(dialog)
            mPtbm.replaceFromBundle(index, newBundle)
            refreshBellsList()
        }
    }

    companion object {
        private const val DIALOG_TAG_ADD_BELL = "add"
        private const val DIALOG_TAG_CLEAR_ALL_BELLS = "clr"
        private const val DIALOG_TAG_EDIT_BELL = "edit"

        private const val KEY_INDEX = "index"

        const val DEFAULT_MINUTES = 5
        const val DEFAULT_SECONDS = 0
        const val DEFAULT_PERCENTAGE_TEXT = "50"

        // These must match the strings in R.string.AddPrepTimeBellTypes
        // (in prep_time_bells_edit.xml)
        private const val ADD_PREP_TIME_BELL_TYPE_START = 0
        private const val ADD_PREP_TIME_BELL_TYPE_FINISH = 1
        private const val ADD_PREP_TIME_BELL_TYPE_PERCENTAGE = 2

        @Suppress("deprecation")
        private fun createBellBundleFromAddOrEditDialog(dialog: Dialog): Bundle {
            val typeSpinner = dialog.findViewById<Spinner>(R.id.addPrepTimeBellDialog_typeSpinner)
            val timePicker =
                    dialog.findViewById<TimePicker>(R.id.addPrepTimeBellDialog_timePicker)
            val editText = dialog.findViewById<EditText>(R.id.addPrepTimeBellDialog_editText)

            val typeSelected = typeSpinner.selectedItemPosition
            val bundle = Bundle()
            when (typeSelected) {
                ADD_PREP_TIME_BELL_TYPE_START, ADD_PREP_TIME_BELL_TYPE_FINISH -> {
                    // We're using this in hours and minutes, not minutes and seconds
                    val minutes = timePicker.currentHour
                    val seconds = timePicker.currentMinute
                    val time = minutes * 60L + seconds
                    if (typeSelected == ADD_PREP_TIME_BELL_TYPE_START)
                        bundle.putString(PrepTimeBellsManager.KEY_TYPE,
                                PrepTimeBellsManager.VALUE_TYPE_START)
                    else
                        bundle.putString(PrepTimeBellsManager.KEY_TYPE,
                                PrepTimeBellsManager.VALUE_TYPE_FINISH)
                    bundle.putLong(PrepTimeBellsManager.KEY_TIME, time)
                }
                ADD_PREP_TIME_BELL_TYPE_PERCENTAGE -> {
                    val text = editText.text
                    bundle.putString(PrepTimeBellsManager.KEY_TYPE,
                            PrepTimeBellsManager.VALUE_TYPE_PROPORTIONAL)
                    val percentage = text.toString().toDouble()
                    val value = percentage / 100
                    bundle.putDouble(PrepTimeBellsManager.KEY_PROPORTION, value)
                }
            }

            return bundle
        }
    }
}
