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

package com.cloud.network.vpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cloud.exception.PermissionDeniedException;
import com.cloud.network.vpc.dao.VpcDao;
import com.cloud.utils.net.NetUtils;
import org.apache.cloudstack.acl.SecurityChecker.AccessType;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.command.user.network.CreateNetworkACLCmd;
import org.apache.cloudstack.api.command.user.network.MoveNetworkAclItemCmd;
import org.apache.cloudstack.api.command.user.network.UpdateNetworkACLItemCmd;
import org.apache.cloudstack.api.command.user.network.UpdateNetworkACLListCmd;
import org.apache.cloudstack.context.CallContext;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.Network;
import com.cloud.network.NetworkModel;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.vpc.NetworkACLItem.Action;
import com.cloud.network.vpc.NetworkACLItem.TrafficType;
import com.cloud.network.vpc.dao.NetworkACLDao;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.User;
import com.cloud.utils.db.EntityManager;
import com.cloud.utils.exception.CloudRuntimeException;
import org.junit.After;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NetworkACLServiceImplTest {

    @Spy
    @InjectMocks
    private NetworkACLServiceImpl networkAclServiceImpl = new NetworkACLServiceImpl();
    @Mock
    private NetworkModel networkModelMock;
    @Mock
    private NetworkACLManager networkAclManagerMock;
    @Mock
    private NetworkACLItemDao networkAclItemDaoMock;
    @Mock
    private EntityManager entityManagerMock;
    @Mock
    private VpcDao vpcDaoMock;
    @Mock
    private AccountManager accountManagerMock;
    @Mock
    private NetworkACLDao networkAclDaoMock;

    @Mock
    private CreateNetworkACLCmd createNetworkAclCmdMock;
    @Mock
    private UpdateNetworkACLItemCmd updateNetworkACLItemCmdMock;
    @Mock
    private Network networkMock;
    @Mock
    private NetworkACLVO networkAclMock;
    @Mock
    private NetworkACLItemVO networkAclItemVoMock;
    @Mock
    private NetworkACLVO networkACLVOMock;
    @Mock
    private UpdateNetworkACLListCmd updateNetworkACLListCmdMock;

    private Long networkAclMockId = 5L;
    private Long networkOfferingMockId = 2L;
    private Long networkMockVpcMockId = 3L;
    private long networkAclListId = 1l;
    private static final String SOME_UUID = "someUuid";

    @Mock
    private MoveNetworkAclItemCmd moveNetworkAclItemCmdMock;
    @Mock
    private NetworkACLItemVO aclRuleBeingMovedMock;
    private String uuidAclRuleBeingMoved = "uuidRuleBeingMoved";

    @Mock
    private NetworkACLItemVO previousAclRuleMock;
    private String previousAclRuleUuid = "uuidPreviousAclRule";

    @Mock
    private NetworkACLItemVO nextAclRuleMock;
    private String nextAclRuleUuid = "uuidNextAclRule";

    @Mock
    private CallContext callContextMock;

    @Mock
    private VpcVO vpcVOMock;

    @Mock
    private Account accountMock;

    private MockedStatic<CallContext> callContextMocked;

    @Before
    public void setup() {
        callContextMocked = Mockito.mockStatic(CallContext.class);
        Mockito.when(CallContext.current()).thenReturn(callContextMock);
        Mockito.doReturn(Mockito.mock(User.class)).when(callContextMock).getCallingUser();
        Mockito.doReturn(Mockito.mock(Account.class)).when(callContextMock).getCallingAccount();

        Mockito.when(networkAclDaoMock.findById(networkAclListId)).thenReturn(networkACLVOMock);
        Mockito.when(createNetworkAclCmdMock.getNetworkId()).thenReturn(1L);
        Mockito.when(createNetworkAclCmdMock.getProtocol()).thenReturn("tcp");

        Mockito.when(networkMock.getNetworkOfferingId()).thenReturn(networkOfferingMockId);
        Mockito.when(networkMock.getVpcId()).thenReturn(networkMockVpcMockId);

        Mockito.when(moveNetworkAclItemCmdMock.getUuidRuleBeingMoved()).thenReturn(uuidAclRuleBeingMoved);

        Mockito.when(aclRuleBeingMovedMock.getAclId()).thenReturn(networkAclMockId);

        Mockito.when(networkAclMock.getVpcId()).thenReturn(networkMockVpcMockId);
    }

    @After
    public void tearDown() throws Exception {
        callContextMocked.close();
    }

    @Test
    public void createNetworkACLItemTestAclNumberNull() {
        createNetworkACLItemTestForNumberAndExecuteTest(null);
    }

    @Test
    public void createNetworkACLItemTestAclNumberNotNull() {
        createNetworkACLItemTestForNumberAndExecuteTest(10);
    }

    private void createNetworkACLItemTestForNumberAndExecuteTest(Integer number) {
        Mockito.when(createNetworkAclCmdMock.getNumber()).thenReturn(number);

        Mockito.doReturn(networkAclMockId).when(networkAclServiceImpl).createAclListIfNeeded(createNetworkAclCmdMock);
        Mockito.when(networkAclManagerMock.getNetworkACL(networkAclMockId)).thenReturn(networkAclMock);

        Mockito.doNothing().when(networkAclServiceImpl).validateAclRuleNumber(createNetworkAclCmdMock, networkAclMock);
        Mockito.doNothing().when(networkAclServiceImpl).validateNetworkAcl(networkAclMock);

        Mockito.when(networkAclItemDaoMock.getMaxNumberByACL(networkAclMockId)).thenReturn(5);

        Mockito.doNothing().when(networkAclServiceImpl).validateNetworkACLItem(Mockito.any(NetworkACLItemVO.class));
        Mockito.when(networkAclManagerMock.createNetworkACLItem(Mockito.any(NetworkACLItemVO.class))).thenAnswer(new Answer<NetworkACLItemVO>() {
            @Override
            public NetworkACLItemVO answer(InvocationOnMock invocation) throws Throwable {
                return (NetworkACLItemVO)invocation.getArguments()[0];
            }
        });

        NetworkACLItem networkAclRuleCreated = networkAclServiceImpl.createNetworkACLItem(createNetworkAclCmdMock);

        Assert.assertEquals(number == null ? 6 : number, networkAclRuleCreated.getNumber());

        InOrder inOrder = Mockito.inOrder( networkAclServiceImpl, networkAclManagerMock, networkAclItemDaoMock);
        inOrder.verify(networkAclServiceImpl).createAclListIfNeeded(createNetworkAclCmdMock);
        inOrder.verify(networkAclManagerMock).getNetworkACL(networkAclMockId);
        inOrder.verify(networkAclServiceImpl).validateNetworkAcl(networkAclMock);
        inOrder.verify(networkAclServiceImpl).validateAclRuleNumber(createNetworkAclCmdMock, networkAclMock);
        inOrder.verify(networkAclServiceImpl).validateAndCreateNetworkAclRuleAction(nullable(String.class));
        inOrder.verify(networkAclItemDaoMock, Mockito.times(number == null ? 1 : 0)).getMaxNumberByACL(networkAclMockId);
        inOrder.verify(networkAclServiceImpl).validateNetworkACLItem(Mockito.any(NetworkACLItemVO.class));
        inOrder.verify(networkAclManagerMock).createNetworkACLItem(Mockito.any(NetworkACLItemVO.class));
    }

    @Test
    public void createAclListIfNeededTestAclRuleListIdNotNull() {
        Long expectedAclListId = 1L;
        Mockito.when(createNetworkAclCmdMock.getACLId()).thenReturn(expectedAclListId);

        Long returnetAclListId = networkAclServiceImpl.createAclListIfNeeded(createNetworkAclCmdMock);

        Assert.assertEquals(expectedAclListId, returnetAclListId);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void createAclListIfNeededTestAclRuleListIdNullAndNetworkDoesNotHaveVpc() {
        Mockito.when(createNetworkAclCmdMock.getACLId()).thenReturn(null);

        long networkId = 1L;
        Mockito.when(createNetworkAclCmdMock.getNetworkId()).thenReturn(networkId);

        Network networkMock = Mockito.mock(Network.class);
        Mockito.when(networkMock.getVpcId()).thenReturn(null);

        Mockito.doReturn(networkMock).when(networkModelMock).getNetwork(networkId);

        networkAclServiceImpl.createAclListIfNeeded(createNetworkAclCmdMock);
    }

    @Test
    public void createAclListIfNeededTestAclRuleListIdNullAndNetworkWithVpcAndNotAclListYet() {
        Mockito.when(createNetworkAclCmdMock.getACLId()).thenReturn(null);

        long networkId = 1L;
        Mockito.when(createNetworkAclCmdMock.getNetworkId()).thenReturn(networkId);

        Network networkMock = Mockito.mock(Network.class);
        Mockito.when(networkMock.getVpcId()).thenReturn(12L);
        Mockito.when(networkMock.getNetworkACLId()).thenReturn(null);

        Mockito.doReturn(networkMock).when(networkModelMock).getNetwork(networkId);

        Long expectedAclListId = 15L;
        Mockito.doReturn(expectedAclListId).when(networkAclServiceImpl).createAclListForNetworkAndReturnAclListId(createNetworkAclCmdMock, networkMock);

        Long aclIdReturned = networkAclServiceImpl.createAclListIfNeeded(createNetworkAclCmdMock);

        Assert.assertEquals(expectedAclListId, aclIdReturned);
        Mockito.verify(networkAclServiceImpl).createAclListForNetworkAndReturnAclListId(createNetworkAclCmdMock, networkMock);
    }

    @Test
    public void createAclListIfNeededTestAclRuleListIdNullAndNetworkWithVpcAndAclListAlreadyCreated() {
        Mockito.when(createNetworkAclCmdMock.getACLId()).thenReturn(null);

        long networkId = 1L;
        Mockito.when(createNetworkAclCmdMock.getNetworkId()).thenReturn(networkId);
        Network networkMock = Mockito.mock(Network.class);

        Mockito.when(networkMock.getVpcId()).thenReturn(12L);
        Long expectedAclListId = 15L;
        Mockito.when(networkMock.getNetworkACLId()).thenReturn(expectedAclListId);

        Mockito.doReturn(networkMock).when(networkModelMock).getNetwork(networkId);

        Long aclIdReturned = networkAclServiceImpl.createAclListIfNeeded(createNetworkAclCmdMock);

        Assert.assertEquals(expectedAclListId, aclIdReturned);
        Mockito.verify(networkAclServiceImpl, Mockito.times(0)).createAclListForNetworkAndReturnAclListId(createNetworkAclCmdMock, networkMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void createAclListForNetworkAndReturnAclListIdTestServicesNotSupportedByNetworkOffering() {
        Mockito.doReturn(false).when(networkModelMock).areServicesSupportedByNetworkOffering(networkOfferingMockId, Network.Service.NetworkACL);
        networkAclServiceImpl.createAclListForNetworkAndReturnAclListId(createNetworkAclCmdMock, networkMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void createAclListForNetworkAndReturnAclListIdTestServicesSupportedByNetworkOfferingButVpcNotFound() {
        Mockito.doReturn(true).when(networkModelMock).areServicesSupportedByNetworkOffering(networkOfferingMockId, Network.Service.NetworkACL);
        Mockito.doReturn(null).when(entityManagerMock).findById(Vpc.class, networkMockVpcMockId);

        networkAclServiceImpl.createAclListForNetworkAndReturnAclListId(createNetworkAclCmdMock, networkMock);
    }

    @Test(expected = CloudRuntimeException.class)
    public void createAclListForNetworkAndReturnAclListIdTestCreateNetworkAclReturnsNull() {
        Mockito.doReturn(true).when(networkModelMock).areServicesSupportedByNetworkOffering(networkOfferingMockId, Network.Service.NetworkACL);
        Mockito.doReturn(Mockito.mock(Vpc.class)).when(entityManagerMock).findById(Vpc.class, networkMockVpcMockId);
        Mockito.doReturn(null).when(networkAclManagerMock).createNetworkACL(Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyBoolean());

        networkAclServiceImpl.createAclListForNetworkAndReturnAclListId(createNetworkAclCmdMock, networkMock);
    }

    @Test(expected = CloudRuntimeException.class)
    public void createAclListForNetworkAndReturnAclListIdTestAclNetworkIsCreatedButNotApplied() throws ResourceUnavailableException {
        Mockito.doReturn(true).when(networkModelMock).areServicesSupportedByNetworkOffering(networkOfferingMockId, Network.Service.NetworkACL);
        Mockito.doReturn(Mockito.mock(Vpc.class)).when(entityManagerMock).findById(Vpc.class, networkMockVpcMockId);
        Mockito.doReturn(Mockito.mock(NetworkACL.class)).when(networkAclManagerMock).createNetworkACL(Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyBoolean());
        Mockito.doReturn(false).when(networkAclManagerMock).replaceNetworkACL(Mockito.any(NetworkACL.class), Mockito.any(NetworkVO.class));

        NetworkVO networkVoMock = new NetworkVO();
        networkVoMock.setNetworkOfferingId(networkOfferingMockId);
        networkVoMock.setVpcId(networkMockVpcMockId);

        networkAclServiceImpl.createAclListForNetworkAndReturnAclListId(createNetworkAclCmdMock, networkVoMock);
    }

    @Test(expected = CloudRuntimeException.class)
    public void createAclListForNetworkAndReturnAclListIdTestAclNetworkIsCreatedButNotAppliedWithException() throws ResourceUnavailableException {
        Mockito.doReturn(true).when(networkModelMock).areServicesSupportedByNetworkOffering(networkOfferingMockId, Network.Service.NetworkACL);
        Mockito.doReturn(Mockito.mock(Vpc.class)).when(entityManagerMock).findById(Vpc.class, networkMockVpcMockId);
        Mockito.doReturn(Mockito.mock(NetworkACL.class)).when(networkAclManagerMock).createNetworkACL(Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyBoolean());

        Mockito.doThrow(ResourceUnavailableException.class).when(networkAclManagerMock).replaceNetworkACL(Mockito.any(NetworkACL.class), Mockito.any(NetworkVO.class));

        NetworkVO networkVoMock = new NetworkVO();
        networkVoMock.setNetworkOfferingId(networkOfferingMockId);
        networkVoMock.setVpcId(networkMockVpcMockId);

        networkAclServiceImpl.createAclListForNetworkAndReturnAclListId(createNetworkAclCmdMock, networkVoMock);
    }

    @Test
    public void createAclListForNetworkAndReturnAclListIdTestAclIsCreatedAndAppliedWithSuccess() throws ResourceUnavailableException {
        Mockito.doReturn(true).when(networkModelMock).areServicesSupportedByNetworkOffering(networkOfferingMockId, Network.Service.NetworkACL);
        Mockito.doReturn(Mockito.mock(Vpc.class)).when(entityManagerMock).findById(Vpc.class, networkMockVpcMockId);

        NetworkACL networkAclMock = Mockito.mock(NetworkACL.class);
        Long expectedNetworkAclId = 5L;
        Mockito.when(networkAclMock.getId()).thenReturn(expectedNetworkAclId);
        Mockito.doReturn(networkAclMock).when(networkAclManagerMock).createNetworkACL(Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyBoolean());

        Mockito.doReturn(true).when(networkAclManagerMock).replaceNetworkACL(Mockito.any(NetworkACL.class), Mockito.any(NetworkVO.class));

        NetworkVO networkVoMock = new NetworkVO();
        networkVoMock.setNetworkOfferingId(networkOfferingMockId);
        networkVoMock.setVpcId(networkMockVpcMockId);

        Long networkAclIdReceived = networkAclServiceImpl.createAclListForNetworkAndReturnAclListId(createNetworkAclCmdMock, networkVoMock);

        Assert.assertEquals(expectedNetworkAclId, networkAclIdReceived);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateAclRuleNumberTestNumberLessThanOne() {
        Mockito.when(createNetworkAclCmdMock.getNumber()).thenReturn(0);
        networkAclServiceImpl.validateAclRuleNumber(createNetworkAclCmdMock, networkAclMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateAclRuleNumberTestNumberNegative() {
        Mockito.when(createNetworkAclCmdMock.getNumber()).thenReturn(-1);
        networkAclServiceImpl.validateAclRuleNumber(createNetworkAclCmdMock, networkAclMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateAclRuleNumberTestNumberInOtherAcl() {
        Mockito.when(createNetworkAclCmdMock.getNumber()).thenReturn(1);
        Mockito.doReturn(Mockito.mock(NetworkACLItemVO.class)).when(networkAclItemDaoMock).findByAclAndNumber(Mockito.anyLong(), Mockito.anyInt());

        networkAclServiceImpl.validateAclRuleNumber(createNetworkAclCmdMock, networkAclMock);

        Mockito.verify(networkAclItemDaoMock).findByAclAndNumber(Mockito.anyLong(), Mockito.anyInt());
    }

    @Test
    public void validateAclRuleNumberTestHappyDay() {
        Mockito.when(createNetworkAclCmdMock.getNumber()).thenReturn(1);
        Mockito.doReturn(null).when(networkAclItemDaoMock).findByAclAndNumber(Mockito.anyLong(), Mockito.anyInt());

        networkAclServiceImpl.validateAclRuleNumber(createNetworkAclCmdMock, networkAclMock);
        Mockito.verify(networkAclItemDaoMock).findByAclAndNumber(Mockito.anyLong(), Mockito.anyInt());
    }

    @Test
    public void validateAclRuleNumberTestNumberNull() {
        Mockito.when(createNetworkAclCmdMock.getNumber()).thenReturn(null);

        networkAclServiceImpl.validateAclRuleNumber(createNetworkAclCmdMock, networkAclMock);
        Mockito.verify(networkAclItemDaoMock, Mockito.times(0)).findByAclAndNumber(Mockito.anyLong(), Mockito.anyInt());
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateNetworkAclTestAclNull() {
        networkAclServiceImpl.validateNetworkAcl(null);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateNetworkAclTestAclDefaulAllow() {
        Mockito.when(networkAclMock.getId()).thenReturn(2L);
        networkAclServiceImpl.validateNetworkAcl(networkAclMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateNetworkAclTestAclDefaulDeny() {
        Mockito.when(networkAclMock.getId()).thenReturn(1L);
        networkAclServiceImpl.validateNetworkAcl(networkAclMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateNetworkAclTestAclNotDefaulWithoutVpc() {
        Mockito.when(networkAclMock.getId()).thenReturn(3L);
        Mockito.doReturn(null).when(vpcDaoMock).findById(networkMockVpcMockId);

        networkAclServiceImpl.validateNetworkAcl(networkAclMock);
    }

    @Test
    public void validateNetworkAclTestAclNotDefaultWithVpc() {
        CallContext callContextMock = Mockito.mock(CallContext.class);
        Mockito.doReturn(Mockito.mock(Account.class)).when(callContextMock).getCallingAccount();

        Mockito.when(CallContext.current()).thenReturn(callContextMock);

        Mockito.when(networkAclMock.getId()).thenReturn(3L);
        Mockito.when(networkAclMock.getVpcId()).thenReturn(networkMockVpcMockId);

        Mockito.doReturn(vpcVOMock).when(vpcDaoMock).findById(networkMockVpcMockId);
        Mockito.doNothing().when(accountManagerMock).checkAccess(Mockito.any(Account.class), Mockito.isNull(AccessType.class), Mockito.eq(true), Mockito.any(Vpc.class));

        networkAclServiceImpl.validateNetworkAcl(networkAclMock);

        Mockito.verify(accountManagerMock).checkAccess(Mockito.any(Account.class), Mockito.isNull(AccessType.class), Mockito.eq(true), Mockito.any(Vpc.class));

        callContextMocked.verify(() -> CallContext.current());

    }

    @Test
    public void validateAndCreateNetworkAclRuleActionTestActionNull() {
        NetworkACLItem.Action receivedAction = networkAclServiceImpl.validateAndCreateNetworkAclRuleAction(null);

        Assert.assertEquals(NetworkACLItem.Action.Allow, receivedAction);

        Mockito.verify(networkAclServiceImpl).validateNetworkAclRuleAction(null);
    }

    @Test
    public void validateAndCreateNetworkAclRuleActionTestActionAllow() {
        NetworkACLItem.Action receivedAction = networkAclServiceImpl.validateAndCreateNetworkAclRuleAction("allow");

        Assert.assertEquals(NetworkACLItem.Action.Allow, receivedAction);
        Mockito.verify(networkAclServiceImpl).validateNetworkAclRuleAction("allow");
    }

    @Test
    public void validateAndCreateNetworkAclRuleActionTestActionDeny() {
        NetworkACLItem.Action receivedAction = networkAclServiceImpl.validateAndCreateNetworkAclRuleAction("deny");

        Assert.assertEquals(NetworkACLItem.Action.Deny, receivedAction);
        Mockito.verify(networkAclServiceImpl).validateNetworkAclRuleAction("deny");
    }

    @Test
    public void validateNetworkAclRuleActionTestActionNull() {
        networkAclServiceImpl.validateNetworkAclRuleAction(null);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateNetworkAclRuleActionTestInvalidAction() {
        networkAclServiceImpl.validateNetworkAclRuleAction("Invalid");
    }

    @Test
    public void validateNetworkAclRuleActionTestValidActions() {
        networkAclServiceImpl.validateNetworkAclRuleAction("deny");
        networkAclServiceImpl.validateNetworkAclRuleAction("allow");
    }

    @Test
    public void validateNetworkACLItemTest() {
        Mockito.doNothing().when(networkAclServiceImpl).validateSourceStartAndEndPorts(networkAclItemVoMock);
        Mockito.doNothing().when(networkAclServiceImpl).validateSourceCidrList(networkAclItemVoMock);
        Mockito.doNothing().when(networkAclServiceImpl).validateProtocol(networkAclItemVoMock);

        networkAclServiceImpl.validateNetworkACLItem(networkAclItemVoMock);

        InOrder inOrder = Mockito.inOrder(networkAclServiceImpl);
        inOrder.verify(networkAclServiceImpl).validateSourceStartAndEndPorts(networkAclItemVoMock);
        inOrder.verify(networkAclServiceImpl).validateSourceCidrList(networkAclItemVoMock);
        inOrder.verify(networkAclServiceImpl).validateProtocol(networkAclItemVoMock);
    }

    @Test
    public void validateSourceStartAndEndPortsTestBothPortsNull() {
        networkAclServiceImpl.validateSourceStartAndEndPorts(networkAclItemVoMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateSourceStartAndEndPortsTestStartPorInvalid() {
        Mockito.when(networkAclItemVoMock.getSourcePortStart()).thenReturn(65536);

        networkAclServiceImpl.validateSourceStartAndEndPorts(networkAclItemVoMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateSourceStartAndEndPortsTestStartPorValidButEndPortInvalid() {
        Mockito.when(networkAclItemVoMock.getSourcePortStart()).thenReturn(65535);
        Mockito.when(networkAclItemVoMock.getSourcePortEnd()).thenReturn(65536);

        networkAclServiceImpl.validateSourceStartAndEndPorts(networkAclItemVoMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateSourceStartAndEndPortsTestStartPortBiggerThanEndPort() {
        Mockito.when(networkAclItemVoMock.getSourcePortStart()).thenReturn(65535);
        Mockito.when(networkAclItemVoMock.getSourcePortEnd()).thenReturn(2);

        networkAclServiceImpl.validateSourceStartAndEndPorts(networkAclItemVoMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateSourceStartAndEndPortsTestPortsWithAllProtocol() {
        Mockito.when(networkAclItemVoMock.getSourcePortStart()).thenReturn(1);
        Mockito.when(networkAclItemVoMock.getSourcePortEnd()).thenReturn(2);
        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn("all");

        networkAclServiceImpl.validateSourceStartAndEndPorts(networkAclItemVoMock);
    }

    @Test
    public void validateSourceStartAndEndPortsTestPortsWithTcpProtocol() {
        Mockito.when(networkAclItemVoMock.getSourcePortStart()).thenReturn(1);
        Mockito.when(networkAclItemVoMock.getSourcePortEnd()).thenReturn(2);
        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn("tcp");

        networkAclServiceImpl.validateSourceStartAndEndPorts(networkAclItemVoMock);
    }

    @Test
    public void validateSourceCidrListTestEmptySourceCirdList() {
        Mockito.when(networkAclItemVoMock.getSourceCidrList()).thenReturn(new ArrayList<>());
        networkAclServiceImpl.validateSourceCidrList(networkAclItemVoMock);
    }

    @Test(expected = ServerApiException.class)
    public void validateSourceCidrListTestInvalidCidrs() {
        ArrayList<String> cidrsInvalid = new ArrayList<>();
        cidrsInvalid.add("256.0.0.0./32");

        Mockito.when(networkAclItemVoMock.getSourceCidrList()).thenReturn(cidrsInvalid);
        networkAclServiceImpl.validateSourceCidrList(networkAclItemVoMock);
    }

    @Test
    public void validateSourceCidrListTestValidCidrs() {
        ArrayList<String> cidrsInvalid = new ArrayList<>();
        cidrsInvalid.add("192.168.12.0/24");

        Mockito.when(networkAclItemVoMock.getSourceCidrList()).thenReturn(cidrsInvalid);
        networkAclServiceImpl.validateSourceCidrList(networkAclItemVoMock);
    }

    @Test
    public void validateProtocolTestProtocolIsNullOrBlank() {

        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn(null);
        networkAclServiceImpl.validateProtocol(networkAclItemVoMock);

        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn("");
        networkAclServiceImpl.validateProtocol(networkAclItemVoMock);

        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn("    ");
        networkAclServiceImpl.validateProtocol(networkAclItemVoMock);

        Mockito.verify(networkAclServiceImpl, Mockito.times(0)).validateIcmpTypeAndCode(networkAclItemVoMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateProtocolTestProtocolIsNumericValueLessThanZero() {
        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn("-1");
        networkAclServiceImpl.validateProtocol(networkAclItemVoMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateProtocolTestProtocolIsNumericValueMoreThan255() {
        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn("256");
        networkAclServiceImpl.validateProtocol(networkAclItemVoMock);
    }

    @Test
    public void validateProtocolTestProtocolIsNumericValidValue() {
        Mockito.doNothing().when(networkAclServiceImpl).validateIcmpTypeAndCode(networkAclItemVoMock);

        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn("255");
        Mockito.when(networkAclItemVoMock.getIcmpCode()).thenReturn(null);
        Mockito.when(networkAclItemVoMock.getIcmpType()).thenReturn(null);

        networkAclServiceImpl.validateProtocol(networkAclItemVoMock);

        Mockito.verify(networkAclServiceImpl).validateIcmpTypeAndCode(networkAclItemVoMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateProtocolTestProtocolIsStringInvalid() {
        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn("invalid");
        networkAclServiceImpl.validateProtocol(networkAclItemVoMock);
    }

    @Test
    public void validateProtocolTestProtocolIsStringValid() {
        Mockito.doNothing().when(networkAclServiceImpl).validateIcmpTypeAndCode(networkAclItemVoMock);

        Mockito.when(networkAclItemVoMock.getIcmpCode()).thenReturn(null);
        Mockito.when(networkAclItemVoMock.getIcmpType()).thenReturn(null);

        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn("tcp");
        networkAclServiceImpl.validateProtocol(networkAclItemVoMock);

        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn("all");
        networkAclServiceImpl.validateProtocol(networkAclItemVoMock);

        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn("udp");
        networkAclServiceImpl.validateProtocol(networkAclItemVoMock);

        Mockito.verify(networkAclServiceImpl, Mockito.times(3)).validateIcmpTypeAndCode(networkAclItemVoMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateProtocolTestProtocolNotIcmpWithIcmpConfigurations() {
        Mockito.when(networkAclItemVoMock.getIcmpCode()).thenReturn(2);
        Mockito.when(networkAclItemVoMock.getIcmpType()).thenReturn(3);

        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn("tcp");
        networkAclServiceImpl.validateProtocol(networkAclItemVoMock);
    }

    @Test
    public void validateProtocolTestProtocolNotIcmpWithSourcePorts() {
        Mockito.doNothing().when(networkAclServiceImpl).validateIcmpTypeAndCode(networkAclItemVoMock);

        Mockito.when(networkAclItemVoMock.getIcmpCode()).thenReturn(null);
        Mockito.when(networkAclItemVoMock.getIcmpType()).thenReturn(null);

        Mockito.when(networkAclItemVoMock.getSourcePortStart()).thenReturn(1);
        Mockito.when(networkAclItemVoMock.getSourcePortEnd()).thenReturn(1);

        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn("tcp");
        networkAclServiceImpl.validateProtocol(networkAclItemVoMock);

        Mockito.verify(networkAclServiceImpl).validateIcmpTypeAndCode(networkAclItemVoMock);
    }

    @Test
    public void validateProtocolTestProtocolIcmpWithIcmpConfigurations() {
        Mockito.when(networkAclItemVoMock.getIcmpCode()).thenReturn(2);
        Mockito.when(networkAclItemVoMock.getIcmpType()).thenReturn(3);

        Mockito.when(networkAclItemVoMock.getSourcePortStart()).thenReturn(null);
        Mockito.when(networkAclItemVoMock.getSourcePortEnd()).thenReturn(null);

        Mockito.doNothing().when(networkAclServiceImpl).validateIcmpTypeAndCode(networkAclItemVoMock);

        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn("icmp");
        networkAclServiceImpl.validateProtocol(networkAclItemVoMock);

        Mockito.verify(networkAclServiceImpl).validateIcmpTypeAndCode(networkAclItemVoMock);
    }

    @Test
    public void validateIcmpTypeAndCodeTestIcmpTypeNull() {
        Mockito.when(networkAclItemVoMock.getIcmpType()).thenReturn(null);

        networkAclServiceImpl.validateIcmpTypeAndCode(networkAclItemVoMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateIcmpTypeAndCodeTestIcmpTypeInvalid() {
        Mockito.when(networkAclItemVoMock.getIcmpType()).thenReturn(256);

        networkAclServiceImpl.validateIcmpTypeAndCode(networkAclItemVoMock);
    }

    @Test
    public void validateIcmpTypeAndCodeTestIcmpTypeNegativeOne() {
        Mockito.when(networkAclItemVoMock.getIcmpType()).thenReturn(-1);
        Mockito.when(networkAclItemVoMock.getIcmpCode()).thenReturn(null);

        networkAclServiceImpl.validateIcmpTypeAndCode(networkAclItemVoMock);
    }

    @Test
    public void validateIcmpTypeAndCodeTestIcmpTypeNegativeOneAndIcmpCodeNegativeOne() {
        Mockito.when(networkAclItemVoMock.getIcmpType()).thenReturn(-1);
        Mockito.when(networkAclItemVoMock.getIcmpCode()).thenReturn(-1);

        networkAclServiceImpl.validateIcmpTypeAndCode(networkAclItemVoMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateIcmpTypeAndCodeTestIcmpTypeValidAndIcmpCodeInvalid() {
        Mockito.when(networkAclItemVoMock.getIcmpType()).thenReturn(255);
        Mockito.when(networkAclItemVoMock.getIcmpCode()).thenReturn(17);

        networkAclServiceImpl.validateIcmpTypeAndCode(networkAclItemVoMock);
    }

    @Test
    public void validateIcmpTypeAndCodeTestIcmpTypeValidAndIcmpCodeValid() {
        Mockito.when(networkAclItemVoMock.getIcmpType()).thenReturn(255);
        Mockito.when(networkAclItemVoMock.getIcmpCode()).thenReturn(1);

        networkAclServiceImpl.validateIcmpTypeAndCode(networkAclItemVoMock);
    }

    @Test
    public void updateNetworkACLItemTest() throws ResourceUnavailableException {
        Mockito.when(networkAclItemVoMock.getAclId()).thenReturn(networkAclMockId);
        Mockito.doReturn(networkAclItemVoMock).when(networkAclServiceImpl).validateNetworkAclRuleIdAndRetrieveIt(updateNetworkACLItemCmdMock);
        Mockito.doReturn(networkAclMock).when(networkAclManagerMock).getNetworkACL(networkAclMockId);
        Mockito.doNothing().when(networkAclServiceImpl).validateNetworkAcl(Mockito.eq(networkAclMock));
        Mockito.doNothing().when(networkAclServiceImpl).validateGlobalAclPermissionAndAclAssociatedToVpc(Mockito.any(NetworkACL.class), Mockito.any(Account.class), Mockito.anyString());
        Mockito.doNothing().when(networkAclServiceImpl).transferDataToNetworkAclRulePojo(Mockito.eq(updateNetworkACLItemCmdMock), Mockito.eq(networkAclItemVoMock), Mockito.eq(networkAclMock));
        Mockito.doNothing().when(networkAclServiceImpl).validateNetworkACLItem(networkAclItemVoMock);
        Mockito.doReturn(networkAclItemVoMock).when(networkAclManagerMock).updateNetworkACLItem(networkAclItemVoMock);

        CallContext callContextMock = Mockito.mock(CallContext.class);
        Mockito.doReturn(accountMock).when(callContextMock).getCallingAccount();
        Mockito.when(CallContext.current()).thenReturn(callContextMock);

        networkAclServiceImpl.updateNetworkACLItem(updateNetworkACLItemCmdMock);

        InOrder inOrder = Mockito.inOrder(networkAclServiceImpl, networkAclManagerMock);
        inOrder.verify(networkAclServiceImpl).validateNetworkAclRuleIdAndRetrieveIt(updateNetworkACLItemCmdMock);
        inOrder.verify(networkAclManagerMock).getNetworkACL(networkAclMockId);
        inOrder.verify(networkAclServiceImpl).validateNetworkAcl(networkAclMock);
        inOrder.verify(networkAclServiceImpl).validateGlobalAclPermissionAndAclAssociatedToVpc(networkAclMock, accountMock, "Only Root Admins can update global ACLs.");
        inOrder.verify(networkAclServiceImpl).transferDataToNetworkAclRulePojo(Mockito.eq(updateNetworkACLItemCmdMock), Mockito.eq(networkAclItemVoMock), Mockito.eq(networkAclMock));
        inOrder.verify(networkAclServiceImpl).validateNetworkACLItem(networkAclItemVoMock);
        inOrder.verify(networkAclManagerMock).updateNetworkACLItem(networkAclItemVoMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateNetworkAclRuleIdAndRetrieveItTestNetworkAclNotFound() {
        Mockito.doReturn(null).when(networkAclItemDaoMock).findById(Mockito.anyLong());

        networkAclServiceImpl.validateNetworkAclRuleIdAndRetrieveIt(updateNetworkACLItemCmdMock);
    }

    @Test
    public void validateNetworkAclRuleIdAndRetrieveItTestNetworkAclFound() {
        Mockito.doReturn(networkAclItemVoMock).when(networkAclItemDaoMock).findById(Mockito.anyLong());

        NetworkACLItemVO returnedNetworkAclItemVo = networkAclServiceImpl.validateNetworkAclRuleIdAndRetrieveIt(updateNetworkACLItemCmdMock);

        Assert.assertNotEquals(networkAclItemVoMock, returnedNetworkAclItemVo);
        Mockito.verify(networkAclItemVoMock).clone();
    }

    @Test(expected = InvalidParameterValueException.class)
    public void transferDataToNetworkAclRulePojoTestNumberOfAcltoBeUpdatedAlreadyInUse() {
        int aclNumberToUpdate = 1;
        Mockito.when(updateNetworkACLItemCmdMock.getNumber()).thenReturn(aclNumberToUpdate);
        Mockito.when(networkAclMock.getId()).thenReturn(networkAclMockId);
        Mockito.when(networkAclItemVoMock.getId()).thenReturn(100L);
        NetworkACLItemVO otherNetworkAclItemVoMock = Mockito.mock(NetworkACLItemVO.class);
        Mockito.when(otherNetworkAclItemVoMock.getId()).thenReturn(101L);
        Mockito.doReturn(otherNetworkAclItemVoMock).when(networkAclItemDaoMock).findByAclAndNumber(networkAclMockId, aclNumberToUpdate);

        networkAclServiceImpl.transferDataToNetworkAclRulePojo(updateNetworkACLItemCmdMock, networkAclItemVoMock, networkAclMock);
    }

    @Test
    public void transferDataToNetworkAclRulePojoTestPartialUpgradeAllValuesNull() {
        Mockito.when(updateNetworkACLItemCmdMock.isPartialUpgrade()).thenReturn(true);
        Mockito.when(updateNetworkACLItemCmdMock.getNumber()).thenReturn(null);
        Mockito.when(updateNetworkACLItemCmdMock.getSourcePortStart()).thenReturn(null);
        Mockito.when(updateNetworkACLItemCmdMock.getSourcePortEnd()).thenReturn(null);
        Mockito.when(updateNetworkACLItemCmdMock.getSourceCidrList()).thenReturn(null);
        Mockito.when(updateNetworkACLItemCmdMock.getProtocol()).thenReturn(null);
        Mockito.when(updateNetworkACLItemCmdMock.getAction()).thenReturn(null);
        Mockito.when(updateNetworkACLItemCmdMock.getTrafficType()).thenReturn(null);
        Mockito.when(updateNetworkACLItemCmdMock.getCustomId()).thenReturn(null);
        Mockito.when(updateNetworkACLItemCmdMock.getReason()).thenReturn(null);

        Mockito.when(updateNetworkACLItemCmdMock.isDisplay()).thenReturn(false);
        Mockito.when(networkAclItemVoMock.isDisplay()).thenReturn(false);

        networkAclServiceImpl.transferDataToNetworkAclRulePojo(updateNetworkACLItemCmdMock, networkAclItemVoMock, networkAclMock);

        Mockito.verify(networkAclItemVoMock, Mockito.times(0)).setNumber(Mockito.anyInt());
        Mockito.verify(networkAclItemVoMock, Mockito.times(0)).setSourcePortStart(Mockito.anyInt());
        Mockito.verify(networkAclItemVoMock, Mockito.times(0)).setSourcePortEnd(Mockito.anyInt());
        Mockito.verify(networkAclItemVoMock, Mockito.times(0)).setSourceCidrList(Mockito.anyList());
        Mockito.verify(networkAclItemVoMock, Mockito.times(0)).setProtocol(Mockito.anyString());
        Mockito.verify(networkAclServiceImpl).updateIcmpCodeAndType(Mockito.any(Boolean.class), Mockito.any(), Mockito.any());
        Mockito.verify(networkAclItemVoMock, Mockito.times(0)).setAction(Mockito.any(Action.class));
        Mockito.verify(networkAclItemVoMock, Mockito.times(0)).setTrafficType(Mockito.any(TrafficType.class));
        Mockito.verify(networkAclItemVoMock, Mockito.times(0)).setUuid(Mockito.anyString());
        Mockito.verify(networkAclItemVoMock, Mockito.times(0)).setReason(Mockito.anyString());
        Mockito.verify(networkAclItemVoMock, Mockito.times(0)).setDisplay(Mockito.anyBoolean());
        Mockito.verify(networkAclServiceImpl, Mockito.times(0)).validateAndCreateNetworkAclRuleAction(Mockito.anyString());
    }

    @Test
    public void transferDataToNetworkAclRulePojoTestNotPartialUpgradeAllValuesNull() {
        Mockito.when(updateNetworkACLItemCmdMock.isPartialUpgrade()).thenReturn(false);

        Mockito.when(updateNetworkACLItemCmdMock.getNumber()).thenReturn(null);
        Mockito.when(updateNetworkACLItemCmdMock.getSourcePortStart()).thenReturn(null);
        Mockito.when(updateNetworkACLItemCmdMock.getSourcePortEnd()).thenReturn(null);
        Mockito.when(updateNetworkACLItemCmdMock.getSourceCidrList()).thenReturn(null);
        Mockito.when(updateNetworkACLItemCmdMock.getProtocol()).thenReturn(null);
        Mockito.when(updateNetworkACLItemCmdMock.getAction()).thenReturn(null);
        Mockito.when(updateNetworkACLItemCmdMock.getTrafficType()).thenReturn(null);
        Mockito.when(updateNetworkACLItemCmdMock.getCustomId()).thenReturn(null);
        Mockito.when(updateNetworkACLItemCmdMock.getReason()).thenReturn(null);

        Mockito.when(updateNetworkACLItemCmdMock.isDisplay()).thenReturn(false);
        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn("");

        networkAclServiceImpl.transferDataToNetworkAclRulePojo(updateNetworkACLItemCmdMock, networkAclItemVoMock, networkAclMock);

        Mockito.verify(networkAclItemVoMock, Mockito.times(0)).setNumber(nullable(Integer.class));
        Mockito.verify(networkAclItemVoMock, Mockito.times(1)).setSourcePortStart(nullable(Integer.class));
        Mockito.verify(networkAclItemVoMock, Mockito.times(1)).setSourcePortEnd(nullable(Integer.class));
        Mockito.verify(networkAclItemVoMock, Mockito.times(1)).setSourceCidrList(nullable(List.class));
        Mockito.verify(networkAclItemVoMock, Mockito.times(1)).setProtocol(nullable(String.class));
        Mockito.verify(networkAclServiceImpl).updateIcmpCodeAndType(Mockito.any(Boolean.class), Mockito.any(), Mockito.any());
        Mockito.verify(networkAclItemVoMock, Mockito.times(1)).setAction(nullable(Action.class));
        Mockito.verify(networkAclItemVoMock, Mockito.times(1)).setTrafficType(nullable(TrafficType.class));
        Mockito.verify(networkAclItemVoMock, Mockito.times(0)).setUuid(nullable(String.class));
        Mockito.verify(networkAclItemVoMock, Mockito.times(1)).setReason(nullable(String.class));
        Mockito.verify(networkAclItemVoMock, Mockito.times(1)).setDisplay(nullable(Boolean.class));
        Mockito.verify(networkAclServiceImpl, Mockito.times(1)).validateAndCreateNetworkAclRuleAction(nullable(String.class));
    }

    @Test
    public void transferDataToNetworkAclRulePojoTestAllValuesWithUpdateData() {
        Mockito.when(updateNetworkACLItemCmdMock.getNumber()).thenReturn(1);
        Mockito.when(updateNetworkACLItemCmdMock.getSourcePortStart()).thenReturn(23);
        Mockito.when(updateNetworkACLItemCmdMock.getSourcePortEnd()).thenReturn(24);

        ArrayList<String> cidrsList = new ArrayList<>();
        cidrsList.add("192.168.6.0/24");
        Mockito.when(updateNetworkACLItemCmdMock.getSourceCidrList()).thenReturn(cidrsList);

        Mockito.when(updateNetworkACLItemCmdMock.getProtocol()).thenReturn("all");
        Mockito.when(updateNetworkACLItemCmdMock.getAction()).thenReturn("deny");
        Mockito.when(updateNetworkACLItemCmdMock.getTrafficType()).thenReturn(TrafficType.Egress);
        Mockito.when(updateNetworkACLItemCmdMock.getCustomId()).thenReturn("customUuid");
        Mockito.when(updateNetworkACLItemCmdMock.getReason()).thenReturn("reason");

        Mockito.when(updateNetworkACLItemCmdMock.isDisplay()).thenReturn(true);
        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn("");

        networkAclServiceImpl.transferDataToNetworkAclRulePojo(updateNetworkACLItemCmdMock, networkAclItemVoMock, networkAclMock);

        Mockito.verify(networkAclItemVoMock).setNumber(1);
        Mockito.verify(networkAclItemVoMock).setSourcePortStart(23);
        Mockito.verify(networkAclItemVoMock).setSourcePortEnd(24);
        Mockito.verify(networkAclItemVoMock).setSourceCidrList(cidrsList);
        Mockito.verify(networkAclItemVoMock).setProtocol("all");
        Mockito.verify(networkAclServiceImpl).updateIcmpCodeAndType(Mockito.any(Boolean.class), Mockito.any(), Mockito.any());
        Mockito.verify(networkAclItemVoMock).setAction(Action.Deny);
        Mockito.verify(networkAclItemVoMock).setTrafficType(TrafficType.Egress);
        Mockito.verify(networkAclItemVoMock).setUuid("customUuid");
        Mockito.verify(networkAclItemVoMock).setReason("reason");
        Mockito.verify(networkAclItemVoMock).setDisplay(true);
        Mockito.verify(networkAclServiceImpl).validateAndCreateNetworkAclRuleAction("deny");
    }

    private void setUpdateICMPCodeAndTypeTest(String protocol, Integer icmpCode, Integer icmpType) {
        Mockito.when(networkAclItemVoMock.getProtocol()).thenReturn(protocol);
        Mockito.when(updateNetworkACLItemCmdMock.getIcmpCode()).thenReturn(icmpCode);
        Mockito.when(updateNetworkACLItemCmdMock.getIcmpType()).thenReturn(icmpType);
    }

    @Test
    public void updateICMPCodeAndTypeTestIsPartialUpgradeIsICMPNotNullCodeAndType() {
        setUpdateICMPCodeAndTypeTest(NetUtils.ICMP_PROTO, 5, 5);
        networkAclServiceImpl.updateIcmpCodeAndType(true, updateNetworkACLItemCmdMock, networkAclItemVoMock);

        Mockito.verify(networkAclItemVoMock).setIcmpCode(5);
        Mockito.verify(networkAclItemVoMock).setIcmpType(5);
    }

    @Test
    public void updateICMPCodeAndTypeTestIsPartialUpgradeIsICMPNullCodeAndType() {
        setUpdateICMPCodeAndTypeTest(NetUtils.ICMP_PROTO, null, null);
        networkAclServiceImpl.updateIcmpCodeAndType(true, updateNetworkACLItemCmdMock, networkAclItemVoMock);

        Mockito.verify(networkAclItemVoMock, Mockito.never()).setIcmpCode(nullable(Integer.class));
        Mockito.verify(networkAclItemVoMock, Mockito.never()).setIcmpType(nullable(Integer.class));
    }

    @Test
    public void updateICMPCodeAndTypeTestIsPartialUpgradeNotICMPNotNullCodeAndType() {
        setUpdateICMPCodeAndTypeTest("", 5, 5);
        networkAclServiceImpl.updateIcmpCodeAndType(true, updateNetworkACLItemCmdMock, networkAclItemVoMock);

        Mockito.verify(networkAclItemVoMock).setIcmpCode(5);
        Mockito.verify(networkAclItemVoMock).setIcmpType(5);
    }

    @Test
    public void updateICMPCodeAndTypeTestIsPartialUpgradeNotICMPNullCodeAndType() {
        setUpdateICMPCodeAndTypeTest("", null, null);
        networkAclServiceImpl.updateIcmpCodeAndType(true, updateNetworkACLItemCmdMock, networkAclItemVoMock);

        Mockito.verify(networkAclItemVoMock, Mockito.never()).setIcmpCode(Mockito.any());
        Mockito.verify(networkAclItemVoMock, Mockito.never()).setIcmpType(Mockito.any());
    }

    @Test
    public void updateICMPCodeAndTypeTestNotPartialUpgradeIsICMPNotNullCodeAndType() {
        setUpdateICMPCodeAndTypeTest(NetUtils.ICMP_PROTO, 5, 5);
        networkAclServiceImpl.updateIcmpCodeAndType(false, updateNetworkACLItemCmdMock, networkAclItemVoMock);

        Mockito.verify(networkAclItemVoMock).setIcmpCode(5);
        Mockito.verify(networkAclItemVoMock).setIcmpType(5);
    }

    @Test
    public void updateICMPCodeAndTypeTestNotPartialUpgradeIsICMPNullCodeAndType() {
        setUpdateICMPCodeAndTypeTest(NetUtils.ICMP_PROTO, null, null);
        networkAclServiceImpl.updateIcmpCodeAndType(false, updateNetworkACLItemCmdMock, networkAclItemVoMock);

        Mockito.verify(networkAclItemVoMock).setIcmpCode(-1);
        Mockito.verify(networkAclItemVoMock).setIcmpType(-1);
    }

    @Test
    public void updateICMPCodeAndTypeTestNotPartialUpgradeNotICMPNotNullCodeAndType() {
        setUpdateICMPCodeAndTypeTest("", 5, 5);

        networkAclServiceImpl.updateIcmpCodeAndType(false, updateNetworkACLItemCmdMock, networkAclItemVoMock);

        Mockito.verify(networkAclItemVoMock).setIcmpCode(null);
        Mockito.verify(networkAclItemVoMock).setIcmpType(null);
    }

    @Test
    public void updateICMPCodeAndTypeTestNotPartialUpgradeNotICMPNullCodeAndType() {
        setUpdateICMPCodeAndTypeTest("", null, null);

        networkAclServiceImpl.updateIcmpCodeAndType(false, updateNetworkACLItemCmdMock, networkAclItemVoMock);

        Mockito.verify(networkAclItemVoMock).setIcmpCode(null);
        Mockito.verify(networkAclItemVoMock).setIcmpType(null);
    }

    @Test
    public void updateNetworkACLTestParametersNotNull() {
        String name = "name";
        String description = "desc";
        String customId = "customId";

        Mockito.when(updateNetworkACLListCmdMock.getName()).thenReturn(name);
        Mockito.when(updateNetworkACLListCmdMock.getDescription()).thenReturn(description);
        Mockito.when(updateNetworkACLListCmdMock.getCustomId()).thenReturn(customId);
        Mockito.when(updateNetworkACLListCmdMock.getId()).thenReturn(networkAclListId);
        Mockito.when(updateNetworkACLListCmdMock.getDisplay()).thenReturn(false);
        Mockito.when(networkACLVOMock.getVpcId()).thenReturn(networkMockVpcMockId);
        Mockito.doReturn(vpcVOMock).when(vpcDaoMock).findById(networkMockVpcMockId);
        Mockito.doNothing().when(accountManagerMock).checkAccess(Mockito.any(Account.class),
        Mockito.isNull(AccessType.class), Mockito.eq(true), Mockito.any(Vpc.class));


        networkAclServiceImpl.updateNetworkACL(updateNetworkACLListCmdMock);

        InOrder inOrder = Mockito.inOrder(networkAclDaoMock, vpcDaoMock, accountManagerMock, networkACLVOMock);

        inOrder.verify(networkAclDaoMock).findById(networkAclListId);
        inOrder.verify(vpcDaoMock).findById(Mockito.anyLong());
        inOrder.verify(accountManagerMock).checkAccess(Mockito.any(Account.class), Mockito.isNull(AccessType.class), Mockito.eq(true), nullable(Vpc.class));


        inOrder.verify(networkACLVOMock).setName(name);
        inOrder.verify(networkACLVOMock).setDescription(description);
        inOrder.verify(networkACLVOMock).setUuid(customId);
        inOrder.verify(networkACLVOMock).setDisplay(false);

        inOrder.verify(networkAclDaoMock).update(networkAclListId, networkACLVOMock);
        inOrder.verify(networkAclDaoMock).findById(networkAclListId);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void moveNetworkAclRuleToNewPositionTestBothPreviousAndNextAclRuleIdsNull() {
        configureNextAndPreviousAclRuleUuidsForMoveAclRuleCommand(null, null);

        networkAclServiceImpl.moveNetworkAclRuleToNewPosition(moveNetworkAclItemCmdMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void moveNetworkAclRuleToNewPositionTestBothPreviousAndNextAclRuleIdsEmpty() {
        configureNextAndPreviousAclRuleUuidsForMoveAclRuleCommand("", "");

        networkAclServiceImpl.moveNetworkAclRuleToNewPosition(moveNetworkAclItemCmdMock);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void moveNetworkAclRuleToNewPositionTestBothPreviousAndNextAclRuleIdsBlank() {
        configureNextAndPreviousAclRuleUuidsForMoveAclRuleCommand("     ", "         ");

        networkAclServiceImpl.moveNetworkAclRuleToNewPosition(moveNetworkAclItemCmdMock);
    }

    private void configureNextAndPreviousAclRuleUuidsForMoveAclRuleCommand(String nextAclRuleUuid, String previousAclRuleUuid) {
        Mockito.when(moveNetworkAclItemCmdMock.getNextAclRuleUuid()).thenReturn(nextAclRuleUuid);
        Mockito.when(moveNetworkAclItemCmdMock.getPreviousAclRuleUuid()).thenReturn(previousAclRuleUuid);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void moveNetworkAclRuleToNewPositionTestAclRuleBeingMovedNotFound() {
        configureNextAndPreviousAclRuleUuidsForMoveAclRuleCommand(nextAclRuleUuid, previousAclRuleUuid);

        Mockito.doReturn(null).when(networkAclItemDaoMock).findByUuid(uuidAclRuleBeingMoved);

        networkAclServiceImpl.moveNetworkAclRuleToNewPosition(moveNetworkAclItemCmdMock);
    }

    @Test
    public void moveNetworkAclRuleToNewPositionTestMoveRuleToTop() {
        configureNextAndPreviousAclRuleUuidsForMoveAclRuleCommand(nextAclRuleUuid, previousAclRuleUuid);

        Mockito.doReturn(aclRuleBeingMovedMock).when(networkAclItemDaoMock).findByUuid(uuidAclRuleBeingMoved);

        Mockito.doReturn(null).when(networkAclServiceImpl).retrieveAndValidateAclRule(previousAclRuleUuid);
        Mockito.doReturn(nextAclRuleMock).when(networkAclServiceImpl).retrieveAndValidateAclRule(nextAclRuleUuid);

        Mockito.doNothing().when(networkAclServiceImpl).validateMoveAclRulesData(aclRuleBeingMovedMock, null, nextAclRuleMock);

        configureMoveMethodsToDoNothing();

        networkAclServiceImpl.moveNetworkAclRuleToNewPosition(moveNetworkAclItemCmdMock);

        Mockito.verify(networkAclServiceImpl, Mockito.times(1)).moveRuleToTheTop(Mockito.eq(aclRuleBeingMovedMock), Mockito.anyList());
        Mockito.verify(networkAclServiceImpl, Mockito.times(0)).moveRuleToTheBottom(Mockito.eq(aclRuleBeingMovedMock), Mockito.anyList());
        Mockito.verify(networkAclServiceImpl, Mockito.times(0)).moveRuleBetweenAclRules(Mockito.eq(aclRuleBeingMovedMock), Mockito.anyList(), Mockito.eq(previousAclRuleMock),
                Mockito.eq(nextAclRuleMock));
        Mockito.verify(networkAclServiceImpl, Mockito.times(1)).validateAclConsistency(Mockito.any(MoveNetworkAclItemCmd.class), Mockito.any(NetworkACLVO.class),
                Mockito.anyList());
    }

    @Test
    public void moveNetworkAclRuleToNewPositionTestMoveRuleToBottom() {
        configureNextAndPreviousAclRuleUuidsForMoveAclRuleCommand(nextAclRuleUuid, previousAclRuleUuid);

        Mockito.doNothing().when(networkAclServiceImpl).validateMoveAclRulesData(aclRuleBeingMovedMock, previousAclRuleMock, null);

        Mockito.doReturn(aclRuleBeingMovedMock).when(networkAclItemDaoMock).findByUuid(uuidAclRuleBeingMoved);

        Mockito.doReturn(previousAclRuleMock).when(networkAclServiceImpl).retrieveAndValidateAclRule(previousAclRuleUuid);
        Mockito.doReturn(null).when(networkAclServiceImpl).retrieveAndValidateAclRule(nextAclRuleUuid);

        configureMoveMethodsToDoNothing();

        networkAclServiceImpl.moveNetworkAclRuleToNewPosition(moveNetworkAclItemCmdMock);

        Mockito.verify(networkAclServiceImpl, Mockito.times(0)).moveRuleToTheTop(Mockito.eq(aclRuleBeingMovedMock), Mockito.anyList());
        Mockito.verify(networkAclServiceImpl, Mockito.times(1)).moveRuleToTheBottom(Mockito.eq(aclRuleBeingMovedMock), Mockito.anyList());
        Mockito.verify(networkAclServiceImpl, Mockito.times(0)).moveRuleBetweenAclRules(Mockito.eq(aclRuleBeingMovedMock), Mockito.anyList(), Mockito.eq(previousAclRuleMock),
                Mockito.eq(nextAclRuleMock));
        Mockito.verify(networkAclServiceImpl, Mockito.times(1)).validateAclConsistency(Mockito.any(MoveNetworkAclItemCmd.class), Mockito.any(NetworkACLVO.class),
                Mockito.anyList());
    }

    @Test
    public void moveNetworkAclRuleToNewPositionTestMoveBetweenAclRules() {
        configureNextAndPreviousAclRuleUuidsForMoveAclRuleCommand(nextAclRuleUuid, previousAclRuleUuid);

        Mockito.doNothing().when(networkAclServiceImpl).validateMoveAclRulesData(aclRuleBeingMovedMock, previousAclRuleMock, nextAclRuleMock);

        Mockito.doReturn(aclRuleBeingMovedMock).when(networkAclItemDaoMock).findByUuid(uuidAclRuleBeingMoved);

        Mockito.doReturn(previousAclRuleMock).when(networkAclServiceImpl).retrieveAndValidateAclRule(previousAclRuleUuid);
        Mockito.doReturn(nextAclRuleMock).when(networkAclServiceImpl).retrieveAndValidateAclRule(nextAclRuleUuid);

        configureMoveMethodsToDoNothing();

        networkAclServiceImpl.moveNetworkAclRuleToNewPosition(moveNetworkAclItemCmdMock);

        Mockito.verify(networkAclServiceImpl, Mockito.times(0)).moveRuleToTheTop(Mockito.eq(aclRuleBeingMovedMock), Mockito.anyList());
        Mockito.verify(networkAclServiceImpl, Mockito.times(0)).moveRuleToTheBottom(Mockito.eq(aclRuleBeingMovedMock), Mockito.anyList());
        Mockito.verify(networkAclServiceImpl, Mockito.times(1)).moveRuleBetweenAclRules(Mockito.eq(aclRuleBeingMovedMock), Mockito.anyList(), Mockito.eq(previousAclRuleMock),
                Mockito.eq(nextAclRuleMock));
        Mockito.verify(networkAclServiceImpl, Mockito.times(1)).validateAclConsistency(Mockito.any(MoveNetworkAclItemCmd.class), Mockito.any(NetworkACLVO.class),
                Mockito.anyList());
    }

    private void configureMoveMethodsToDoNothing() {
        Mockito.doReturn(networkACLVOMock).when(networkAclDaoMock).acquireInLockTable(Mockito.anyLong());
        Mockito.doReturn(true).when(networkAclDaoMock).releaseFromLockTable(Mockito.anyLong());

        Mockito.doNothing().when(networkAclServiceImpl).validateAclConsistency(Mockito.any(MoveNetworkAclItemCmd.class), Mockito.any(NetworkACLVO.class), Mockito.anyList());

        Mockito.doReturn(aclRuleBeingMovedMock).when(networkAclServiceImpl).moveRuleToTheTop(Mockito.eq(aclRuleBeingMovedMock), Mockito.anyList());
        Mockito.doReturn(aclRuleBeingMovedMock).when(networkAclServiceImpl).moveRuleToTheBottom(Mockito.eq(aclRuleBeingMovedMock), Mockito.anyList());
        Mockito.doReturn(aclRuleBeingMovedMock).when(networkAclServiceImpl).moveRuleBetweenAclRules(Mockito.eq(aclRuleBeingMovedMock), Mockito.anyList(),
                Mockito.eq(previousAclRuleMock), Mockito.eq(nextAclRuleMock));
    }

    @Test
    public void retrieveAndValidateAclRuleTestUuidNull() {
        NetworkACLItemVO networkACLItemVOReceived = networkAclServiceImpl.retrieveAndValidateAclRule(null);

        Assert.assertNull(networkACLItemVOReceived);
    }

    @Test
    public void retrieveAndValidateAclRuleTestUuidEmpty() {
        NetworkACLItemVO networkACLItemVOReceived = networkAclServiceImpl.retrieveAndValidateAclRule(StringUtils.EMPTY);

        Assert.assertNull(networkACLItemVOReceived);
    }

    @Test
    public void retrieveAndValidateAclRuleTestUuidBlank() {
        NetworkACLItemVO networkACLItemVOReceived = networkAclServiceImpl.retrieveAndValidateAclRule("        ");

        Assert.assertNull(networkACLItemVOReceived);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void retrieveAndValidateAclRuleTestAclRuleNotFound() {
        Mockito.doReturn(null).when(networkAclItemDaoMock).findByUuid(nextAclRuleUuid);

        networkAclServiceImpl.retrieveAndValidateAclRule(nextAclRuleUuid);
    }

    @Test
    public void retrieveAndValidateAclRuleTestAclRuleFound() {
        Mockito.doReturn(nextAclRuleMock).when(networkAclItemDaoMock).findByUuid(nextAclRuleUuid);

        NetworkACLItemVO networkACLItemVOReceived = networkAclServiceImpl.retrieveAndValidateAclRule(nextAclRuleUuid);

        Assert.assertEquals(nextAclRuleMock, networkACLItemVOReceived);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateMoveAclRulesDataTestInvalidPreviousAndNextAclRules() {
        networkAclServiceImpl.validateMoveAclRulesData(aclRuleBeingMovedMock, null, null);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateMoveAclRulesDataTestPreviousRuleWithDifferentAclId() {
        Mockito.when(previousAclRuleMock.getAclId()).thenReturn(99L);

        networkAclServiceImpl.validateMoveAclRulesData(aclRuleBeingMovedMock, previousAclRuleMock, null);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateMoveAclRulesDataTestNextRuleWithDifferentAclId() {
        Mockito.when(nextAclRuleMock.getAclId()).thenReturn(99L);

        networkAclServiceImpl.validateMoveAclRulesData(aclRuleBeingMovedMock, null, nextAclRuleMock);
    }

    @Test
    public void updateNetworkACLTestParametersWithNullValues() {
        Mockito.when(updateNetworkACLListCmdMock.getName()).thenReturn(null);
        Mockito.when(updateNetworkACLListCmdMock.getDescription()).thenReturn(null);
        Mockito.when(updateNetworkACLListCmdMock.getCustomId()).thenReturn(null);
        Mockito.when(updateNetworkACLListCmdMock.getId()).thenReturn(networkAclListId);
        Mockito.when(updateNetworkACLListCmdMock.getDisplay()).thenReturn(null);
        Mockito.when(networkACLVOMock.getVpcId()).thenReturn(networkMockVpcMockId);
        Mockito.doReturn(vpcVOMock).when(vpcDaoMock).findById(networkMockVpcMockId);
        Mockito.doNothing().when(accountManagerMock).checkAccess(Mockito.any(Account.class),
        Mockito.isNull(AccessType.class), Mockito.eq(true), Mockito.any(Vpc.class));

        networkAclServiceImpl.updateNetworkACL(updateNetworkACLListCmdMock);

        InOrder inOrder = Mockito.inOrder(networkAclDaoMock, vpcDaoMock, accountManagerMock, networkACLVOMock);

        inOrder.verify(networkAclDaoMock).findById(networkAclListId);
        inOrder.verify(vpcDaoMock).findById(Mockito.anyLong());
        inOrder.verify(accountManagerMock).checkAccess(any(Account.class), isNull(), eq(true), nullable(Vpc.class));

        Mockito.verify(networkACLVOMock, Mockito.times(0)).setName(null);
        inOrder.verify(networkACLVOMock, Mockito.times(0)).setDescription(null);
        inOrder.verify(networkACLVOMock, Mockito.times(0)).setUuid(null);
        inOrder.verify(networkACLVOMock, Mockito.times(0)).setDisplay(false);

        inOrder.verify(networkAclDaoMock).update(networkAclListId, networkACLVOMock);
        inOrder.verify(networkAclDaoMock).findById(networkAclListId);
    }

    @Test
    public void validateMoveAclRulesDataTestSuccesfullExecution() {
        Mockito.when(nextAclRuleMock.getAclId()).thenReturn(networkAclMockId);
        Mockito.when(previousAclRuleMock.getAclId()).thenReturn(networkAclMockId);

        CallContext callContextMock = Mockito.mock(CallContext.class);
        Mockito.doReturn(Mockito.mock(Account.class)).when(callContextMock).getCallingAccount();

        Mockito.doReturn(networkAclMock).when(networkAclDaoMock).findById(networkAclMockId);
        Mockito.when(CallContext.current()).thenReturn(callContextMock);

        Mockito.doNothing().when(networkAclServiceImpl).validateGlobalAclPermissionAndAclAssociatedToVpc(Mockito.any(NetworkACL.class), Mockito.any(Account.class), Mockito.anyString());

        networkAclServiceImpl.validateMoveAclRulesData(aclRuleBeingMovedMock, previousAclRuleMock, nextAclRuleMock);

        Mockito.verify(networkAclDaoMock).findById(networkAclMockId);
        Mockito.verify(networkAclServiceImpl).validateGlobalAclPermissionAndAclAssociatedToVpc(Mockito.any(NetworkACL.class), Mockito.any(Account.class), Mockito.anyString());
    }

    @Test
    public void getAllAclRulesSortedByNumberTest() {
        List<NetworkACLItemVO> networkAclItemVos = new ArrayList<>();

        NetworkACLItemVO networkACLItemVO1 = new NetworkACLItemVO();
        networkACLItemVO1.setNumber(1);

        NetworkACLItemVO networkACLItemVO2 = new NetworkACLItemVO();
        networkACLItemVO2.setNumber(2);

        NetworkACLItemVO networkACLItemVO3 = new NetworkACLItemVO();
        networkACLItemVO3.setNumber(3);

        NetworkACLItemVO networkACLItemVO4 = new NetworkACLItemVO();
        networkACLItemVO4.setNumber(4);

        networkAclItemVos.add(networkACLItemVO1);
        networkAclItemVos.add(networkACLItemVO2);
        networkAclItemVos.add(networkACLItemVO3);
        networkAclItemVos.add(networkACLItemVO4);

        Collections.shuffle(networkAclItemVos);

        Mockito.doReturn(networkAclItemVos).when(networkAclItemDaoMock).listByACL(networkAclMockId);

        List<NetworkACLItemVO> allAclRulesSortedByNumber = networkAclServiceImpl.getAllAclRulesSortedByNumber(networkAclMockId);

        Assert.assertEquals(networkAclItemVos.size(), allAclRulesSortedByNumber.size());
        Assert.assertEquals(networkACLItemVO1, networkAclItemVos.get(0));
        Assert.assertEquals(networkACLItemVO2, networkAclItemVos.get(1));
        Assert.assertEquals(networkACLItemVO3, networkAclItemVos.get(2));
        Assert.assertEquals(networkACLItemVO4, networkAclItemVos.get(3));

    }

    @Test
    public void moveRuleToTheTopTest() {
        Mockito.doReturn(aclRuleBeingMovedMock).when(networkAclServiceImpl).updateAclRuleToNewPositionAndExecuteShiftIfNecessary(Mockito.eq(aclRuleBeingMovedMock), Mockito.anyInt(),
                Mockito.anyList(), Mockito.anyInt());

        networkAclServiceImpl.moveRuleToTheTop(aclRuleBeingMovedMock, new ArrayList<>());

        Mockito.verify(networkAclServiceImpl).updateAclRuleToNewPositionAndExecuteShiftIfNecessary(Mockito.eq(aclRuleBeingMovedMock), Mockito.eq(1), Mockito.anyList(),
                Mockito.eq(0));
    }

    @Test
    public void moveRuleToTheBottomTest() {
        ArrayList<NetworkACLItemVO> allAclRules = new ArrayList<>();

        NetworkACLItemVO networkACLItemVO1 = new NetworkACLItemVO();
        networkACLItemVO1.setNumber(100);

        allAclRules.add(networkACLItemVO1);
        Mockito.when(aclRuleBeingMovedMock.getId()).thenReturn(99l);

        Mockito.doNothing().when(networkAclItemDaoMock).updateNumberFieldNetworkItem(Mockito.anyLong(), Mockito.anyInt());
        Mockito.doReturn(aclRuleBeingMovedMock).when(networkAclItemDaoMock).findById(99l);

        networkAclServiceImpl.moveRuleToTheBottom(aclRuleBeingMovedMock, allAclRules);

        Mockito.verify(aclRuleBeingMovedMock).setNumber(101);
        Mockito.verify(networkAclItemDaoMock).updateNumberFieldNetworkItem(99l, 101);
        Mockito.verify(networkAclItemDaoMock).findById(99l);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void moveRuleBetweenAclRulesTestThereIsSpaceBetweenPreviousRuleAndNextRuleToAccommodateTheNewRuleWithOtherRuleColliding() {
        Mockito.when(previousAclRuleMock.getNumber()).thenReturn(10);
        Mockito.when(nextAclRuleMock.getNumber()).thenReturn(15);

        ArrayList<NetworkACLItemVO> allAclRules = new ArrayList<>();
        NetworkACLItemVO networkACLItemVO1 = new NetworkACLItemVO();
        networkACLItemVO1.setNumber(11);

        allAclRules.add(previousAclRuleMock);
        allAclRules.add(networkACLItemVO1);
        allAclRules.add(nextAclRuleMock);

        networkAclServiceImpl.moveRuleBetweenAclRules(aclRuleBeingMovedMock, allAclRules, previousAclRuleMock, nextAclRuleMock);
    }

    @Test
    public void moveRuleBetweenAclRulesTestThereIsSpaceBetweenPreviousRuleAndNextRuleToAccommodateTheNewRule() {
        Mockito.when(previousAclRuleMock.getNumber()).thenReturn(10);
        Mockito.when(nextAclRuleMock.getNumber()).thenReturn(11);
        Mockito.when(aclRuleBeingMovedMock.getId()).thenReturn(1l);

        ArrayList<NetworkACLItemVO> allAclRules = new ArrayList<>();
        NetworkACLItemVO networkACLItemVO12 = new NetworkACLItemVO();
        networkACLItemVO12.setNumber(12);
        NetworkACLItemVO networkACLItemVO13 = new NetworkACLItemVO();
        networkACLItemVO13.setNumber(13);
        NetworkACLItemVO networkACLItemVO14 = new NetworkACLItemVO();
        networkACLItemVO14.setNumber(14);

        allAclRules.add(previousAclRuleMock);
        allAclRules.add(nextAclRuleMock);
        allAclRules.add(networkACLItemVO12);
        allAclRules.add(networkACLItemVO13);
        allAclRules.add(networkACLItemVO14);
        allAclRules.add(aclRuleBeingMovedMock);

        Mockito.doReturn(aclRuleBeingMovedMock).when(networkAclServiceImpl).updateAclRuleToNewPositionAndExecuteShiftIfNecessary(Mockito.any(NetworkACLItemVO.class), Mockito.anyInt(),
                Mockito.anyList(), Mockito.anyInt());

        networkAclServiceImpl.moveRuleBetweenAclRules(aclRuleBeingMovedMock, allAclRules, previousAclRuleMock, nextAclRuleMock);

        Mockito.verify(networkAclItemDaoMock, times(0)).updateNumberFieldNetworkItem(aclRuleBeingMovedMock.getId(), 11);
        Mockito.verify(networkAclItemDaoMock, times(0)).findById(1l);
        Mockito.verify(networkAclServiceImpl, Mockito.times(1)).updateAclRuleToNewPositionAndExecuteShiftIfNecessary(Mockito.any(NetworkACLItemVO.class), Mockito.eq(11),
                Mockito.anyList(), Mockito.eq(1));
    }

    @Test
    public void moveRuleBetweenAclRulesTestThereIsNoSpaceBetweenPreviousRuleAndNextRuleToAccommodateTheNewRule() {
        Mockito.when(previousAclRuleMock.getNumber()).thenReturn(10);
        Mockito.when(nextAclRuleMock.getNumber()).thenReturn(15);
        Mockito.when(aclRuleBeingMovedMock.getNumber()).thenReturn(50);
        Mockito.when(aclRuleBeingMovedMock.getId()).thenReturn(1l);

        ArrayList<NetworkACLItemVO> allAclRules = new ArrayList<>();

        allAclRules.add(previousAclRuleMock);
        allAclRules.add(nextAclRuleMock);
        allAclRules.add(aclRuleBeingMovedMock);

        Mockito.doNothing().when(networkAclItemDaoMock).updateNumberFieldNetworkItem(Mockito.anyLong(), Mockito.anyInt());
        Mockito.doReturn(aclRuleBeingMovedMock).when(networkAclItemDaoMock).findById(1l);

        networkAclServiceImpl.moveRuleBetweenAclRules(aclRuleBeingMovedMock, allAclRules, previousAclRuleMock, nextAclRuleMock);

        Mockito.verify(networkAclItemDaoMock).updateNumberFieldNetworkItem(aclRuleBeingMovedMock.getId(), 11);
        Mockito.verify(networkAclItemDaoMock).findById(1l);
        Mockito.verify(networkAclServiceImpl, Mockito.times(0)).updateAclRuleToNewPositionAndExecuteShiftIfNecessary(Mockito.any(NetworkACLItemVO.class), Mockito.anyInt(),
                Mockito.anyList(), Mockito.anyInt());
    }

    @Test
    public void updateAclRuleToNewPositionAndExecuteShiftIfNecessaryTest() {

        Mockito.when(nextAclRuleMock.getNumber()).thenReturn(11);
        Mockito.when(nextAclRuleMock.getId()).thenReturn(50l);

        Mockito.when(aclRuleBeingMovedMock.getNumber()).thenReturn(50);
        Mockito.when(aclRuleBeingMovedMock.getId()).thenReturn(1l);

        ArrayList<NetworkACLItemVO> allAclRules = new ArrayList<>();
        NetworkACLItemVO networkACLItemVO12 = new NetworkACLItemVO();
        networkACLItemVO12.setNumber(12);
        networkACLItemVO12.id = 12;
        NetworkACLItemVO networkACLItemVO13 = new NetworkACLItemVO();
        networkACLItemVO13.id = 13;
        networkACLItemVO13.setNumber(13);
        NetworkACLItemVO networkACLItemVO14 = new NetworkACLItemVO();
        networkACLItemVO14.setNumber(14);
        networkACLItemVO14.id = 14;

        allAclRules.add(previousAclRuleMock);
        allAclRules.add(nextAclRuleMock);
        allAclRules.add(networkACLItemVO12);
        allAclRules.add(networkACLItemVO13);
        allAclRules.add(networkACLItemVO14);
        allAclRules.add(aclRuleBeingMovedMock);

        Map<Long, NetworkACLItemVO> updatedItems = new HashMap<>();
        Mockito.doAnswer((Answer<Void>) invocation -> {
            Long id = (Long)invocation.getArguments()[0];
            int position = (int)invocation.getArguments()[1];
            NetworkACLItemVO item = new NetworkACLItemVO();
            item.setNumber(position);
            updatedItems.put(id, item);
            return null;
        }).when(networkAclItemDaoMock).updateNumberFieldNetworkItem(Mockito.anyLong(), Mockito.anyInt());
        Mockito.doAnswer((Answer<NetworkACLItemVO>) invocation -> {
            Long id = (Long)invocation.getArguments()[0];
            return updatedItems.get(id);
        }).when(networkAclItemDaoMock).findById(Mockito.anyLong());

        NetworkACLItem result = networkAclServiceImpl.updateAclRuleToNewPositionAndExecuteShiftIfNecessary(aclRuleBeingMovedMock, 11, allAclRules, 1);

        Assert.assertNotNull(result);
        Assert.assertEquals(11, result.getNumber());
        Assert.assertEquals(11, updatedItems.get(aclRuleBeingMovedMock.getId()).getNumber());
        Assert.assertEquals(12, updatedItems.get(nextAclRuleMock.getId()).getNumber());
        Mockito.verify(networkAclItemDaoMock).updateNumberFieldNetworkItem(1l, 11);
        Mockito.verify(networkAclItemDaoMock).updateNumberFieldNetworkItem(50l, 12);

        Assert.assertEquals(networkACLItemVO12.getNumber() + 1, updatedItems.get(networkACLItemVO12.getId()).getNumber());
        Assert.assertEquals(networkACLItemVO13.getNumber() + 1, updatedItems.get(networkACLItemVO13.getId()).getNumber());
        Assert.assertEquals(networkACLItemVO14.getNumber() + 1, updatedItems.get(networkACLItemVO14.getId()).getNumber());
    }

    @Test
    public void validateAclConsistencyTestRuleListEmpty() {
        networkAclServiceImpl.validateAclConsistency(moveNetworkAclItemCmdMock, networkACLVOMock, new ArrayList<>());

        Mockito.verify(moveNetworkAclItemCmdMock, Mockito.times(0)).getAclConsistencyHash();
    }

    @Test
    public void validateAclConsistencyTestRuleListNull() {
        networkAclServiceImpl.validateAclConsistency(moveNetworkAclItemCmdMock, networkACLVOMock, null);

        Mockito.verify(moveNetworkAclItemCmdMock, Mockito.times(0)).getAclConsistencyHash();
    }

    @Test
    public void validateAclConsistencyTestAclConsistencyHashIsNull() {
        Mockito.doReturn(null).when(moveNetworkAclItemCmdMock).getAclConsistencyHash();

        validateAclConsistencyTestAclConsistencyHashBlank();
    }

    @Test
    public void validateAclConsistencyTestAclConsistencyHashIsEmpty() {
        Mockito.doReturn("").when(moveNetworkAclItemCmdMock).getAclConsistencyHash();

        validateAclConsistencyTestAclConsistencyHashBlank();
    }

    @Test
    public void validateAclConsistencyTestAclConsistencyHashIsBlank() {
        Mockito.doReturn("            ").when(moveNetworkAclItemCmdMock).getAclConsistencyHash();

        validateAclConsistencyTestAclConsistencyHashBlank();
    }

    private void validateAclConsistencyTestAclConsistencyHashBlank() {
        ArrayList<NetworkACLItemVO> allAclRules = new ArrayList<>();
        allAclRules.add(networkAclItemVoMock);

        networkAclServiceImpl.validateAclConsistency(moveNetworkAclItemCmdMock, networkACLVOMock, allAclRules);

        Mockito.verify(moveNetworkAclItemCmdMock, Mockito.times(1)).getAclConsistencyHash();
        Mockito.verify(callContextMock, Mockito.times(1)).getCallingAccount();
        Mockito.verify(callContextMock, Mockito.times(1)).getCallingUser();
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateAclConsistencyTestAclConsistencyHashIsNotEqualsToDatabaseHash() {
        Mockito.doReturn("differentHash").when(moveNetworkAclItemCmdMock).getAclConsistencyHash();

        ArrayList<NetworkACLItemVO> allAclRules = new ArrayList<>();
        allAclRules.add(networkAclItemVoMock);

        networkAclServiceImpl.validateAclConsistency(moveNetworkAclItemCmdMock, networkACLVOMock, allAclRules);
    }

    @Test
    public void validateAclConsistencyTest() {
        Mockito.doReturn("eac527fe45c77232ef06d9c7eb8abd94").when(moveNetworkAclItemCmdMock).getAclConsistencyHash();

        ArrayList<NetworkACLItemVO> allAclRules = new ArrayList<>();
        allAclRules.add(networkAclItemVoMock);

        Mockito.doReturn(SOME_UUID).when(networkAclItemVoMock).getUuid();
        networkAclServiceImpl.validateAclConsistency(moveNetworkAclItemCmdMock, networkACLVOMock, allAclRules);

        Mockito.verify(moveNetworkAclItemCmdMock, Mockito.times(1)).getAclConsistencyHash();
    }

    @Test
    public void checkGlobalAclPermissionTestGlobalAclWithRootAccountShouldWork() {
        Mockito.doReturn(Account.Type.ADMIN).when(accountMock).getType();
        Mockito.doReturn(true).when(networkAclServiceImpl).isGlobalAcl(Mockito.anyLong());

        networkAclServiceImpl.checkGlobalAclPermission(networkMockVpcMockId, accountMock, "exception");
    }

    @Test(expected = PermissionDeniedException.class)
    public void checkGlobalAclPermissionTestGlobalAclWithNonRootAccountShouldThrow() {
        Mockito.doReturn(Account.Type.NORMAL).when(accountMock).getType();
        Mockito.doReturn(true).when(networkAclServiceImpl).isGlobalAcl(Mockito.anyLong());

        networkAclServiceImpl.checkGlobalAclPermission(networkMockVpcMockId, accountMock, "exception");
    }

    @Test
    public void validateAclAssociatedToVpcTestNonNullVpcShouldCheckAccess() {
        Mockito.doReturn(vpcVOMock).when(vpcDaoMock).findById(Mockito.anyLong());

        networkAclServiceImpl.validateAclAssociatedToVpc(networkMockVpcMockId, accountMock, SOME_UUID);

        Mockito.verify(accountManagerMock, Mockito.times(1)).checkAccess(Mockito.any(Account.class), isNull(), Mockito.anyBoolean(), Mockito.any(Vpc.class));
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateAclAssociatedToVpcTestNullVpcShouldThrowInvalidParameterValueException() {
        Mockito.doReturn(null).when(vpcDaoMock).findById(Mockito.anyLong());

        networkAclServiceImpl.validateAclAssociatedToVpc(networkMockVpcMockId, accountMock, SOME_UUID);
    }
}
