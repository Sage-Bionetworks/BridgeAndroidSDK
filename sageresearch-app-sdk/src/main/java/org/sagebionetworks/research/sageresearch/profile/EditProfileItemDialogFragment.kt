package org.sagebionetworks.research.sageresearch.profile

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import kotlinx.android.synthetic.main.dialog_edit_profileitem.view.*
import org.sagebionetworks.research.sageresearch_app_sdk.R



class EditProfileItemDialogFragment: DialogFragment() {

    companion object {

        const val VALUE_KEY = "value_key"
        const val ITEM_KEY = "item_key"

        fun newInstance(value: String, profileItemKey: String, listener: Fragment): EditProfileItemDialogFragment {
            val fragment = EditProfileItemDialogFragment()
            val args = Bundle()
            args.putString(VALUE_KEY, value)
            args.putString(ITEM_KEY, profileItemKey)
            fragment.setArguments(args)
            fragment.setTargetFragment(listener, -1)
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            // Get the layout inflater
            val inflater = requireActivity().layoutInflater;

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            val layout = inflater.inflate(R.layout.dialog_edit_profileitem, null)
            layout.editText.setText(arguments?.getString(VALUE_KEY))
            builder.setView(layout)
                    // Add action buttons
                    .setPositiveButton(R.string.rsb_BUTTON_SAVE,
                            DialogInterface.OnClickListener { dialog, id ->
                                if (targetFragment is EditProfileItemDialogListener) {
                                    val listener: EditProfileItemDialogListener? = targetFragment as? EditProfileItemDialogListener
                                    listener?.saveEditDialogValue(layout.editText.text.toString(), arguments!!.getString(ITEM_KEY))
                                }
                            })
                    .setNegativeButton(R.string.rsb_BUTTON_CANCEL,
                            DialogInterface.OnClickListener { dialog, id ->
                                getDialog().cancel()
                            })
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

}

interface EditProfileItemDialogListener {
    fun saveEditDialogValue(value: String, profileItemKey: String)
}