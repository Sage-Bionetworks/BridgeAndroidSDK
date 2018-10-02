package org.sagebionetworks.research.sageresearch.extensions

import org.sagebionetworks.bridge.rest.exceptions.EntityNotFoundException

fun Throwable.isUnrecoverableError(): Boolean {
    return this.isUnrecoverableAccountNotFoundError() ||
            this.isUnrecoverableClientDataTooLargeError()
}

fun Throwable.isUnrecoverableAccountNotFoundError(): Boolean {
    return this is EntityNotFoundException &&
            this.message != null &&
            this.message == "Account not found."
}

fun Throwable.isUnrecoverableClientDataTooLargeError(): Boolean {
    return this.message?.contains("Client data too large") ?: false
}