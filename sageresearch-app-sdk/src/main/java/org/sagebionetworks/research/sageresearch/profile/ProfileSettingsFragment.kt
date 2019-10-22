package org.sagebionetworks.research.sageresearch.profile

import android.arch.lifecycle.Observer
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.view.ViewCompat
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.fragment_profilesettings_list.*
import kotlinx.android.synthetic.main.fragment_profilesettings_list.view.*
import org.sagebionetworks.bridge.rest.model.SurveyReference
import org.sagebionetworks.research.mobile_ui.show_step.view.SystemWindowHelper
import org.sagebionetworks.research.sageresearch.profile.ProfileSettingsRecyclerViewAdapter.Companion.VIEW_TYPE_SECTION
import org.sagebionetworks.research.sageresearch_app_sdk.R


abstract class ProfileSettingsFragment : OnListInteractionListener, Fragment()  {

    private var profileKey = "ProfileDataSource" //Initialized to the default key
    private var isMainView = true;

    protected lateinit var profileViewModel: ProfileViewModel

    override abstract fun launchSurvey(surveyReference: SurveyReference)
    abstract fun newInstance(profileKey: String, isMainView: Boolean): ProfileSettingsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            profileKey = it.getString(ARG_PROFILE_KEY)
            isMainView = it.getBoolean(ARG_IS_MAIN_VIEW, true)
        }
    }

    abstract fun loadProfileViewModel(): ProfileViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        profileViewModel = loadProfileViewModel()

        val view = inflater.inflate(R.layout.fragment_profilesettings_list, container, false)

        if (!isMainView) {
            view.back_icon.visibility = View.VISIBLE
            view.textView.visibility = View.INVISIBLE
            view.settings_icon.visibility = View.GONE
            view.back_icon.setOnClickListener {
                activity?.onBackPressed()
            }
        }

        val topListener = SystemWindowHelper.getOnApplyWindowInsetsListener(SystemWindowHelper.Direction.TOP)
        ViewCompat.setOnApplyWindowInsetsListener(view.textView, topListener)

        // Set the adapter
        if (view.list is RecyclerView) {
            with(view.list) {
                layoutManager = LinearLayoutManager(context)

                val divider = object : DividerItemDecoration(this.getContext(), DividerItemDecoration.VERTICAL) {

                    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                        val pos = getChildAdapterPosition(view)
                        if (parent.adapter?.getItemViewType(pos) == VIEW_TYPE_SECTION) {
                            if (pos == 0) {
                                outRect.set(0, 0, 0, 0)
                            } else {
                                outRect.set(0, 50, 0, 0)
                            }
                        } else {
                            super.getItemOffsets(outRect, view, parent, state)
                        }
                    }

                }
                val drawable = this.context!!.resources.getDrawable(R.drawable.form_step_divider)
                divider.setDrawable(drawable)
                this.addItemDecoration(divider)

            }
            profileViewModel.profileData(profileKey).observe(this, Observer { t -> val loader = t
                val a = loader
                view.list.adapter = ProfileSettingsRecyclerViewAdapter(loader!!, this)
            })
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        settings_icon.setOnClickListener {
            val settingsFragment = newInstance("SettingsDataSource", false)
            addChildFragmentOnTop(settingsFragment, "settingsFragment")
        }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    /**
     * Adds a child fragment on top of this fragment and adds this fragment to the back stack with the provided tag.
     * @param childFragment The fragment to add on top of this fragment.
     * @param tag The tag for this fragment on the back stack.
     */
    fun addChildFragmentOnTop(childFragment: Fragment, tag: String?) {
        fragmentManager!!
                .beginTransaction()
                .detach(this)
                .add((this.view!!.parent as ViewGroup).id, childFragment)
                .addToBackStack(null)
                .commit()
    }

    companion object {

        const val ARG_PROFILE_KEY = "profile_key"
        const val ARG_IS_MAIN_VIEW = "is_main_view"

    }
}
