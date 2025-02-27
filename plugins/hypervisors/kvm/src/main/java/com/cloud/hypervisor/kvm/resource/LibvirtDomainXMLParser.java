// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.hypervisor.kvm.resource;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.cloudstack.utils.security.ParserUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.cloudstack.utils.qemu.QemuObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.ChannelDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.DiskDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.InterfaceDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.InterfaceDef.NicModel;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.MemBalloonDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.RngDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.RngDef.RngBackendModel;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.WatchDogDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.WatchDogDef.WatchDogAction;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.WatchDogDef.WatchDogModel;

public class LibvirtDomainXMLParser {
    protected Logger logger = LogManager.getLogger(getClass());
    private final List<InterfaceDef> interfaces = new ArrayList<InterfaceDef>();
    private MemBalloonDef memBalloonDef = new MemBalloonDef();
    private final List<DiskDef> diskDefs = new ArrayList<DiskDef>();
    private final List<RngDef> rngDefs = new ArrayList<RngDef>();
    private final List<ChannelDef> channels = new ArrayList<ChannelDef>();
    private final List<WatchDogDef> watchDogDefs = new ArrayList<WatchDogDef>();
    private Integer vncPort;
    private  String vncPasswd;
    private String desc;
    private LibvirtVMDef.CpuTuneDef cpuTuneDef;
    private LibvirtVMDef.CpuModeDef cpuModeDef;
    private String name;

    public boolean parseDomainXML(String domXML) {
        DocumentBuilder builder;
        try {
            builder = ParserUtils.getSaferDocumentBuilderFactory().newDocumentBuilder();

            InputSource is = new InputSource();
            is.setCharacterStream(new StringReader(domXML));
            Document doc = builder.parse(is);

            Element rootElement = doc.getDocumentElement();

            desc = getTagValue("description", rootElement);
            name = getTagValue("name", rootElement);

            Element devices = (Element)rootElement.getElementsByTagName("devices").item(0);
            NodeList disks = devices.getElementsByTagName("disk");
            for (int i = 0; i < disks.getLength(); i++) {
                Element disk = (Element)disks.item(i);
                String type = disk.getAttribute("type");
                DiskDef def = new DiskDef();
                if (type.equalsIgnoreCase("network")) {
                    String diskFmtType = getAttrValue("driver", "type", disk);
                    String diskCacheMode = getAttrValue("driver", "cache", disk);
                    String diskPath = getAttrValue("source", "name", disk);
                    String protocol = getAttrValue("source", "protocol", disk);
                    String authUserName = getAttrValue("auth", "username", disk);
                    String poolUuid = getAttrValue("secret", "uuid", disk);
                    String host = LibvirtStoragePoolXMLParser.getStorageHosts(disk);
                    int port = 0;
                    String xmlPort = getAttrValue("host", "port", disk);
                    if (StringUtils.isNotBlank(xmlPort)) {
                        port = Integer.parseInt(xmlPort);
                    }
                    String diskLabel = getAttrValue("target", "dev", disk);
                    String bus = getAttrValue("target", "bus", disk);

                    DiskDef.DiskFmtType fmt = null;
                    if (diskFmtType != null) {
                        fmt = DiskDef.DiskFmtType.valueOf(diskFmtType.toUpperCase());
                    }

                    def.defNetworkBasedDisk(diskPath, host, port, authUserName, poolUuid, diskLabel,
                        DiskDef.DiskBus.valueOf(bus.toUpperCase()),
                        DiskDef.DiskProtocol.valueOf(protocol.toUpperCase()), fmt);
                    def.setCacheMode(DiskDef.DiskCacheMode.valueOf(diskCacheMode.toUpperCase()));
                } else {
                    String diskFmtType = getAttrValue("driver", "type", disk);
                    String diskCacheMode = getAttrValue("driver", "cache", disk);
                    String diskFile = getAttrValue("source", "file", disk);
                    String diskDev = getAttrValue("source", "dev", disk);

                    String diskLabel = getAttrValue("target", "dev", disk);
                    String bus = getAttrValue("target", "bus", disk);
                    String device = disk.getAttribute("device");

                    if (type.equalsIgnoreCase("file")) {
                        if (device.equalsIgnoreCase("disk")) {
                            DiskDef.DiskFmtType fmt = null;
                            if (diskFmtType != null) {
                                fmt = DiskDef.DiskFmtType.valueOf(diskFmtType.toUpperCase());
                            }
                            def.defFileBasedDisk(diskFile, diskLabel, DiskDef.DiskBus.valueOf(bus.toUpperCase()), fmt);
                        } else if (device.equalsIgnoreCase("cdrom")) {
                            def.defISODisk(diskFile, i+1, diskLabel);
                        }
                    } else if (type.equalsIgnoreCase("block")) {
                        def.defBlockBasedDisk(diskDev, diskLabel,
                            DiskDef.DiskBus.valueOf(bus.toUpperCase()));
                    }
                    if (StringUtils.isNotBlank(diskCacheMode)) {
                        def.setCacheMode(DiskDef.DiskCacheMode.valueOf(diskCacheMode.toUpperCase()));
                    }
                }

                NodeList iotune = disk.getElementsByTagName("iotune");
                if ((iotune != null) && (iotune.getLength() != 0)) {
                    String bytesReadRateStr = getTagValue("read_bytes_sec", (Element)iotune.item(0));
                    if (bytesReadRateStr != null) {
                        Long bytesReadRate = Long.parseLong(bytesReadRateStr);
                        def.setBytesReadRate(bytesReadRate);
                    }
                    String bytesReadRateMaxStr = getTagValue("read_bytes_sec_max", (Element)iotune.item(0));
                    if (bytesReadRateMaxStr != null) {
                        Long bytesReadRateMax = Long.parseLong(bytesReadRateMaxStr);
                        def.setBytesReadRateMax(bytesReadRateMax);
                    }
                    String bytesReadRateMaxLengthStr = getTagValue("read_bytes_sec_max_length", (Element)iotune.item(0));
                    if (bytesReadRateMaxLengthStr != null) {
                        Long bytesReadRateMaxLength = Long.parseLong(bytesReadRateMaxLengthStr);
                        def.setBytesReadRateMaxLength(bytesReadRateMaxLength);
                    }
                    String bytesWriteRateStr = getTagValue("write_bytes_sec", (Element)iotune.item(0));
                    if (bytesWriteRateStr != null) {
                        Long bytesWriteRate = Long.parseLong(bytesWriteRateStr);
                        def.setBytesWriteRate(bytesWriteRate);
                    }
                    String bytesWriteRateMaxStr = getTagValue("write_bytes_sec_max", (Element)iotune.item(0));
                    if (bytesWriteRateMaxStr != null) {
                        Long bytesWriteRateMax = Long.parseLong(bytesWriteRateMaxStr);
                        def.setBytesWriteRateMax(bytesWriteRateMax);
                    }
                    String bytesWriteRateMaxLengthStr = getTagValue("write_bytes_sec_max_length", (Element)iotune.item(0));
                    if (bytesWriteRateMaxLengthStr != null) {
                        Long bytesWriteRateMaxLength = Long.parseLong(bytesWriteRateMaxLengthStr);
                        def.setBytesWriteRateMaxLength(bytesWriteRateMaxLength);
                    }
                    String iopsReadRateStr = getTagValue("read_iops_sec", (Element)iotune.item(0));
                    if (iopsReadRateStr != null) {
                        Long iopsReadRate = Long.parseLong(iopsReadRateStr);
                        def.setIopsReadRate(iopsReadRate);
                    }
                    String iopsReadRateMaxStr = getTagValue("read_iops_sec_max", (Element)iotune.item(0));
                    if (iopsReadRateMaxStr != null) {
                        Long iopsReadRateMax = Long.parseLong(iopsReadRateMaxStr);
                        def.setIopsReadRateMax(iopsReadRateMax);
                    }
                    String iopsReadRateMaxLengthStr = getTagValue("read_iops_sec_max_length", (Element)iotune.item(0));
                    if (iopsReadRateMaxLengthStr != null) {
                        Long iopsReadRateMaxLength = Long.parseLong(iopsReadRateMaxLengthStr);
                        def.setIopsReadRateMaxLength(iopsReadRateMaxLength);
                    }
                    String iopsWriteRateStr = getTagValue("write_iops_sec", (Element)iotune.item(0));
                    if (iopsWriteRateStr != null) {
                        Long iopsWriteRate = Long.parseLong(iopsWriteRateStr);
                        def.setIopsWriteRate(iopsWriteRate);
                    }
                    String iopsWriteRateMaxStr = getTagValue("write_iops_sec_max", (Element)iotune.item(0));
                    if (iopsWriteRateMaxStr != null) {
                        Long iopsWriteRateMax = Long.parseLong(iopsWriteRateMaxStr);
                        def.setIopsWriteRateMax(iopsWriteRateMax);
                    }
                    String iopsWriteRateMaxLengthStr = getTagValue("write_iops_sec_max_length", (Element)iotune.item(0));
                    if (iopsWriteRateMaxLengthStr != null) {
                        Long iopsWriteRateMaxLength = Long.parseLong(iopsWriteRateMaxLengthStr);
                        def.setIopsWriteRateMaxLength(iopsWriteRateMaxLength);
                    }
                }

                NodeList encryption = disk.getElementsByTagName("encryption");
                if (encryption.getLength() != 0) {
                    Element encryptionElement = (Element) encryption.item(0);
                    String passphraseUuid = getAttrValue("secret", "uuid", encryptionElement);
                    QemuObject.EncryptFormat encryptFormat = QemuObject.EncryptFormat.enumValue(encryptionElement.getAttribute("format"));
                    DiskDef.LibvirtDiskEncryptDetails encryptDetails = new DiskDef.LibvirtDiskEncryptDetails(passphraseUuid, encryptFormat);
                    def.setLibvirtDiskEncryptDetails(encryptDetails);
                }

                diskDefs.add(def);
            }

            memBalloonDef = parseMemBalloonTag(devices);

            NodeList nics = devices.getElementsByTagName("interface");
            for (int i = 0; i < nics.getLength(); i++) {
                Element nic = (Element)nics.item(i);

                String type = nic.getAttribute("type");
                String mac = getAttrValue("mac", "address", nic);
                String dev = getAttrValue("target", "dev", nic);
                String model = getAttrValue("model", "type", nic);
                String slot = StringUtils.removeStart(getAttrValue("address", "slot", nic), "0x");

                InterfaceDef def = new InterfaceDef();
                NodeList bandwidth = nic.getElementsByTagName("bandwidth");
                Integer networkRateKBps = 0;
                if ((bandwidth != null) && (bandwidth.getLength() != 0)) {
                    Integer inbound = Integer.valueOf(getAttrValue("inbound", "average", (Element)bandwidth.item(0)));
                    Integer outbound = Integer.valueOf(getAttrValue("outbound", "average", (Element)bandwidth.item(0)));
                    if (inbound.equals(outbound)) {
                        networkRateKBps = inbound;
                    }
                }
                if (type.equalsIgnoreCase("network")) {
                    String network = getAttrValue("source", "network", nic);
                    def.defPrivateNet(network, dev, mac, NicModel.valueOf(model.toUpperCase()), networkRateKBps);
                } else if (type.equalsIgnoreCase("bridge")) {
                    String bridge = getAttrValue("source", "bridge", nic);
                    def.defBridgeNet(bridge, dev, mac, NicModel.valueOf(model.toUpperCase()), networkRateKBps);
                } else if (type.equalsIgnoreCase("ethernet")) {
                    String scriptPath = getAttrValue("script", "path", nic);
                    def.defEthernet(dev, mac, NicModel.valueOf(model.toUpperCase()), scriptPath, networkRateKBps);
                } else if (type.equals("vhostuser")) {
                    String sourcePort = getAttrValue("source", "path", nic);
                    String mode = getAttrValue("source", "mode", nic);
                    int lastSlashIndex = sourcePort.lastIndexOf("/");
                    String ovsPath = sourcePort.substring(0,lastSlashIndex);
                    String port = sourcePort.substring(lastSlashIndex + 1);
                    def.setDpdkSourcePort(port);
                    def.setDpdkOvsPath(ovsPath);
                    def.setInterfaceMode(mode);
                }
                String multiQueueNumber = getAttrValue("driver", "queues", nic);
                if (StringUtils.isNotBlank(multiQueueNumber)) {
                    def.setMultiQueueNumber(Integer.valueOf(multiQueueNumber));
                }

                String packedOn = getAttrValue("driver", "packed", nic);
                if (StringUtils.isNotBlank(packedOn)) {
                    def.setPackedVirtQueues("on".equalsIgnoreCase(packedOn));
                }

                if (StringUtils.isNotBlank(slot)) {
                    def.setSlot(Integer.parseInt(slot, 16));
                }

                interfaces.add(def);
            }

            NodeList ports = devices.getElementsByTagName("channel");
            for (int i = 0; i < ports.getLength(); i++) {
                Element channel = (Element)ports.item(i);

                String type = channel.getAttribute("type");
                String path = getAttrValue("source", "path", channel);
                String name = getAttrValue("target", "name", channel);
                String state = getAttrValue("target", "state", channel);

                if (ChannelDef.ChannelType.valueOf(type.toUpperCase()).equals(ChannelDef.ChannelType.SPICEVMC)) {
                    continue;
                }

                if (path == null) {
                    path = "";
                }

                ChannelDef def = null;
                if (StringUtils.isBlank(state)) {
                    def = new ChannelDef(name, ChannelDef.ChannelType.valueOf(type.toUpperCase()), new File(path));
                } else {
                    def = new ChannelDef(name, ChannelDef.ChannelType.valueOf(type.toUpperCase()),
                            ChannelDef.ChannelState.valueOf(state.toUpperCase()), new File(path));
                }

                channels.add(def);
            }

            Element graphic = (Element)devices.getElementsByTagName("graphics").item(0);

            if (graphic != null) {
                String port = graphic.getAttribute("port");
                if (port != null) {
                    try {
                        vncPort = Integer.parseInt(port);
                        if (vncPort != -1) {
                            vncPort = vncPort - 5900;
                        } else {
                            vncPort = null;
                        }
                    } catch (NumberFormatException nfe) {
                        vncPort = null;
                    }
                }

                String passwd = graphic.getAttribute("passwd");
                if (passwd != null) {
                    vncPasswd = passwd;
                }

            }

            NodeList rngs = devices.getElementsByTagName("rng");
            for (int i = 0; i < rngs.getLength(); i++) {
                RngDef def = null;
                Element rng = (Element)rngs.item(i);
                String backendModel = getAttrValue("backend", "model", rng);
                String path = getTagValue("backend", rng);
                String bytes = getAttrValue("rate", "bytes", rng);
                String period = getAttrValue("rate", "period", rng);
                if (StringUtils.isAnyEmpty(bytes, period)) {
                    logger.debug(String.format("Bytes and period in the rng section should not be null, please check the VM %s", name));
                }

                if (bytes == null) {
                    bytes = "0";
                }

                if (period == null) {
                    period = "0";
                }

                if (bytes == null) {
                    bytes = "0";
                }

                if (period == null) {
                    period = "0";
                }

                if (StringUtils.isEmpty(backendModel)) {
                    def = new RngDef(path, Integer.parseInt(bytes), Integer.parseInt(period));
                } else {
                    if (StringUtils.isEmpty(backendModel)) {
                        def = new RngDef(path, Integer.parseInt(bytes), Integer.parseInt(period));
                    } else {
                        def = new RngDef(path, RngBackendModel.valueOf(backendModel.toUpperCase()),
                                Integer.parseInt(bytes), Integer.parseInt(period));
                    }
                }
                if (def != null) {
                    rngDefs.add(def);
                }
            }

            NodeList watchDogs = devices.getElementsByTagName("watchdog");
            for (int i = 0; i < watchDogs.getLength(); i++) {
                WatchDogDef def = null;
                Element watchDog = (Element)watchDogs.item(i);
                String action = watchDog.getAttribute("action");
                String model = watchDog.getAttribute("model");

                if (StringUtils.isEmpty(model)) {
                   continue;
                }

                if (StringUtils.isEmpty(action)) {
                    def = new WatchDogDef(WatchDogModel.valueOf(model.toUpperCase()));
                } else {
                    def = new WatchDogDef(WatchDogAction.valueOf(action.toUpperCase()),
                                          WatchDogModel.valueOf(model.toUpperCase()));
                }

                watchDogDefs.add(def);
            }
            extractCpuTuneDef(rootElement);
            extractCpuModeDef(rootElement);
            return true;
        } catch (ParserConfigurationException e) {
            logger.debug(e.toString());
        } catch (SAXException e) {
            logger.debug(e.toString());
        } catch (IOException e) {
            logger.debug(e.toString());
        }
        return false;
    }

    /**
     * Parse the memballoon tag.
     * @param devices the devices tag.
     * @return the MemBalloonDef.
     */
    private MemBalloonDef parseMemBalloonTag(Element devices) {
        MemBalloonDef def = new MemBalloonDef();
        NodeList memBalloons = devices.getElementsByTagName("memballoon");
        if (memBalloons != null && memBalloons.getLength() != 0) {
            Element memBalloon = (Element)memBalloons.item(0);
            String model = memBalloon.getAttribute("model");
            if (model.equalsIgnoreCase("virtio")) {
                String statsPeriod = getAttrValue("stats", "period", memBalloon);
                def.defVirtioMemBalloon(statsPeriod);
            }
        }
        return def;
    }

    private static String getTagValue(String tag, Element eElement) {
        NodeList tagNodeList = eElement.getElementsByTagName(tag);
        if (tagNodeList == null || tagNodeList.getLength() == 0) {
            return null;
        }

        NodeList nlList = tagNodeList.item(0).getChildNodes();

        Node nValue = nlList.item(0);

        return nValue.getNodeValue();
    }

    private static String getAttrValue(String tag, String attr, Element eElement) {
        NodeList tagNode = eElement.getElementsByTagName(tag);
        if (tagNode.getLength() == 0) {
            return null;
        }
        Element node = (Element)tagNode.item(0);
        return node.getAttribute(attr);
    }

    public Integer getVncPort() {
        return vncPort;
    }

    public List<InterfaceDef> getInterfaces() {
        return interfaces;
    }

    public String getVncPasswd() {
        return vncPasswd;
    }

    public MemBalloonDef getMemBalloon() {
        return memBalloonDef;
    }

    public List<DiskDef> getDisks() {
        return diskDefs;
    }

    public List<RngDef> getRngs() {
        return rngDefs;
    }

    public List<ChannelDef> getChannels() {
        return Collections.unmodifiableList(channels);
    }

    public List<WatchDogDef> getWatchDogs() {
        return watchDogDefs;
    }

    public String getDescription() {
        return desc;
    }

    public String getName() {
        return name;
    }

    public LibvirtVMDef.CpuTuneDef getCpuTuneDef() {
        return cpuTuneDef;
    }

    public LibvirtVMDef.CpuModeDef getCpuModeDef() {
        return cpuModeDef;
    }

    private void extractCpuTuneDef(final Element rootElement) {
        NodeList cpuTunesList = rootElement.getElementsByTagName("cputune");
        if (cpuTunesList.getLength() > 0) {
            cpuTuneDef = new LibvirtVMDef.CpuTuneDef();
            final Element cpuTuneDefElement = (Element) cpuTunesList.item(0);
            final String cpuShares = getTagValue("shares", cpuTuneDefElement);
            if (StringUtils.isNotBlank(cpuShares)) {
                cpuTuneDef.setShares((Integer.parseInt(cpuShares)));
            }

            final String quota = getTagValue("quota", cpuTuneDefElement);
            if (StringUtils.isNotBlank(quota)) {
                cpuTuneDef.setQuota((Integer.parseInt(quota)));
            }

            final String period = getTagValue("period", cpuTuneDefElement);
            if (StringUtils.isNotBlank(period)) {
                cpuTuneDef.setPeriod((Integer.parseInt(period)));
            }
        }
    }

    private void extractCpuModeDef(final Element rootElement){
        NodeList cpuModeList = rootElement.getElementsByTagName("cpu");
        if (cpuModeList.getLength() > 0){
            cpuModeDef = new LibvirtVMDef.CpuModeDef();
            final Element cpuModeDefElement = (Element) cpuModeList.item(0);
            final String cpuModel = getTagValue("model", cpuModeDefElement);
            if (StringUtils.isNotBlank(cpuModel)){
                cpuModeDef.setModel(cpuModel);
            }
            NodeList cpuFeatures = cpuModeDefElement.getElementsByTagName("features");
            if (cpuFeatures.getLength() > 0) {
                final ArrayList<String> features = new ArrayList<>(cpuFeatures.getLength());
                for (int i = 0; i < cpuFeatures.getLength(); i++) {
                    final Element feature = (Element)cpuFeatures.item(i);
                    final String policy = feature.getAttribute("policy");
                    String featureName = feature.getAttribute("name");
                    if ("disable".equals(policy)) {
                        featureName = "-" + featureName;
                    }
                    features.add(featureName);
                }
                cpuModeDef.setFeatures(features);
            }
            final String sockets = getAttrValue("topology", "sockets", cpuModeDefElement);
            final String cores = getAttrValue("topology", "cores", cpuModeDefElement);
            if (StringUtils.isNotBlank(sockets) && StringUtils.isNotBlank(cores)) {
                cpuModeDef.setTopology(Integer.parseInt(cores), Integer.parseInt(sockets));
            }
        }
    }
}
