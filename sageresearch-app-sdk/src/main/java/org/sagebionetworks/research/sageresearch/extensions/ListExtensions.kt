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

package org.sagebionetworks.research.sageresearch.extensions

import android.content.Context
import org.sagebionetworks.research.sageresearch_app_sdk.R

/**
 * Join the list of text strings using a localized "and".
 *
 * - example:
 * ````
 *     let groceryList1 = ["apples", "oranges"]
 *     print (localizedJoin(groceryList1))  // "apples and oranges"
 *
 *     let groceryList2 = ["apples", "oranges", "bananas", "grapes"]
 *     print (localizedJoin(groceryList2))  // "apples, oranges, bananas, and grapes"
 * ````
 *
 * - note: This function is currently written to support US English. Any other language is untested.
 *
 * @param context can be any type, used to get string resources
 * @return A localized [String] with the joined values.
 */
fun List<String>.localizedAndJoin(context: Context): String {
    return localizedAndOrJoin(context, false)
}

/**
 * Join the list of text strings using a localized "or".
 *
 * - example:
 * ````
 *     let groceryList1 = ["apples", "oranges"]
 *     print (localizedJoin(groceryList1))  // "apples or oranges"
 *
 *     let groceryList2 = ["apples", "oranges", "bananas", "grapes"]
 *     print (localizedJoin(groceryList2))  // "apples, oranges, bananas, or grapes"
 * ````
 *
 * - note: This function is currently written to support US English. Any other language is untested.
 *
 * @param context can be any type, used to get string resources
 * @return A localized [String] with the joined values.
 */
fun List<String>.localizedOrJoin(context: Context): String {
    return localizedAndOrJoin(context, true)
}

private fun List<String>.localizedAndOrJoin(context: Context, or: Boolean): String {
    // Use this function when the List size >= 3
    val threeOrMoreItemsFun = {
        if (or) {
            context.getString(R.string.three_item_list_or_format)
                    .format(this[size - 3], this[size - 2], this[size - 1])
        } else {
            context.getString(R.string.three_item_list_and_format)
                    .format(this[size - 3], this[size - 2], this[size - 1])
        }
    }
    return when (size) {
        0 -> return ""
        1 -> this[0]
        2 ->
            if (or) {
                context.getString(R.string.two_item_list_or_format).format(this[0], this[1])
            } else {
                context.getString(R.string.two_item_list_and_format).format(this[0], this[1])
            }
        3 -> threeOrMoreItemsFun()
        else -> {
            val delimiter = context.getString(R.string.list_format_delimiter)
            val endText = threeOrMoreItemsFun()
            listOf(this.subList(0, size - 3).joinToString(delimiter), endText).joinToString(delimiter)
        }
    }
}