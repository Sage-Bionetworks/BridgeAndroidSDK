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

import org.sagebionetworks.bridge.android.access.Resource.Status

/**
 * Strategy for handling BridgeAccessState values.
 */
interface BridgeAccessStrategy {

    companion object {
        fun handle(statusResource: Resource<BridgeAccessState>, strategy: BridgeAccessStrategy) {
            when (statusResource.status) {
                Status.LOADING -> {
                    statusResource.data?.let { strategy.onLoading(it) }
                }
                Status.ERROR -> {
                    statusResource.data?.let { strategy.onErrored(it, statusResource.message) }
                }
                Status.SUCCESS -> {
                    statusResource.data?.let {
                        when (it) {
                            BridgeAccessState.REQUIRES_APP_UPGRADE ->
                                strategy.onRequireAppUpgrade()
                            BridgeAccessState.REQUIRES_AUTHENTICATION ->
                                strategy.onRequireAuthentication()
                            BridgeAccessState.REQUIRES_CONSENT ->
                                strategy.onRequireConsent()
                            BridgeAccessState.ACCESS_GRANTED ->
                                strategy.onAccessGranted()
                        }
                    }
                }
            }
        }
    }

    /**
     * @param state BridgeAccessState that is being loaded
     */
    fun onLoading(state: BridgeAccessState)

    fun onRequireAppUpgrade()

    fun onRequireAuthentication()

    fun onRequireConsent()

    fun onAccessGranted()

    /**
     * @param state BridgeAccessState that was being loaded
     * @param message error message encountered while loading state
     */
    fun onErrored(state: BridgeAccessState, message: String?)
}