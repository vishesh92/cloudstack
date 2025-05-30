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
package com.cloud.usage;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.cloudstack.api.command.admin.usage.GenerateUsageRecordsCmd;
import org.apache.cloudstack.api.command.admin.usage.ListUsageRecordsCmd;
import org.apache.cloudstack.api.command.admin.usage.RemoveRawUsageRecordsCmd;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.usage.Usage;
import org.apache.cloudstack.usage.UsageService;
import org.apache.cloudstack.usage.UsageTypes;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import com.cloud.configuration.Config;
import com.cloud.domain.Domain;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.network.VpnUserVO;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.IPAddressVO;
import com.cloud.network.dao.LoadBalancerDao;
import com.cloud.network.dao.LoadBalancerVO;
import com.cloud.network.dao.VpnUserDao;
import com.cloud.network.rules.PortForwardingRuleVO;
import com.cloud.network.rules.dao.PortForwardingRulesDao;
import com.cloud.network.security.SecurityGroupVO;
import com.cloud.network.security.dao.SecurityGroupDao;
import com.cloud.offerings.NetworkOfferingVO;
import com.cloud.offerings.dao.NetworkOfferingDao;
import com.cloud.projects.Project;
import com.cloud.projects.ProjectManager;
import com.cloud.storage.SnapshotVO;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.SnapshotDao;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.usage.dao.UsageDao;
import com.cloud.usage.dao.UsageJobDao;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.user.AccountVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.DateUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.component.Manager;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.TransactionLegacy;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.dao.VMInstanceDao;

@Component
public class UsageServiceImpl extends ManagerBase implements UsageService, Manager {

    //ToDo: Move implementation to ManagaerImpl

    @Inject
    private AccountDao _accountDao;
    @Inject
    private DomainDao _domainDao;
    @Inject
    private UsageDao _usageDao;
    @Inject
    private UsageJobDao _usageJobDao;
    @Inject
    private ConfigurationDao _configDao;
    @Inject
    private ProjectManager _projectMgr;
    private TimeZone _usageTimezone = TimeZone.getTimeZone("GMT");
    @Inject
    private AccountService _accountService;
    @Inject
    private VMInstanceDao _vmDao;
    @Inject
    private SnapshotDao _snapshotDao;
    @Inject
    private SecurityGroupDao _sgDao;
    @Inject
    private VpnUserDao _vpnUserDao;
    @Inject
    private PortForwardingRulesDao _pfDao;
    @Inject
    private LoadBalancerDao _lbDao;
    @Inject
    private VMTemplateDao _vmTemplateDao;
    @Inject
    private VolumeDao _volumeDao;
    @Inject
    private IPAddressDao _ipDao;
    @Inject
    private HostDao _hostDao;
    @Inject
    private NetworkOfferingDao _networkOfferingDao;

    public UsageServiceImpl() {
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
        String timeZoneStr = ObjectUtils.defaultIfNull(_configDao.getValue(Config.UsageAggregationTimezone.toString()), "GMT");
        _usageTimezone = TimeZone.getTimeZone(timeZoneStr);
        return true;
    }

    @Override
    public boolean generateUsageRecords(GenerateUsageRecordsCmd cmd) {
        TransactionLegacy txn = TransactionLegacy.open(TransactionLegacy.USAGE_DB);
        try {
            UsageJobVO immediateJob = _usageJobDao.getNextImmediateJob();
            if (immediateJob == null) {
                UsageJobVO job = _usageJobDao.getLastJob();

                String host = null;
                int pid = 0;
                if (job != null) {
                    host = job.getHost();
                    pid = ((job.getPid() == null) ? 0 : job.getPid().intValue());
                }
                _usageJobDao.createNewJob(host, pid, UsageJobVO.JOB_TYPE_SINGLE);
            }
        } finally {
            txn.close();

            // switch back to VMOPS_DB
            TransactionLegacy swap = TransactionLegacy.open(TransactionLegacy.CLOUD_DB);
            swap.close();
        }
        return true;
    }

    @Override
    public Pair<List<? extends Usage>, Integer> getUsageRecords(ListUsageRecordsCmd cmd) {
        Long accountId = cmd.getAccountId();
        Long domainId = cmd.getDomainId();
        String accountName = cmd.getAccountName();
        Account caller = CallContext.current().getCallingAccount();
        Long usageType = cmd.getUsageType();
        Long projectId = cmd.getProjectId();
        String usageId = cmd.getUsageId();
        boolean projectRequested = false;

        if (projectId != null) {
            if (accountId != null) {
                throw new InvalidParameterValueException("Projectid and accountId can't be specified together");
            }
            accountId = getAccountIdFromProject(projectId);
            projectRequested = true;
        } else if ((accountId == null) && (StringUtils.isNotBlank(accountName)) && (domainId != null)) {
            accountId = getAccountIdFromDomainPlusName(domainId, accountName, caller);
        }

        boolean ignoreAccountId = false;
        boolean isDomainAdmin = _accountService.isDomainAdmin(caller.getId());
        boolean isNormalUser = _accountService.isNormalUser(caller.getId());

        //If accountId couldn't be found using project or accountName and domainId, get it from userContext
        if (accountId == null) {
            accountId = caller.getId();
            //List records for all the accounts if the caller account is of type admin.
            //If account_id or account_name is explicitly mentioned, list records for the specified account only even if the caller is of type admin
            ignoreAccountId = _accountService.isRootAdmin(caller.getId());
            logger.debug("Account details not available. Using userContext account: {}", caller);
        }

        // Check if a domain admin is allowed to access the requested domain id
        domainId = getDomainScopeForQuery(cmd, accountId, domainId, caller, isDomainAdmin);

        // By default users do not have access to this API.
        // Adding checks here in case someone changes the default access.
        checkUserAccess(cmd, accountId, caller, isNormalUser);

        Date startDate = cmd.getStartDate();
        Date endDate = cmd.getEndDate();
        if (startDate.after(endDate)) {
            throw new InvalidParameterValueException("Incorrect Date Range. Start date: " + startDate + " is after end date:" + endDate);
        }

        logger.debug("Getting usage records for account ID [{}], domain ID [{}] between [{}] and [{}] using page size [{}] and start index [{}].",
                accountId, domainId, DateUtil.displayDateInTimezone(_usageTimezone, startDate), DateUtil.displayDateInTimezone(_usageTimezone, endDate),
                cmd.getPageSizeVal(), cmd.getStartIndex());

        Filter usageFilter = new Filter(UsageVO.class, "id", true, cmd.getStartIndex(), cmd.getPageSizeVal());

        SearchCriteria<UsageVO> sc = _usageDao.createSearchCriteria();

        if (accountId != -1 && accountId != Account.ACCOUNT_ID_SYSTEM && !ignoreAccountId) {
            if (!cmd.isRecursive() || cmd.getAccountId() != null || projectRequested){
                sc.addAnd("accountId", SearchCriteria.Op.EQ, accountId);
            }
        }

        if (domainId != null) {
            if (cmd.isRecursive()) {
                SearchCriteria<DomainVO> sdc = _domainDao.createSearchCriteria();
                sdc.addOr("path", SearchCriteria.Op.LIKE, _domainDao.findById(domainId).getPath() + "%");
                List<DomainVO> domains = _domainDao.search(sdc, null);
                List<Long> domainIds = new ArrayList<Long>();
                for (DomainVO domain : domains) {
                    domainIds.add(domain.getId());
                }
                sc.addAnd("domainId", SearchCriteria.Op.IN, domainIds.toArray());
            } else {
                sc.addAnd("domainId", SearchCriteria.Op.EQ, domainId);
            }
        }

        if (usageType != null) {
            sc.addAnd("usageType", SearchCriteria.Op.EQ, usageType);
        }

        if (usageId != null) {
            if (usageType == null) {
                throw new InvalidParameterValueException("Usageid must be specified together with usageType");
            }

            Long usageDbId = null;
            boolean offeringExistsForNetworkOfferingType = false;

            switch (usageType.intValue()) {
                case UsageTypes.NETWORK_BYTES_RECEIVED:
                case UsageTypes.NETWORK_BYTES_SENT:
                case UsageTypes.RUNNING_VM:
                case UsageTypes.ALLOCATED_VM:
                case UsageTypes.VM_SNAPSHOT:
                case UsageTypes.BACKUP:
                    VMInstanceVO vm = _vmDao.findByUuidIncludingRemoved(usageId);
                    if (vm != null) {
                        usageDbId = vm.getId();
                    }

                    if (vm == null && (usageType == UsageTypes.NETWORK_BYTES_RECEIVED || usageType == UsageTypes.NETWORK_BYTES_SENT)) {
                        HostVO host = _hostDao.findByUuidIncludingRemoved(usageId);
                        if (host != null) {
                            usageDbId = host.getId();
                        }
                    }
                    break;
                case UsageTypes.SNAPSHOT:
                    SnapshotVO snap = _snapshotDao.findByUuidIncludingRemoved(usageId);
                    if (snap != null) {
                        usageDbId = snap.getId();
                    }
                    break;
                case UsageTypes.TEMPLATE:
                case UsageTypes.ISO:
                    VMTemplateVO tmpl = _vmTemplateDao.findByUuidIncludingRemoved(usageId);
                    if (tmpl != null) {
                        usageDbId = tmpl.getId();
                    }
                    break;
                case UsageTypes.LOAD_BALANCER_POLICY:
                    LoadBalancerVO lb = _lbDao.findByUuidIncludingRemoved(usageId);
                    if (lb != null) {
                        usageDbId = lb.getId();
                    }
                    break;
                case UsageTypes.PORT_FORWARDING_RULE:
                    PortForwardingRuleVO pf = _pfDao.findByUuidIncludingRemoved(usageId);
                    if (pf != null) {
                        usageDbId = pf.getId();
                    }
                    break;
                case UsageTypes.VOLUME:
                case UsageTypes.VM_DISK_IO_READ:
                case UsageTypes.VM_DISK_IO_WRITE:
                case UsageTypes.VM_DISK_BYTES_READ:
                case UsageTypes.VM_DISK_BYTES_WRITE:
                    VolumeVO volume = _volumeDao.findByUuidIncludingRemoved(usageId);
                    if (volume != null) {
                        usageDbId = volume.getId();
                    }
                    break;
                case UsageTypes.VPN_USERS:
                    VpnUserVO vpnUser = _vpnUserDao.findByUuidIncludingRemoved(usageId);
                    if (vpnUser != null) {
                        usageDbId = vpnUser.getId();
                    }
                    break;
                case UsageTypes.SECURITY_GROUP:
                    SecurityGroupVO sg = _sgDao.findByUuidIncludingRemoved(usageId);
                    if (sg != null) {
                        usageDbId = sg.getId();
                    }
                    break;
                case UsageTypes.IP_ADDRESS:
                    IPAddressVO ip = _ipDao.findByUuidIncludingRemoved(usageId);
                    if (ip != null) {
                        usageDbId = ip.getId();
                    }
                    break;
                case UsageTypes.NETWORK_OFFERING:
                    NetworkOfferingVO networkOffering = _networkOfferingDao.findByUuidIncludingRemoved(usageId);
                    if (networkOffering != null) {
                        offeringExistsForNetworkOfferingType = true;
                        sc.addAnd("offeringId", SearchCriteria.Op.EQ, networkOffering.getId());
                    }
                default:
                    break;
            }

            if (usageDbId != null) {
                sc.addAnd("usageId", SearchCriteria.Op.EQ, usageDbId);
            } else if (!offeringExistsForNetworkOfferingType) {
                // return an empty list if usageId was not found
                return new Pair<List<? extends Usage>, Integer>(new ArrayList<Usage>(), new Integer(0));
            }
        }

        // Filter out hidden usages
        sc.addAnd("isHidden", SearchCriteria.Op.EQ, false);

        if ((startDate != null) && (endDate != null) && startDate.before(endDate)) {
            sc.addAnd("startDate", SearchCriteria.Op.BETWEEN, startDate, endDate);
            sc.addAnd("endDate", SearchCriteria.Op.BETWEEN, startDate, endDate);
        } else {
            return new Pair<List<? extends Usage>, Integer>(new ArrayList<Usage>(), new Integer(0)); // return an empty list if we fail to validate the dates
        }

        Pair<List<UsageVO>, Integer> usageRecords = null;
        TransactionLegacy txn = TransactionLegacy.open(TransactionLegacy.USAGE_DB);
        try {
            usageRecords = _usageDao.searchAndCountAllRecords(sc, usageFilter);
        } finally {
            txn.close();

            // switch back to VMOPS_DB
            TransactionLegacy swap = TransactionLegacy.open(TransactionLegacy.CLOUD_DB);
            swap.close();
        }

        return new Pair<List<? extends Usage>, Integer>(usageRecords.first(), usageRecords.second());
    }

    private Long getDomainScopeForQuery(ListUsageRecordsCmd cmd, Long accountId, Long domainId, Account caller, boolean isDomainAdmin) {
        if (isDomainAdmin) {
            if (domainId != null) {
                Account callerAccount = _accountService.getAccount(caller.getId());
                Domain domain = _domainDao.findById(domainId);
                _accountService.checkAccess(callerAccount, domain);
            } else {
                domainId = caller.getDomainId();
            }

            if (cmd.getAccountId() != null) {
                checkDomainAdminAccountAccess(accountId, domainId);
            }
        }
        return domainId;
    }

    @NotNull
    private Long getAccountIdFromDomainPlusName(Long domainId, String accountName, Account caller) {
        Long accountId;
        Account userAccount = null;
        if (! _domainDao.isChildDomain(caller.getDomainId(), domainId)) {
            throw new PermissionDeniedException("Invalid Domain Id or Account");
        }
        Filter filter = new Filter(AccountVO.class, "id", Boolean.FALSE, null, null);
        List<AccountVO> accounts = _accountDao.listAccounts(accountName, domainId, filter);
        if (accounts.size() > 0) {
            userAccount = accounts.get(0);
        }
        if (userAccount == null) {
            throw new InvalidParameterValueException("Unable to find account " + accountName + " in domain " + domainId);
        }
        return userAccount.getId();
    }

    @NotNull
    private Long getAccountIdFromProject(Long projectId) {
        Long accountId;
        Project project = _projectMgr.getProject(projectId);
        if (project == null) {
            throw new InvalidParameterValueException("Unable to find project by id " + projectId);
        }
        final long projectAccountId = project.getProjectAccountId();
        if (logger.isInfoEnabled()) {
            logger.info(String.format("Using projectAccountId %d for project %s [%s] as account id", projectAccountId, project.getName(), project.getUuid()));
        }
        accountId = projectAccountId;
        return accountId;
    }

    private void checkUserAccess(ListUsageRecordsCmd cmd, Long accountId, Account caller, boolean isNormalUser) {
        if (isNormalUser) {
            // A user can only access their own account records
            if (caller.getId() != accountId) {
                throw new PermissionDeniedException("Users are only allowed to list usage records for their own account.");
            }
            // Users cannot get recursive records
            if (cmd.isRecursive()) {
                throw new PermissionDeniedException("Users are not allowed to list usage records recursively.");
            }
            // Users cannot get domain records
            if (cmd.getDomainId() != null) {
                throw new PermissionDeniedException("Users are not allowed to list usage records for a domain");
            }
        }
    }

    private void checkDomainAdminAccountAccess(Long accountId, Long domainId) {
        Account account = _accountService.getAccount(accountId);
        boolean matchFound = false;

        if (account.getDomainId() == domainId) {
            matchFound = true;
        } else {

            // Check if the account is in a child domain of this domain admin.
            List<DomainVO> childDomains = _domainDao.findAllChildren(_domainDao.findById(domainId).getPath(), domainId);

            for (DomainVO domainVO : childDomains) {
                if (account.getDomainId() == domainVO.getId()) {
                    matchFound = true;
                    break;
                }
            }
        }
        if (!matchFound) {
            throw new PermissionDeniedException("Domain admins may only retrieve usage records for accounts in their own domain and child domains.");
        }
    }

    @Override
    public TimeZone getUsageTimezone() {
        return _usageTimezone;
    }

    @Override
    public boolean removeRawUsageRecords(RemoveRawUsageRecordsCmd cmd) throws InvalidParameterValueException {
        Integer interval = cmd.getInterval();
        if (interval != null && interval > 0 ) {
            String jobExecTime = _configDao.getValue(Config.UsageStatsJobExecTime.toString());
            if (jobExecTime != null ) {
                String[] segments = jobExecTime.split(":");
                if (segments.length == 2) {
                    String timeZoneStr = _configDao.getValue(Config.UsageExecutionTimezone.toString());
                    if (timeZoneStr == null) {
                        timeZoneStr = "GMT";
                    }
                    TimeZone tz = TimeZone.getTimeZone(timeZoneStr);
                    Calendar cal = Calendar.getInstance(tz);
                    cal.setTime(new Date());
                    long curTS = cal.getTimeInMillis();
                    cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(segments[0]));
                    cal.set(Calendar.MINUTE, Integer.parseInt(segments[1]));
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    long execTS = cal.getTimeInMillis();
                    logger.debug("Trying to remove old raw cloud_usage records older than " + interval + " day(s), current time=" + curTS + " next job execution time=" + execTS);
                    // Let's avoid cleanup when job runs and around a 15 min interval
                    if (Math.abs(curTS - execTS) < 15 * 60 * 1000) {
                        return false;
                    }
                }
            }
            _usageDao.removeOldUsageRecords(interval);
        } else {
            throw new InvalidParameterValueException("Invalid interval value. Interval to remove cloud_usage records should be greater than 0");
        }
        return true;
    }
}
