/*
 * BSD 3-Clause License
 *
 * Copyright 2018  Sage Bionetworks. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2.  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3.  Neither the name of the copyright holder(s) nor the names of any contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. No license is granted to the trademarks of
 * the copyright holders even if such marks are included in this software.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sagebionetworks.bridge.android.access

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import dagger.android.support.DaggerFragment
import org.sagebionetworks.bridge.android.R
import org.slf4j.LoggerFactory
import javax.inject.Inject

/**
 * A fragment for functionality that is gated by Bridge access rules.
 */
abstract class BridgeAccessFragment : BridgeAccessStrategy, DaggerFragment() {

    private val logger = LoggerFactory.getLogger(BridgeAccessFragment::class.java)

    @Inject
    lateinit var bridgeAccessViewModelFactory: BridgeAccessViewModel.Factory

    private lateinit var bridgeAccessViewModel: BridgeAccessViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.osb_access_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        bridgeAccessViewModel = ViewModelProviders.of(requireActivity(), bridgeAccessViewModelFactory)
                .get(BridgeAccessViewModel::class.java)

        bridgeAccessViewModel.bridgeAccessStatus.observe(this,
                Observer<Resource<BridgeAccessState>> { state ->
                    state?.let {
                        BridgeAccessStrategy.handle(it, this)
                    }
                })
    }

    override fun onResume() {
        super.onResume()
        logger.debug("onResume")

        bridgeAccessViewModel.checkAccess()
    }

    override fun onPause() {
        super.onPause()

        logger.debug("onPause")
    }

    override fun onRequireAppUpgrade() {
        val container = view?.findViewById<FrameLayout>(R.id.container)
        layoutInflater.inflate(R.layout.osb_app_upgrade_required, container, true)
    }

    override fun onLoading(state: BridgeAccessState) {
        logger.debug("Loading bridge access state: {}", state)
    }

    override fun onErrored(state: BridgeAccessState, message: String?) {
        logger.warn("Error loading bridge access state: {}, message: {}", state, message)
    }
}
