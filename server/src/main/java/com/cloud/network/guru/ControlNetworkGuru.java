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
package com.cloud.network.guru;

import java.util.Map;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import com.cloud.network.router.VirtualNetworkApplianceManager;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;

import com.cloud.configuration.Config;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenter.NetworkType;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.deploy.DeploymentPlan;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.InsufficientVirtualNetworkCapacityException;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.network.Network;
import com.cloud.network.NetworkModel;
import com.cloud.network.NetworkProfile;
import com.cloud.network.Networks.AddressFormat;
import com.cloud.network.Networks.BroadcastDomainType;
import com.cloud.network.Networks.Mode;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.dao.NetworkVO;
import com.cloud.offering.NetworkOffering;
import com.cloud.user.Account;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.Nic;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;

public class ControlNetworkGuru extends PodBasedNetworkGuru implements NetworkGuru {
    @Inject
    DataCenterDao _dcDao;
    @Inject
    ConfigurationDao _configDao;
    @Inject
    NetworkModel networkModel;
    String _cidr;
    String _gateway;

    private static final TrafficType[] TrafficTypes = {TrafficType.Control};

    @Override
    public boolean isMyTrafficType(TrafficType type) {
        for (TrafficType t : TrafficTypes) {
            if (t == type) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TrafficType[] getSupportedTrafficType() {
        return TrafficTypes;
    }

    protected boolean canHandle(NetworkOffering offering) {
        if (offering.isSystemOnly() && isMyTrafficType(offering.getTrafficType())) {
            return true;
        } else {
            logger.trace("We only care about System only Control network");
            return false;
        }
    }

    @Override
    public Network design(NetworkOffering offering, DeploymentPlan plan, Network specifiedConfig, Account owner) {
        if (!canHandle(offering)) {
            return null;
        }

        NetworkVO config =
            new NetworkVO(offering.getTrafficType(), Mode.Static, BroadcastDomainType.LinkLocal, offering.getId(), Network.State.Setup, plan.getDataCenterId(),
                plan.getPhysicalNetworkId(), offering.isRedundantRouter());
        config.setCidr(_cidr);
        config.setGateway(_gateway);

        return config;
    }

    protected ControlNetworkGuru() {
        super();
    }

    @Override
    public NicProfile allocate(Network config, NicProfile nic, VirtualMachineProfile vm) throws InsufficientVirtualNetworkCapacityException,
        InsufficientAddressCapacityException {

        if (vm.getHypervisorType() == HypervisorType.VMware && !isRouterVm(vm)) {
            NicProfile nicProf = new NicProfile(Nic.ReservationStrategy.Create, null, null, null, null);
            String mac = networkModel.getNextAvailableMacAddressInNetwork(config.getId());
            nicProf.setMacAddress(mac);
            return nicProf;
        }

        if (nic != null) {
            throw new CloudRuntimeException("Does not support nic specification at this time: " + nic);
        }

        return new NicProfile(Nic.ReservationStrategy.Start, null, null, null, null);
    }

    @Override
    public void deallocate(Network config, NicProfile nic, VirtualMachineProfile vm) {
    }

    @Override
    public void reserve(NicProfile nic, Network config, VirtualMachineProfile vm, DeployDestination dest, ReservationContext context)
        throws InsufficientVirtualNetworkCapacityException, InsufficientAddressCapacityException {
        assert nic.getTrafficType() == TrafficType.Control;

        // we have to get management/private ip for the control nic for vmware/hyperv due ssh issues.
        HypervisorType hType = vm.getHypervisorType();
        if (((hType == HypervisorType.VMware) || (hType == HypervisorType.Hyperv)) && isRouterVm(vm)) {
            super.reserve(nic, config, vm, dest, context);

            String mac = networkModel.getNextAvailableMacAddressInNetwork(config.getId());
            nic.setMacAddress(mac);
            return;
        }

        String ip = _dcDao.allocateLinkLocalIpAddress(dest.getDataCenter().getId(), dest.getPod().getId(), nic.getId(), context.getReservationId());
        if (ip == null) {
            throw new InsufficientAddressCapacityException("Insufficient link local address capacity", DataCenter.class, dest.getDataCenter().getId());
        }

        String netmask = NetUtils.cidr2Netmask(_cidr);

        logger.debug(String.format("Reserved NIC for %s [ipv4:%s netmask:%s gateway:%s]", vm.getInstanceName(), ip, netmask, _gateway));

        nic.setIPv4Address(ip);
        nic.setMacAddress(NetUtils.long2Mac(NetUtils.ip2Long(ip) | (14l << 40)));
        nic.setIPv4Netmask(netmask);
        nic.setFormat(AddressFormat.Ip4);
        nic.setIPv4Gateway(_gateway);
    }

    @Override
    public boolean release(NicProfile nic, VirtualMachineProfile vm, String reservationId) {
        assert nic.getTrafficType() == TrafficType.Control;
        HypervisorType hType = vm.getHypervisorType();
        if ( ( (hType == HypervisorType.VMware) || (hType == HypervisorType.Hyperv) )&& isRouterVm(vm)) {
            if (!VirtualNetworkApplianceManager.RemoveControlIpOnStop.valueIn(vm.getVirtualMachine().getDataCenterId())) {
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format("not releasing %s from %s with reservationId %s, as systemvm.release.control.ip.on.stop is set to false for the data center.", nic, vm, reservationId));
                }
                return true;
            }
            long dcId = vm.getVirtualMachine().getDataCenterId();
            DataCenterVO dcVo = _dcDao.findById(dcId);
            if (dcVo.getNetworkType() != NetworkType.Basic) {
                super.release(nic, vm, reservationId);
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format("Released nic: %s for vm %s", nic, vm));
                }
                return true;
            } else {
                nic.deallocate();
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format("Released nic: %s for vm %s", nic, vm));
                }
                return true;
            }
        }

        _dcDao.releaseLinkLocalIpAddress(nic.getId(), reservationId);

        nic.deallocate();
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Released nic: %s for vm %s", nic, vm));
        }

        return true;
    }

    protected boolean isRouterVm(VirtualMachineProfile vm) {
        return vm.getType() == VirtualMachine.Type.DomainRouter || vm.getType() == VirtualMachine.Type.InternalLoadBalancerVm;
    }

    @Override
    public Network implement(Network config, NetworkOffering offering, DeployDestination destination, ReservationContext context)
        throws InsufficientVirtualNetworkCapacityException {
        assert config.getTrafficType() == TrafficType.Control : "Why are you sending this configuration to me " + config;
        return config;
    }

    @Override
    public void shutdown(NetworkProfile config, NetworkOffering offering) {
        assert false : "Destroying a link local...Either you're out of your mind or something has changed.";
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);

        Map<String, String> dbParams = _configDao.getConfiguration(params);

        _cidr = dbParams.get(Config.ControlCidr.toString());
        if (_cidr == null) {
            _cidr = NetUtils.getLinkLocalCIDR();
        }

        _gateway = dbParams.get(Config.ControlGateway.toString());
        if (_gateway == null) {
            _gateway = NetUtils.getLinkLocalGateway();
        }

        logger.info("Control network setup: cidr=" + _cidr + "; gateway = " + _gateway);

        return true;
    }

    @Override
    public boolean trash(Network config, NetworkOffering offering) {
        return true;
    }

}
