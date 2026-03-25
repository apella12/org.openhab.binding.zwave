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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.openhab.binding.zwave.internal.protocol.SerialMessage;
import org.openhab.binding.zwave.internal.protocol.ZWaveCommandClassPayload;
import org.openhab.binding.zwave.internal.protocol.ZWaveSerialMessageException;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveCommandClass.CommandClass;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveFirmwareUpdateCommandClass.FirmwareUpdateStatus;
import org.openhab.binding.zwave.internal.protocol.transaction.ZWaveCommandClassTransactionPayload;

/**
 * Test cases for {@link ZWaveFirmwareUpdateCommandClass}.
 *
 * @author Chris Jackson - Initial version
 */
public class ZWaveFirmwareUpdateCommandClassTest extends ZWaveCommandClassTest {

    @Test
    public void getFirmwareMdMessage() {
        ZWaveFirmwareUpdateCommandClass cls = (ZWaveFirmwareUpdateCommandClass) getCommandClass(
                CommandClass.COMMAND_CLASS_FIRMWARE_UPDATE_MD);
        ZWaveCommandClassTransactionPayload msg;

        // 0x7A = COMMAND_CLASS_FIRMWARE_UPDATE_MD, 0x01 = FIRMWARE_MD_GET
        byte[] expectedResponse = { 0x7A, 0x01 };
        cls.setVersion(1);
        msg = cls.getFirmwareMdMessage();
        assertTrue(Arrays.equals(msg.getPayloadBuffer(), expectedResponse));
    }

    @Test
    public void handleFirmwareMdReportV1() {
        // Frame: SOF=0x01, Len=0x0F, Type=0x00 (Request), Class=0x04 (AppCmdHandler),
        //        TxOptions=0x00, NodeId=0x08, CmdLen=0x09,
        //        CC=0x7A (FIRMWARE_UPDATE_MD), Cmd=0x02 (FIRMWARE_MD_REPORT),
        //        ManufHigh=0x00, ManufLow=0x86, FwIdHigh=0x00, FwIdLow=0x01,
        //        CkHigh=0xAB, CkLow=0xCD, Upgradable=0xFF,
        //        Checksum=0x93
        byte[] packetData = { 0x01, 0x0F, 0x00, 0x04, 0x00, 0x08, 0x09, 0x7A, 0x02, 0x00, (byte) 0x86, 0x00, 0x01,
                (byte) 0xAB, (byte) 0xCD, (byte) 0xFF, (byte) 0x93 };

        ZWaveFirmwareUpdateCommandClass cls = (ZWaveFirmwareUpdateCommandClass) getCommandClass(
                CommandClass.COMMAND_CLASS_FIRMWARE_UPDATE_MD);
        SerialMessage msg = new SerialMessage(packetData);
        try {
            ZWaveCommandClassPayload payload = new ZWaveCommandClassPayload(msg);
            cls.handleApplicationCommandRequest(payload, 0);

            assertEquals(0x0086, cls.getManufacturerId());
            assertEquals(0x0001, cls.getFirmwareId());
            assertEquals(0xABCD, cls.getChecksum());
            assertTrue(cls.isFirmwareUpgradable());
        } catch (ZWaveSerialMessageException e) {
            fail("Exception processing FIRMWARE_MD_REPORT: " + e.getMessage());
        }
    }

    @Test
    public void handleFirmwareMdReportV3() {
        // Frame with version 3+ fields: number of targets=2 and max fragment size=0x0040 (64)
        // Frame: SOF=0x01, Len=0x12, Type=0x00, Class=0x04,
        //        TxOptions=0x00, NodeId=0x08, CmdLen=0x0C,
        //        CC=0x7A, Cmd=0x02,
        //        ManufHigh=0x01, ManufLow=0x23, FwIdHigh=0x00, FwIdLow=0x04,
        //        CkHigh=0x12, CkLow=0x34, Upgradable=0xFF,
        //        Targets=0x02, FragSizeHigh=0x00, FragSizeLow=0x40,
        //        Checksum
        byte[] packetData = buildFrame(new byte[] { 0x7A, 0x02, 0x01, 0x23, 0x00, 0x04, 0x12, 0x34, (byte) 0xFF, 0x02,
                0x00, 0x40 });

        ZWaveFirmwareUpdateCommandClass cls = (ZWaveFirmwareUpdateCommandClass) getCommandClass(
                CommandClass.COMMAND_CLASS_FIRMWARE_UPDATE_MD);
        SerialMessage msg = new SerialMessage(packetData);
        try {
            ZWaveCommandClassPayload payload = new ZWaveCommandClassPayload(msg);
            cls.handleApplicationCommandRequest(payload, 0);

            assertEquals(0x0123, cls.getManufacturerId());
            assertEquals(0x0004, cls.getFirmwareId());
            assertEquals(0x1234, cls.getChecksum());
            assertTrue(cls.isFirmwareUpgradable());
            assertEquals(2, cls.getFirmwareTargets());
            assertEquals(64, cls.getMaxFragmentSize());
        } catch (ZWaveSerialMessageException e) {
            fail("Exception processing FIRMWARE_MD_REPORT v3: " + e.getMessage());
        }
    }

    @Test
    public void firmwareUpdateStatusLookup() {
        assertEquals(FirmwareUpdateStatus.STATUS_STORED_UPDATED, FirmwareUpdateStatus.getStatus(0xFF));
        assertEquals(FirmwareUpdateStatus.STATUS_STORED_WAITING, FirmwareUpdateStatus.getStatus(0xFE));
        assertEquals(FirmwareUpdateStatus.ERROR_CHECKSUM, FirmwareUpdateStatus.getStatus(0x01));
        assertEquals(FirmwareUpdateStatus.ERROR_DOWNLOAD_ABORTED, FirmwareUpdateStatus.getStatus(0x02));
        assertEquals(FirmwareUpdateStatus.ERROR_MANUFACTURER_ID, FirmwareUpdateStatus.getStatus(0x03));
        assertEquals(FirmwareUpdateStatus.ERROR_FIRMWARE_ID, FirmwareUpdateStatus.getStatus(0x04));
        assertEquals(FirmwareUpdateStatus.ERROR_FIRMWARE_TARGET, FirmwareUpdateStatus.getStatus(0x05));
        assertEquals(FirmwareUpdateStatus.ERROR_INVALID_HEADER, FirmwareUpdateStatus.getStatus(0x06));
        assertEquals(FirmwareUpdateStatus.ERROR_INVALID_HARDWARE_VERSION, FirmwareUpdateStatus.getStatus(0x09));
        assertEquals(FirmwareUpdateStatus.STATUS_UNKNOWN, FirmwareUpdateStatus.getStatus(0x42));
    }

    /**
     * Builds a valid Z-Wave ApplicationCommandHandler serial message frame for the given
     * command class payload bytes (CC byte, command byte, data bytes...).
     *
     * @param commandPayload the payload bytes starting with the command class byte
     * @return a complete, checksum-valid serial message byte array
     */
    private byte[] buildFrame(byte[] commandPayload) {
        // Frame: SOF(1) + Len(1) + Type(1) + MsgClass(1) + TxOpts(1) + NodeId(1) + CmdLen(1) + commandPayload + CRC(1)
        int cmdLen = commandPayload.length;
        int totalLen = 7 + cmdLen + 1; // including SOF and checksum
        byte[] frame = new byte[totalLen];
        frame[0] = 0x01; // SOF
        frame[1] = (byte) (totalLen - 2); // Length (excludes SOF and itself)
        frame[2] = 0x00; // Request
        frame[3] = 0x04; // ApplicationCommandHandler
        frame[4] = 0x00; // transmit options
        frame[5] = 0x08; // node ID
        frame[6] = (byte) cmdLen; // command length
        System.arraycopy(commandPayload, 0, frame, 7, cmdLen);
        // Compute checksum: 0xFF XOR bytes[1..length-2]
        byte checksum = (byte) 0xFF;
        for (int i = 1; i < frame.length - 1; i++) {
            checksum ^= frame[i];
        }
        frame[frame.length - 1] = checksum;
        return frame;
    }
}
