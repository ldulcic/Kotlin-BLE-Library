/*
 * Copyright (c) 2026, Nordic Semiconductor
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

package no.nordicsemi.kotlin.ble.client.android

/**
 * Scan mode, which trades scanning latency for power consumption.
 *
 * The scan mode is ignored before Android 5. From Android 9, while the app is not visible and
 * has no foreground service, Android lowers [BALANCED] and [LOW_LATENCY] scans to [LOW_POWER]
 * or below.
 *
 * @see CentralManager.ScanOptions
 */
enum class ScanMode {

    /**
     * Scans in short windows with long breaks in between. Uses the least power, but devices are
     * found the slowest.
     */
    LOW_POWER,

    /**
     * A balance between scanning latency and power consumption.
     */
    BALANCED,

    /**
     * Scans with the highest duty cycle. Devices are found the fastest, but it uses the most power.
     */
    LOW_LATENCY,

    /**
     * Doesn't scan on its own, but receives results when other apps are scanning.
     *
     * Available from Android 6, Android 5 uses [LOW_POWER] instead.
     */
    OPPORTUNISTIC,
}
