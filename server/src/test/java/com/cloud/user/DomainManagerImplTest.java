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

package com.cloud.user;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.cloudstack.annotation.dao.AnnotationDao;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
import org.apache.cloudstack.framework.messagebus.MessageBus;
import org.apache.cloudstack.framework.messagebus.PublishScope;
import org.apache.cloudstack.network.RoutedIpv4Manager;
import org.apache.cloudstack.region.RegionManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.api.query.dao.DiskOfferingJoinDao;
import com.cloud.api.query.dao.NetworkOfferingJoinDao;
import com.cloud.api.query.dao.ServiceOfferingJoinDao;
import com.cloud.api.query.dao.VpcOfferingJoinDao;
import com.cloud.configuration.ConfigurationManager;
import com.cloud.configuration.Resource.ResourceOwnerType;
import com.cloud.configuration.ResourceLimit;
import com.cloud.configuration.dao.ResourceCountDao;
import com.cloud.configuration.dao.ResourceLimitDao;
import com.cloud.dc.DedicatedResourceVO;
import com.cloud.dc.dao.DedicatedResourceDao;
import com.cloud.domain.Domain;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.domain.dao.DomainDetailsDao;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.network.dao.NetworkDomainDao;
import com.cloud.projects.ProjectManager;
import com.cloud.projects.dao.ProjectDao;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.UuidUtils;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.net.NetUtils;


@RunWith(MockitoJUnitRunner.class)
public class DomainManagerImplTest {
    @Mock
    DomainDao domainDaoMock;
    @Mock
    AccountManager _accountMgr;
    @Mock
    ResourceCountDao _resourceCountDao;
    @Mock
    AccountDao _accountDao;
    @Mock
    DiskOfferingJoinDao _diskOfferingDao;
    @Mock
    NetworkOfferingJoinDao networkOfferingJoinDao;
    @Mock
    ServiceOfferingJoinDao serviceOfferingJoinDao;
    @Mock
    VpcOfferingJoinDao vpcOfferingJoinDao;
    @Mock
    ProjectDao _projectDao;
    @Mock
    ProjectManager _projectMgr;
    @Mock
    RegionManager _regionMgr;
    @Mock
    ResourceLimitDao _resourceLimitDao;
    @Mock
    DedicatedResourceDao _dedicatedDao;
    @Mock
    NetworkOrchestrationService _networkMgr;
    @Mock
    NetworkDomainDao _networkDomainDao;
    @Mock
    MessageBus _messageBus;
    @Mock
    ConfigurationManager _configMgr;
    @Mock
    DomainDetailsDao _domainDetailsDao;
    @Mock
    AnnotationDao annotationDao;
    @Mock
    RoutedIpv4Manager routedIpv4Manager;

    @Spy
    @InjectMocks
    DomainManagerImpl domainManager = new DomainManagerImpl();

    @Mock
    DomainVO domain;
    @Mock
    Account adminAccount;
    @Mock
    GlobalLock lock;

    List<AccountVO> domainAccountsForCleanup;
    List<Long> domainNetworkIds;
    List<DedicatedResourceVO> domainDedicatedResources;

    private static final long DOMAIN_ID = 3l;
    private static final long ACCOUNT_ID = 1l;

    private static boolean testDomainCleanup = false;

    @Before
    public void setup() throws NoSuchFieldException, SecurityException,
            IllegalArgumentException, IllegalAccessException {
        Mockito.doReturn(adminAccount).when(domainManager).getCaller();
        Mockito.doReturn(lock).when(domainManager).getGlobalLock("DomainCleanup");
        Mockito.when(lock.lock(Mockito.anyInt())).thenReturn(true);
        Mockito.when(domainDaoMock.findById(DOMAIN_ID)).thenReturn(domain);
        Mockito.when(domain.getAccountId()).thenReturn(ACCOUNT_ID);
        Mockito.when(domain.getId()).thenReturn(DOMAIN_ID);
        Mockito.when(domainDaoMock.remove(DOMAIN_ID)).thenReturn(true);
        domainAccountsForCleanup = new ArrayList<AccountVO>();
        domainNetworkIds = new ArrayList<Long>();
        domainDedicatedResources = new ArrayList<DedicatedResourceVO>();
        Mockito.when(_accountDao.findCleanupsForRemovedAccounts(DOMAIN_ID)).thenReturn(domainAccountsForCleanup);
        Mockito.when(_networkDomainDao.listNetworkIdsByDomain(DOMAIN_ID)).thenReturn(domainNetworkIds);
        Mockito.when(_dedicatedDao.listByDomainId(DOMAIN_ID)).thenReturn(domainDedicatedResources);

        Mockito.when(_diskOfferingDao.findByDomainId(Mockito.anyLong())).thenReturn(Collections.emptyList());
        Mockito.when(networkOfferingJoinDao.findByDomainId(Mockito.anyLong(), Mockito.anyBoolean())).thenReturn(Collections.emptyList());
        Mockito.when(serviceOfferingJoinDao.findByDomainId(Mockito.anyLong())).thenReturn(Collections.emptyList());
        Mockito.when(vpcOfferingJoinDao.findByDomainId(Mockito.anyLong())).thenReturn(Collections.emptyList());
    }

    @Test
    public void testFindDomainByIdOrPathNullOrEmpty() {
        final DomainVO domain = new DomainVO("someDomain", 123, 1L, "network.domain");
        Mockito.when(domainDaoMock.findById(1L)).thenReturn(domain);
        Assert.assertEquals(domain, domainManager.findDomainByIdOrPath(null, null));
        Assert.assertEquals(domain, domainManager.findDomainByIdOrPath(0L, ""));
        Assert.assertEquals(domain, domainManager.findDomainByIdOrPath(-1L, " "));
        Assert.assertEquals(domain, domainManager.findDomainByIdOrPath(null, "       "));
    }

    @Test
    public void testFindDomainByIdOrPathValidPathAndInvalidId() {
        final DomainVO domain = new DomainVO("someDomain", 123, 1L, "network.domain");
        Mockito.when(domainDaoMock.findDomainByPath(Mockito.eq("/someDomain/"))).thenReturn(domain);
        Assert.assertEquals(domain, domainManager.findDomainByIdOrPath(null, "/someDomain/"));
        Assert.assertEquals(domain, domainManager.findDomainByIdOrPath(0L, " /someDomain/"));
        Assert.assertEquals(domain, domainManager.findDomainByIdOrPath(-1L, "/someDomain/ "));
        Assert.assertEquals(domain, domainManager.findDomainByIdOrPath(null, "   /someDomain/   "));
    }

    @Test
    public void testFindDomainByIdOrPathInvalidPathAndInvalidId() {
        Mockito.when(domainDaoMock.findDomainByPath(Mockito.anyString())).thenReturn(null);
        Assert.assertNull(domainManager.findDomainByIdOrPath(null, "/nonExistingDomain/"));
        Assert.assertNull(domainManager.findDomainByIdOrPath(0L, " /nonExistingDomain/"));
        Assert.assertNull(domainManager.findDomainByIdOrPath(-1L, "/nonExistingDomain/ "));
        Assert.assertNull(domainManager.findDomainByIdOrPath(null, "   /nonExistingDomain/   "));
    }


    @Test
    public void testFindDomainByIdOrPathValidId() {
        final DomainVO domain = new DomainVO("someDomain", 123, 1L, "network.domain");
        Mockito.when(domainDaoMock.findById(1L)).thenReturn(domain);
        Mockito.lenient().when(domainDaoMock.findDomainByPath(Mockito.eq("/validDomain/"))).thenReturn(new DomainVO());
        Assert.assertEquals(domain, domainManager.findDomainByIdOrPath(1L, null));
        Assert.assertEquals(domain, domainManager.findDomainByIdOrPath(1L, ""));
        Assert.assertEquals(domain, domainManager.findDomainByIdOrPath(1L, " "));
        Assert.assertEquals(domain, domainManager.findDomainByIdOrPath(1L, "       "));
        Assert.assertEquals(domain, domainManager.findDomainByIdOrPath(1L, "/validDomain/"));
    }

    @Test(expected=InvalidParameterValueException.class)
    public void testDeleteDomainNullDomain() {
        Mockito.when(domainDaoMock.findById(DOMAIN_ID)).thenReturn(null);
        domainManager.deleteDomain(DOMAIN_ID, testDomainCleanup);
    }

    @Test(expected=PermissionDeniedException.class)
    public void testDeleteDomainRootDomain() {
        Mockito.when(domainDaoMock.findById(Domain.ROOT_DOMAIN)).thenReturn(domain);
        domainManager.deleteDomain(Domain.ROOT_DOMAIN, testDomainCleanup);
    }

    @Test
    public void testDeleteDomainNoCleanup() {
        Mockito.when(_configMgr.releaseDomainSpecificVirtualRanges(Mockito.any())).thenReturn(true);
        domainManager.deleteDomain(DOMAIN_ID, testDomainCleanup);
        Mockito.verify(domainManager).deleteDomain(domain, testDomainCleanup);
        Mockito.verify(domainManager).removeDomainWithNoAccountsForCleanupNetworksOrDedicatedResources(domain);
        Mockito.verify(domainManager).cleanupDomainDetails(DOMAIN_ID);
        Mockito.verify(domainManager).cleanupDomainOfferings(DOMAIN_ID);
        Mockito.verify(lock).unlock();
    }

    @Test
    public void testRemoveDomainWithNoAccountsForCleanupNetworksOrDedicatedResourcesRemoveDomain() {
        domainManager.removeDomainWithNoAccountsForCleanupNetworksOrDedicatedResources(domain);
        Mockito.verify(domainManager).publishRemoveEventsAndRemoveDomain(domain);
    }

    @Test(expected=CloudRuntimeException.class)
    public void testRemoveDomainWithNoAccountsForCleanupNetworksOrDedicatedResourcesDontRemoveDomain() {
        domainNetworkIds.add(2l);
        domainManager.removeDomainWithNoAccountsForCleanupNetworksOrDedicatedResources(domain);
        Mockito.verify(domainManager).failRemoveOperation(domain, domainAccountsForCleanup, domainNetworkIds, false);
    }

    @Test
    public void testPublishRemoveEventsAndRemoveDomainSuccessfulDelete() {
        domainManager.publishRemoveEventsAndRemoveDomain(domain);
        Mockito.verify(_messageBus).publish(Mockito.anyString(), ArgumentMatchers.eq(DomainManager.MESSAGE_PRE_REMOVE_DOMAIN_EVENT),
                ArgumentMatchers.eq(PublishScope.LOCAL), ArgumentMatchers.eq(domain));
        Mockito.verify(_messageBus).publish(Mockito.anyString(), ArgumentMatchers.eq(DomainManager.MESSAGE_REMOVE_DOMAIN_EVENT),
                ArgumentMatchers.eq(PublishScope.LOCAL), ArgumentMatchers.eq(domain));
        Mockito.verify(domainDaoMock).remove(DOMAIN_ID);
    }

    @Test(expected=CloudRuntimeException.class)
    public void testPublishRemoveEventsAndRemoveDomainExceptionDelete() {
        Mockito.when(domainDaoMock.remove(DOMAIN_ID)).thenReturn(false);
        domainManager.publishRemoveEventsAndRemoveDomain(domain);
        Mockito.verify(_messageBus).publish(Mockito.anyString(), ArgumentMatchers.eq(DomainManager.MESSAGE_PRE_REMOVE_DOMAIN_EVENT),
                ArgumentMatchers.eq(PublishScope.LOCAL), ArgumentMatchers.eq(domain));
        Mockito.verify(_messageBus, Mockito.never()).publish(Mockito.anyString(), ArgumentMatchers.eq(DomainManager.MESSAGE_REMOVE_DOMAIN_EVENT),
                ArgumentMatchers.eq(PublishScope.LOCAL), ArgumentMatchers.eq(domain));
        Mockito.verify(domainDaoMock).remove(DOMAIN_ID);
    }

    @Test(expected=CloudRuntimeException.class)
    public void testFailRemoveOperation() {
        domainManager.failRemoveOperation(domain, domainAccountsForCleanup, domainNetworkIds, true);
    }

    @Test
    public void deleteDomain() {
        DomainVO domain = new DomainVO();
        domain.setId(20l);
        domain.setAccountId(30l);
        Account account = new AccountVO("testaccount", 1L, "networkdomain", Account.Type.NORMAL, "uuid");
        UserVO user = new UserVO(1, "testuser", "password", "firstname", "lastName", "email", "timezone", UUID.randomUUID().toString(), User.Source.UNKNOWN);
        CallContext.register(user, account);

        Mockito.when(domainDaoMock.findById(20l)).thenReturn(domain);
        Mockito.doNothing().when(_accountMgr).checkAccess(Mockito.any(Account.class), Mockito.any(Domain.class));
        Mockito.when(domainDaoMock.update(Mockito.eq(20l), Mockito.any(DomainVO.class))).thenReturn(true);
        Mockito.lenient().when(_accountDao.search(Mockito.any(SearchCriteria.class), (Filter) org.mockito.ArgumentMatchers.isNull())).thenReturn(new ArrayList<AccountVO>());
        Mockito.when(_networkDomainDao.listNetworkIdsByDomain(Mockito.anyLong())).thenReturn(new ArrayList<Long>());
        Mockito.when(_accountDao.findCleanupsForRemovedAccounts(Mockito.anyLong())).thenReturn(new ArrayList<AccountVO>());
        Mockito.when(_dedicatedDao.listByDomainId(Mockito.anyLong())).thenReturn(new ArrayList<DedicatedResourceVO>());
        Mockito.when(domainDaoMock.remove(Mockito.anyLong())).thenReturn(true);
        Mockito.when(_configMgr.releaseDomainSpecificVirtualRanges(Mockito.any())).thenReturn(true);

        try {
            Assert.assertTrue(domainManager.deleteDomain(20l, false));
        } finally {
            CallContext.unregister();
        }
    }

    @Test
    public void deleteDomainCleanup() {
        DomainVO domain = new DomainVO();
        domain.setId(20l);
        domain.setAccountId(30l);
        Account account = new AccountVO("testaccount", 1L, "networkdomain", Account.Type.NORMAL, "uuid");
        UserVO user = new UserVO(1, "testuser", "password", "firstname", "lastName", "email", "timezone", UUID.randomUUID().toString(), User.Source.UNKNOWN);
        CallContext.register(user, account);

        Mockito.when(domainDaoMock.findById(20l)).thenReturn(domain);
        Mockito.doNothing().when(_accountMgr).checkAccess(Mockito.any(Account.class), Mockito.any(Domain.class));
        Mockito.when(domainDaoMock.update(Mockito.eq(20l), Mockito.any(DomainVO.class))).thenReturn(true);
        Mockito.when(domainDaoMock.createSearchCriteria()).thenReturn(Mockito.mock(SearchCriteria.class));
        Mockito.when(domainDaoMock.search(Mockito.any(SearchCriteria.class), (Filter) org.mockito.ArgumentMatchers.isNull())).thenReturn(new ArrayList<DomainVO>());
        Mockito.when(_accountDao.createSearchCriteria()).thenReturn(Mockito.mock(SearchCriteria.class));
        Mockito.when(_accountDao.search(Mockito.any(SearchCriteria.class), (Filter) org.mockito.ArgumentMatchers.isNull())).thenReturn(new ArrayList<AccountVO>());
        Mockito.when(_networkDomainDao.listNetworkIdsByDomain(Mockito.anyLong())).thenReturn(new ArrayList<Long>());
        Mockito.when(_accountDao.findCleanupsForRemovedAccounts(Mockito.anyLong())).thenReturn(new ArrayList<AccountVO>());
        Mockito.when(_dedicatedDao.listByDomainId(Mockito.anyLong())).thenReturn(new ArrayList<DedicatedResourceVO>());
        Mockito.when(domainDaoMock.remove(Mockito.anyLong())).thenReturn(true);
        Mockito.when(_resourceCountDao.removeEntriesByOwner(Mockito.anyLong(), Mockito.eq(ResourceOwnerType.Domain))).thenReturn(1l);
        Mockito.when(_resourceLimitDao.removeEntriesByOwner(Mockito.anyLong(), Mockito.eq(ResourceOwnerType.Domain))).thenReturn(1l);
        Mockito.when(_configMgr.releaseDomainSpecificVirtualRanges(Mockito.any())).thenReturn(true);

        try {
            Assert.assertTrue(domainManager.deleteDomain(20l, true));
        } finally {
            CallContext.unregister();
        }
    }

    @Test
    public void validateNetworkDomainTestNullNetworkDomainDoesNotCallVerifyDomainName() {
        try (MockedStatic<NetUtils> ignored = Mockito.mockStatic(NetUtils.class)) {
            Mockito.when(NetUtils.verifyDomainName(Mockito.anyString())).thenReturn(true);
            domainManager.validateNetworkDomain(null);
            Mockito.verify(NetUtils.class, Mockito.never());
            NetUtils.verifyDomainName(Mockito.anyString());
        }
    }

    @Test (expected = InvalidParameterValueException.class)
    public void validateNetworkDomainTestInvalidNetworkDomainThrowsException(){
        domainManager.validateNetworkDomain(" t e s t");
    }

    @Test
    public void validateNetworkDomainTestValidNetworkDomainDoesNotThrowException(){
        domainManager.validateNetworkDomain("test");
    }

    @Test (expected = InvalidParameterValueException.class)
    public void validateUniqueDomainNameTestExistingNameThrowsException() {
        SearchCriteria scMock = Mockito.mock(SearchCriteria.class);
        ArrayList<DomainVO> listMock = Mockito.mock(ArrayList.class);
        Mockito.doReturn(scMock).when(domainDaoMock).createSearchCriteria();
        Mockito.doReturn(listMock).when(domainDaoMock).search(Mockito.any(SearchCriteria.class), Mockito.any());
        Mockito.doReturn(false).when(listMock).isEmpty();
        domainManager.validateUniqueDomainName("test", 1L);
    }

    @Test
    public void validateUniqueDomainNameTestUniqueNameDoesNotThrowException() {
        SearchCriteria scMock = Mockito.mock(SearchCriteria.class);
        ArrayList<DomainVO> listMock = Mockito.mock(ArrayList.class);
        Mockito.doReturn(scMock).when(domainDaoMock).createSearchCriteria();
        Mockito.doReturn(listMock).when(domainDaoMock).search(Mockito.any(SearchCriteria.class), Mockito.any());
        Mockito.doReturn(true).when(listMock).isEmpty();
        domainManager.validateUniqueDomainName("testUnique", 1L);
        Mockito.verify(domainDaoMock).createSearchCriteria();
        Mockito.verify(scMock).addAnd("name", SearchCriteria.Op.EQ, "testUnique");
        Mockito.verify(scMock).addAnd("parent", SearchCriteria.Op.EQ, 1L);
        Mockito.verify(domainDaoMock).search(scMock,null);
        Mockito.verify(listMock).isEmpty();
    }

    @Test
    public void createDomainVoTestCreateValidUuidIfEmptyString(){
        DomainVO domainVo = domainManager.createDomainVo("test",1L,2L,"NetworkTest","");
        Assert.assertTrue(UuidUtils.isUuid(domainVo.getUuid()));
    }

    @Test
    public void createDomainVoTestCreateValidUuidIfWhiteSpace(){
        DomainVO domainVo = domainManager.createDomainVo("test",1L,2L,"NetworkTest","  ");
        Assert.assertTrue(UuidUtils.isUuid(domainVo.getUuid()));
    }

    @Test
    public void createDomainVoTestCreateValidUuidIfNull(){
        DomainVO domainVo = domainManager.createDomainVo("test",1L,2L,"NetworkTest",null);
        Assert.assertTrue(UuidUtils.isUuid(domainVo.getUuid()));
    }

    @Test
    public void createDomainVoTestValidInformedUuid(){
        DomainVO domainVo = domainManager.createDomainVo("test",1L,2L,"NetworkTest","testUuid");
        Assert.assertEquals("testUuid", domainVo.getUuid());
    }

    @Test
    public void createDomainTest(){
        Mockito.doNothing().when(domainManager).validateDomainNameAndNetworkDomain(Mockito.any(String.class), Mockito.any(Long.class), Mockito.any(String.class));

        DomainVO domainVoMock = Mockito.mock(DomainVO.class);
        Mockito.doReturn(domainVoMock).when(domainManager).createDomainVo("test",1L,2L,"netTest","uuidTest");
        Mockito.doReturn(domainVoMock).when(domainDaoMock).create(domainVoMock);
        try (MockedStatic<CallContext> ignored = Mockito.mockStatic(CallContext.class)) {
            CallContext callContextMock = Mockito.mock(CallContext.class);
            Mockito.when(CallContext.current()).thenReturn(callContextMock);

            Domain actualDomain = domainManager.createDomain("test", 1L, 2L, "netTest", "uuidTest");

            Mockito.verify(domainManager).validateDomainNameAndNetworkDomain("test", 1L, "netTest");
            Mockito.verify(domainManager).createDomainVo("test", 1L, 2L, "netTest", "uuidTest");

            Mockito.verify(domainDaoMock).create(domainVoMock);
            Mockito.verify(_resourceCountDao).createResourceCounts(domainVoMock.getId(), ResourceLimit.ResourceOwnerType.Domain);
            CallContext.current();
            Mockito.verify(callContextMock).putContextParameter(Domain.class, domainVoMock.getUuid());
            Mockito.verify(_messageBus).publish("DomainManagerImpl", DomainManager.MESSAGE_ADD_DOMAIN_EVENT, PublishScope.LOCAL, domainVoMock.getId());
            Assert.assertEquals(domainVoMock, actualDomain);
        }
    }

}
