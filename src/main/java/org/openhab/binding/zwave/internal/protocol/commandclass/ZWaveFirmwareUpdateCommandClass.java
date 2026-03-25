/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.zwave.internal.protocol.commandclass;

import java.util.ArrayList;
import java.util.Collection;

import org.openhab.binding.zwave.internal.protocol.ZWaveCommandClassPayload;
import org.openhab.binding.zwave.internal.protocol.ZWaveController;
import org.openhab.binding.zwave.internal.protocol.ZWaveEndpoint;
import org.openhab.binding.zwave.internal.protocol.ZWaveNode;
import org.openhab.binding.zwave.internal.protocol.ZWaveTransaction.TransactionPriority;
import org.openhab.binding.zwave.internal.protocol.transaction.ZWaveCommandClassTransactionPayload;
import org.openhab.binding.zwave.internal.protocol.transaction.ZWaveCommandClassTransactionPayloadBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

/**
 * Handles the Firmware Update Metadata command class.
 * <p>
 * This command class allows a controller to query firmware metadata from a device
 * and monitor firmware update progress and completion status.
 *
 * @author Chris Jackson
 *
 */
@XStreamAlias("COMMAND_CLASS_FIRMWARE_UPDATE_MD")
public class ZWaveFirmwareUpdateCommandClass extends ZWaveCommandClass implements ZWaveCommandClassInitialization {

    @XStreamOmitField
    private static final Logger logger = LoggerFactory.getLogger(ZWaveFirmwareUpdateCommandClass.class);

    private static final int FIRMWARE_MD_GET = 0x01;
    private static final int FIRMWARE_MD_REPORT = 0x02;
    // private static final int FIRMWARE_UPDATE_MD_REQUEST_GET = 0x03;
    private static final int FIRMWARE_UPDATE_MD_REQUEST_REPORT = 0x04;
    // private static final int FIRMWARE_UPDATE_MD_GET = 0x05;
    // private static final int FIRMWARE_UPDATE_MD_REPORT = 0x06;
    private static final int FIRMWARE_UPDATE_MD_STATUS_REPORT = 0x07;

    private static final int FIRMWARE_UPGRADABLE = 0xFF;

    private int manufacturerId = Integer.MAX_VALUE;
    private int firmwareId = Integer.MAX_VALUE;
    private int checksum = Integer.MAX_VALUE;
    private boolean firmwareUpgradable = false;
    private int maxFragmentSize = 0;
    private int firmwareTargets = 0;

    @XStreamOmitField
    private boolean initialiseDone = false;

    /**
     * Creates a new instance of the ZWaveFirmwareUpdateCommandClass class.
     *
     * @param node the node this command class belongs to
     * @param controller the controller to use
     * @param endpoint the endpoint this Command class belongs to
     */
    public ZWaveFirmwareUpdateCommandClass(ZWaveNode node, ZWaveController controller, ZWaveEndpoint endpoint) {
        super(node, controller, endpoint);
    }

    @Override
    public CommandClass getCommandClass() {
        return CommandClass.COMMAND_CLASS_FIRMWARE_UPDATE_MD;
    }

    /**
     * Processes the FIRMWARE_MD_REPORT command received from a device. This report contains
     * manufacturer ID, firmware ID, checksum, and (from v3) the number of firmware targets,
     * maximum fragment size, and additional firmware IDs.
     *
     * @param payload the received command class payload
     * @param endpoint the endpoint or instance number
     */
    @ZWaveResponseHandler(id = FIRMWARE_MD_REPORT, name = "FIRMWARE_MD_REPORT")
    public void handleFirmwareMdReport(ZWaveCommandClassPayload payload, int endpoint) {
        logger.debug("NODE {}: Process Firmware Metadata Report", getNode().getNodeId());

        manufacturerId = ((payload.getPayloadByte(2)) << 8) | (payload.getPayloadByte(3));
        firmwareId = ((payload.getPayloadByte(4)) << 8) | (payload.getPayloadByte(5));
        checksum = ((payload.getPayloadByte(6)) << 8) | (payload.getPayloadByte(7));

        logger.debug("NODE {}: Firmware Manufacturer ID = 0x{}", getNode().getNodeId(),
                Integer.toHexString(manufacturerId));
        logger.debug("NODE {}: Firmware ID              = 0x{}", getNode().getNodeId(),
                Integer.toHexString(firmwareId));
        logger.debug("NODE {}: Firmware Checksum        = 0x{}", getNode().getNodeId(),
                Integer.toHexString(checksum));

        if (payload.getPayloadLength() > 8) {
            firmwareUpgradable = (payload.getPayloadByte(8) == FIRMWARE_UPGRADABLE);
            logger.debug("NODE {}: Firmware Upgradable     = {}", getNode().getNodeId(), firmwareUpgradable);
        }

        if (payload.getPayloadLength() > 11) {
            // Version 3+ includes number of firmware targets and max fragment size
            firmwareTargets = payload.getPayloadByte(9);
            maxFragmentSize = ((payload.getPayloadByte(10)) << 8) | (payload.getPayloadByte(11));
            logger.debug("NODE {}: Firmware Targets        = {}", getNode().getNodeId(), firmwareTargets);
            logger.debug("NODE {}: Max Fragment Size       = {}", getNode().getNodeId(), maxFragmentSize);
        }

        initialiseDone = true;
    }

    /**
     * Processes the FIRMWARE_UPDATE_MD_STATUS_REPORT command. This report is sent by the device
     * upon completion (or failure) of a firmware update.
     *
     * @param payload the received command class payload
     * @param endpoint the endpoint or instance number
     */
    @ZWaveResponseHandler(id = FIRMWARE_UPDATE_MD_STATUS_REPORT, name = "FIRMWARE_UPDATE_MD_STATUS_REPORT")
    public void handleFirmwareUpdateStatusReport(ZWaveCommandClassPayload payload, int endpoint) {
        int status = payload.getPayloadByte(2);
        FirmwareUpdateStatus updateStatus = FirmwareUpdateStatus.getStatus(status);
        logger.debug("NODE {}: Firmware Update Status Report: {} (0x{})", getNode().getNodeId(), updateStatus,
                Integer.toHexString(status));

        if (payload.getPayloadLength() > 4) {
            int waitTime = ((payload.getPayloadByte(3)) << 8) | (payload.getPayloadByte(4));
            logger.debug("NODE {}: Firmware Update Wait Time  = {}s", getNode().getNodeId(), waitTime);
        }
    }

    /**
     * Processes the FIRMWARE_UPDATE_MD_REQUEST_REPORT command. This is the device's response
     * to a firmware update request, indicating whether the update can proceed.
     *
     * @param payload the received command class payload
     * @param endpoint the endpoint or instance number
     */
    @ZWaveResponseHandler(id = FIRMWARE_UPDATE_MD_REQUEST_REPORT, name = "FIRMWARE_UPDATE_MD_REQUEST_REPORT")
    public void handleFirmwareUpdateRequestReport(ZWaveCommandClassPayload payload, int endpoint) {
        int status = payload.getPayloadByte(2);
        logger.debug("NODE {}: Firmware Update Request Report: status=0x{}", getNode().getNodeId(),
                Integer.toHexString(status));
        if (status == 0xFF) {
            logger.debug("NODE {}: Device accepted the firmware update request", getNode().getNodeId());
        } else {
            logger.debug("NODE {}: Device rejected the firmware update request (status=0x{})",
                    getNode().getNodeId(), Integer.toHexString(status));
        }
    }

    /**
     * Creates a message to request firmware metadata from the device.
     *
     * @return the transaction payload for FIRMWARE_MD_GET
     */
    public ZWaveCommandClassTransactionPayload getFirmwareMdMessage() {
        logger.debug("NODE {}: Creating new message for command FIRMWARE_MD_GET", getNode().getNodeId());

        return new ZWaveCommandClassTransactionPayloadBuilder(getNode().getNodeId(), getCommandClass(), FIRMWARE_MD_GET)
                .withExpectedResponseCommand(FIRMWARE_MD_REPORT).withPriority(TransactionPriority.Config).build();
    }

    @Override
    public Collection<ZWaveCommandClassTransactionPayload> initialize(boolean refresh) {
        ArrayList<ZWaveCommandClassTransactionPayload> result = new ArrayList<ZWaveCommandClassTransactionPayload>();

        if (refresh) {
            initialiseDone = false;
        }

        if (!initialiseDone) {
            result.add(getFirmwareMdMessage());
        }

        return result;
    }

    /**
     * Returns the manufacturer ID reported in the firmware metadata.
     *
     * @return the manufacturer ID, or {@link Integer#MAX_VALUE} if not yet received
     */
    public int getManufacturerId() {
        return manufacturerId;
    }

    /**
     * Returns the firmware ID reported in the firmware metadata.
     *
     * @return the firmware ID, or {@link Integer#MAX_VALUE} if not yet received
     */
    public int getFirmwareId() {
        return firmwareId;
    }

    /**
     * Returns the firmware checksum reported in the firmware metadata.
     *
     * @return the checksum, or {@link Integer#MAX_VALUE} if not yet received
     */
    public int getChecksum() {
        return checksum;
    }

    /**
     * Returns whether the firmware is upgradable.
     *
     * @return true if the firmware can be updated, false otherwise
     */
    public boolean isFirmwareUpgradable() {
        return firmwareUpgradable;
    }

    /**
     * Returns the maximum fragment size supported by the device.
     *
     * @return the maximum fragment size in bytes, or 0 if not reported (pre-v3)
     */
    public int getMaxFragmentSize() {
        return maxFragmentSize;
    }

    /**
     * Returns the number of additional firmware targets supported by the device.
     *
     * @return the number of firmware targets, or 0 if not reported (pre-v3)
     */
    public int getFirmwareTargets() {
        return firmwareTargets;
    }

    /**
     * Firmware update status codes returned in FIRMWARE_UPDATE_MD_STATUS_REPORT.
     */
    public enum FirmwareUpdateStatus {
        STATUS_STORED_WAITING(0xFE, "Firmware stored, waiting for activation"),
        STATUS_STORED_UPDATED(0xFF, "Firmware stored and updated"),
        ERROR_CHECKSUM(0x01, "Error: checksum"),
        ERROR_DOWNLOAD_ABORTED(0x02, "Error: download aborted"),
        ERROR_MANUFACTURER_ID(0x03, "Error: invalid manufacturer ID"),
        ERROR_FIRMWARE_ID(0x04, "Error: invalid firmware ID"),
        ERROR_FIRMWARE_TARGET(0x05, "Error: invalid firmware target"),
        ERROR_INVALID_HEADER(0x06, "Error: invalid header information"),
        ERROR_INVALID_HEADER_FORMAT(0x07, "Error: invalid header format"),
        ERROR_INSUFFICIENT_MEMORY(0x08, "Error: insufficient memory"),
        ERROR_INVALID_HARDWARE_VERSION(0x09, "Error: invalid hardware version"),
        STATUS_UNKNOWN(0x00, "Unknown status");

        private final int status;
        private final String description;

        FirmwareUpdateStatus(int status, String description) {
            this.status = status;
            this.description = description;
        }

        /**
         * Looks up a {@link FirmwareUpdateStatus} by its status byte value.
         *
         * @param statusByte the status byte from the Z-Wave frame
         * @return the matching {@link FirmwareUpdateStatus}, or {@link #STATUS_UNKNOWN} if not found
         */
        public static FirmwareUpdateStatus getStatus(int statusByte) {
            for (FirmwareUpdateStatus s : values()) {
                if (s.status == statusByte) {
                    return s;
                }
            }
            return STATUS_UNKNOWN;
        }

        public int getStatus() {
            return status;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return description;
        }
    }
}
