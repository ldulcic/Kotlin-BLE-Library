/*
 * Copyright (c) 2024, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list
 * of conditions and the following disclaimer in the documentation and/or other materials
 * provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be
 * used to endorse or promote products derived from this software without specific prior
 * written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.kotlin.ble.client.android.exception

import no.nordicsemi.kotlin.ble.core.exception.BluetoothException

/**
 * An exception thrown when bonding process failed.
 *
 * This may be caused by invalid PIN / passphrase, rejecting pairing or an internal error.
 *
 * @property reason The reason why bonding failed, as reported by the system. [Reason.Unknown] if
 * the system does not report it.
 */
class BondingFailedException(
    val reason: Reason = Reason.Unknown,
): BluetoothException("Bonding failed: $reason") {

    /** The reason why bonding failed. */
    enum class Reason {
        /** The system did not report the reason. */
        Unknown,
        /** The authentication failed, e.g. the PIN or passkey was wrong. */
        AuthenticationFailed,
        /** The peripheral rejected the pairing. */
        AuthenticationRejected,
        /** The pairing was cancelled by the user. */
        AuthenticationCanceled,
        /** The peripheral did not respond, for example it disconnected. */
        RemoteDeviceDown,
        /** Bonding was not possible because a discovery was in progress. */
        DiscoveryInProgress,
        /** The authentication timed out. */
        AuthenticationTimeout,
        /** Bonding was attempted too many times in a row. */
        RepeatedAttempts,
        /** The peripheral cancelled the pairing. */
        RemoteAuthenticationCanceled,
        /** The bond was removed. */
        Removed,
    }
}
